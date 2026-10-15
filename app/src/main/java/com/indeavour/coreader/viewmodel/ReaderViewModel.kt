package com.indeavour.coreader.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.indeavour.coreader.AppRoomDatabase
import com.indeavour.coreader.model.firebase.AnnotationModel
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.ReplyModel
import com.indeavour.coreader.model.firebase.UserModel
import com.indeavour.coreader.model.room.ReadingActivity
import com.indeavour.coreader.model.room.UserPreferences
import com.indeavour.coreader.repository.ReaderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.repository.AnnotationRepository
import com.indeavour.coreader.repository.AnnotationScope
import com.indeavour.coreader.repository.GroupRepository
import com.indeavour.coreader.repository.LocatorFields
import com.indeavour.coreader.repository.StatsRepository
import com.indeavour.coreader.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.io.File

class ReaderViewModel(
    private val application: Application,
    private val repository: ReaderRepository
) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val users = UserRepository()
    private val groups = GroupRepository()
    private val stats = StatsRepository()
    private val annotations = AnnotationRepository()

    // Persistence launched from teardown paths (onCleared/onPause) must outlive
    // viewModelScope, which is already cancelled when onCleared runs.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication

    private val _initialLocator = MutableStateFlow<Locator?>(null)
    val initialLocator: StateFlow<Locator?> = _initialLocator

    data class ReadingProgress(
        val value: Float = 0f,
        val pageLabel: String = "",
        val percentageLabel: String = "",
        val chapterLabel: String = ""
    )

    private val _progress = MutableStateFlow(ReadingProgress())
    val progress: StateFlow<ReadingProgress> = _progress

    private val _isBookReady = MutableStateFlow(false)
    val isBookReady: StateFlow<Boolean> = _isBookReady

    var hasEverLoaded = false
        private set

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * [id] is the annotation's document id. Deletes and replies address it directly instead of
     * re-identifying an annotation by comparing its locator and text, which could not tell two
     * identical notes apart.
     */
    data class HighlightData(
        val id: String,
        val locator: Locator,
        val userId: String,
        val color: Int = AnnotationModel.DEFAULT_COLOR
    )

    data class NoteData(
        val id: String,
        val locator: Locator,
        val userId: String,
        val content: String,
        val color: Int = AnnotationModel.DEFAULT_COLOR,
        val replies: List<ReplyModel> = emptyList()
    )

    private val _selectedHighlightColor = MutableStateFlow(0x66FFFF00)
    val selectedHighlightColor: StateFlow<Int> = _selectedHighlightColor

    private val _fontSize = MutableStateFlow(100f)
    val fontSize: StateFlow<Float> = _fontSize

    private val _theme = MutableStateFlow("system")
    val theme: StateFlow<String> = _theme

    fun setSelectedHighlightColor(color: Int) {
        _selectedHighlightColor.value = color
        
        // Save to Firestore
        val uid = auth.currentUser?.uid ?: return
        saveScope.launch {
            try {
                users.setLastHighlightColor(uid, color)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to save lastHighlightColor", e)
            }
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val currentPrefs = database.userPreferencesDao().getPreferences().firstOrNull() ?: UserPreferences()
            database.userPreferencesDao().insertOrUpdate(currentPrefs.copy(fontSize = size))
        }
    }

    fun setTheme(theme: String) {
        _theme.value = theme
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val currentPrefs = database.userPreferencesDao().getPreferences().firstOrNull() ?: UserPreferences()
            database.userPreferencesDao().insertOrUpdate(currentPrefs.copy(theme = theme))
        }
    }

    private val _highlights = MutableStateFlow<List<HighlightData>>(emptyList())
    val highlights: StateFlow<List<HighlightData>> = _highlights

    private val _notes = MutableStateFlow<List<NoteData>>(emptyList())
    val notes: StateFlow<List<NoteData>> = _notes

    data class MemberProgress(
        val userId: String,
        val username: String,
        val locator: Locator
    )

    private val _groupProgress = MutableStateFlow<List<MemberProgress>>(emptyList())
    val groupProgress: StateFlow<List<MemberProgress>> = _groupProgress

    private val _usernames = MutableStateFlow<Map<String, String>>(emptyMap())
    val usernames: StateFlow<Map<String, String>> = _usernames

    private val _remoteProgression = MutableStateFlow<Locator?>(null)
    val remoteProgression: StateFlow<Locator?> = _remoteProgression

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private var loadedBookId: Int? = null
    private var loadedGroupId: String? = NO_GROUP
    private var groupBookListener: ListenerRegistration? = null
    private var groupInfoListener: ListenerRegistration? = null
    private var annotationsListener: ListenerRegistration? = null

    private var lastLocator: Locator? = null
    private var sessionStartTime: Long = 0
    private var lastTickTime: Long = 0
    private var secondsAccumulator: Long = 0
    private var wasCompletedAtStart: Boolean = false

    private fun startReadingSession(initialProgress: Float) {
        val now = System.currentTimeMillis()
        sessionStartTime = now
        lastTickTime = now
        secondsAccumulator = 0
        wasCompletedAtStart = initialProgress >= COMPLETION_THRESHOLD
    }

    private fun syncReadingProgress(isCompletedNow: Boolean, forceSync: Boolean = false) {
        if (lastTickTime == 0L) return // Session not started

        val now = System.currentTimeMillis()
        val deltaSeconds = (now - lastTickTime) / 1000
        
        if (deltaSeconds > 0) {
            secondsAccumulator += deltaSeconds
            lastTickTime = now
        }
        
        val newlyCompleted = !wasCompletedAtStart && isCompletedNow

        // Only trigger heavy updates if forced (e.g. leaving screen) or if we have a completion event
        if ((forceSync && secondsAccumulator > 0) || newlyCompleted) {
            val secondsToSave = secondsAccumulator
            secondsAccumulator = 0
            if (newlyCompleted) wasCompletedAtStart = true
            
            val bookId = loadedBookId ?: return
            val uid = auth.currentUser?.uid ?: return

            saveScope.launch {
                // 1. Save to Local Room
                val database = AppRoomDatabase.getDatabase(getApplication())
                database.readingActivityDao().insert(ReadingActivity(
                    bookId = bookId,
                    timestamp = now,
                    durationSeconds = secondsToSave,
                    isCompletedEvent = newlyCompleted
                ))

                if (newlyCompleted) {
                    database.bookDao().setCompletedTimestamp(bookId, now)
                }

                // 2. Push to Firestore for cross-device sync
                try {
                    val pub = _publication.value
                    val bookKey = pub?.let { bookKeyOf(it) }
                    stats.recordReading(
                        uid = uid,
                        secondsToSave = secondsToSave,
                        newlyCompleted = newlyCompleted,
                        nowMillis = now,
                        bookKey = bookKey
                    )
                } catch (e: Exception) {
                    Log.e("ReaderViewModel", "Failed to sync reading activity to Firestore", e)
                }
            }
        }
    }

    private fun bookKeyOf(pub: Publication): String {
        val author = pub.metadata.authors.firstOrNull()?.name ?: UNKNOWN_AUTHOR
        return "${pub.metadata.title}_$author"
    }

    private fun annotationScope(groupId: String?, uid: String): AnnotationScope =
        if (groupId != null && groupId != NO_GROUP && groupId.isNotBlank()) {
            AnnotationScope.Group(groupId)
        } else {
            AnnotationScope.Personal(uid)
        }

    private fun parseLocator(json: String): Locator? = try {
        Locator.fromJSON(org.json.JSONObject(json))
    } catch (e: Exception) {
        null
    }

    /**
     * Streams this book's annotations. In a group that is every member's highlights and notes in
     * one flat query, rather than reconstructing them from each member's embedded arrays.
     *
     * Local edits appear immediately through Firestore's latency compensation, so there is no
     * separate optimistic copy of the list to keep in step.
     */
    private fun observeAnnotations(scope: AnnotationScope, bookKey: String) {
        annotationsListener?.remove()
        annotationsListener = annotations.listen(scope, bookKey) { loaded ->
            val highlights = mutableListOf<HighlightData>()
            val notes = mutableListOf<NoteData>()
            loaded.forEach { annotation ->
                val locator = parseLocator(annotation.locator) ?: return@forEach
                if (annotation.isNote) {
                    notes += NoteData(
                        id = annotation.id,
                        locator = locator,
                        userId = annotation.ownerUid,
                        content = annotation.content,
                        color = annotation.color,
                        replies = annotation.replies
                    )
                } else {
                    highlights += HighlightData(
                        id = annotation.id,
                        locator = locator,
                        userId = annotation.ownerUid,
                        color = annotation.color
                    )
                }
            }
            _highlights.value = highlights
            _notes.value = notes

            resolveUsernames(
                loaded.flatMap { listOf(it.ownerUid) + it.replies.map { reply -> reply.userId } }
            )
        }
    }

    /** Group reading positions; annotations no longer travel on this document. */
    private fun observeGroupProgress(groupCode: String, bookKey: String, uid: String) {
        groupBookListener?.remove()
        groupBookListener = groups.listenGroupBooks(groupCode) { groupBook ->
            val progress = mutableListOf<MemberProgress>()
            groupBook?.bookProgression?.forEach { (memberId, memberBooks) ->
                val book = BookModel.fromAny(memberBooks[bookKey]) ?: return@forEach
                if (book.progress.isBlank()) return@forEach
                val locator = parseLocator(book.progress) ?: return@forEach
                progress += MemberProgress(memberId, _usernames.value[memberId] ?: "", locator)
                if (memberId == uid) _remoteProgression.value = locator
            }
            resolveUsernames(progress.map { it.userId })
            _groupProgress.value = progress.map {
                it.copy(username = _usernames.value[it.userId] ?: "")
            }
        }
    }

    private fun resolveUsernames(userIds: Collection<String>) {
        val missing = userIds.filter { it.isNotBlank() && !_usernames.value.containsKey(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val resolved = users.usernames(missing)
            if (resolved.isEmpty()) return@launch
            _usernames.value = _usernames.value + resolved
            _groupProgress.value = _groupProgress.value.map {
                if (it.username.isBlank()) it.copy(username = _usernames.value[it.userId] ?: "") else it
            }
        }
    }

    fun endReadingSession() {
        val currentProgress = _progress.value.value
        val isCompletedNow = currentProgress >= COMPLETION_THRESHOLD
        syncReadingProgress(isCompletedNow, forceSync = true)
    }

    // Restart the tick clock so time spent paused/backgrounded is not counted as reading time.
    fun resumeReadingSession() {
        if (lastTickTime != 0L) {
            lastTickTime = System.currentTimeMillis()
        }
    }

    fun setBookReady(ready: Boolean) {
        _isBookReady.value = ready
        if (ready) {
            hasEverLoaded = true
        }
    }

    fun updateProgress(pageIndex: Int, totalPages: Int, locator: Locator) {
        val pub = _publication.value ?: return
        val chapterLabel = pub.let {
            val totalChapters = it.readingOrder.size
            val currentChapterIndex = it.readingOrder.indexOfFirst { link -> link.url() == locator.href }
            if (currentChapterIndex != -1) {
                "Chapter ${currentChapterIndex + 1} of $totalChapters"
            } else ""
        }

        val progression = locator.locations.totalProgression?.toFloat() ?: (pageIndex.toFloat() / (totalPages - 1).coerceAtLeast(1))
        val percentage = (progression * 100).coerceIn(0f, 100f)
        val isCompletedNow = progression >= COMPLETION_THRESHOLD
        
        _progress.value = ReadingProgress(
            value = progression,
            pageLabel = "Page ${pageIndex + 1} of $totalPages",
            percentageLabel = if (percentage > 99.5f) "100%" else "${kotlin.math.round(percentage).toInt()}%",
            chapterLabel = chapterLabel
        )

        // Append reading time and sync completion status
        syncReadingProgress(isCompletedNow, forceSync = false)

        // Update the initial locator so that if the fragment is recreated (e.g. theme change),
        // it stays on the current page.
        _initialLocator.value = locator
        lastLocator = locator

        // Save progress to Room database on every page turn
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            database.bookDao().updateActiveBookProgression(locator.toJSON().toString())
        }
    }

    /**
     * Persists the current reading position. Writes only the fields that changed, on both the
     * personal copy and the group copy, instead of reading the whole library map and writing it
     * back — which dropped any edit another device had made in between.
     */
    fun saveProgressionToFirestore() {
        val pub = _publication.value ?: return
        val locator = lastLocator ?: return
        val uid = auth.currentUser?.uid ?: return
        val authorName = pub.metadata.authors.firstOrNull()?.name ?: UNKNOWN_AUTHOR
        val bookKey = bookKeyOf(pub)
        val progressionJson = locator.toJSON().toString()
        val isCompletedNow = _progress.value.value >= COMPLETION_THRESHOLD
        val now = System.currentTimeMillis()

        saveScope.launch {
            try {
                val existing = users.book(uid, bookKey)
                val completedTimestamp = when {
                    existing != null && existing.completedTimestamp > 0 -> existing.completedTimestamp
                    isCompletedNow -> now
                    else -> 0L
                }

                val fields = mutableMapOf<String, Any?>(
                    "title" to (pub.metadata.title ?: ""),
                    "author" to authorName,
                    "progress" to progressionJson,
                    "addedTimestamp" to (existing?.addedTimestamp ?: now)
                )
                if (completedTimestamp > 0) fields["completedTimestamp"] = completedTimestamp

                users.mergeBookFields(uid, bookKey, fields)

                if (isCompletedNow && completedTimestamp > 0) {
                    val database = AppRoomDatabase.getDatabase(getApplication())
                    database.bookDao().setCompletedTimestamp(loadedBookId ?: 0, completedTimestamp)
                }

                val groupId = loadedGroupId?.takeIf { it != NO_GROUP && it.isNotBlank() }
                if (groupId != null) {
                    groups.mergeGroupBookFields(groupId, uid, bookKey, fields)
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to update Firestore progress", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        groupBookListener?.remove()
        groupInfoListener?.remove()
        annotationsListener?.remove()
        endReadingSession()
        saveProgressionToFirestore()
    }

    fun loadActiveBook() {
        Log.d("ReaderViewModel", "loadActiveBook called")
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val activeBook = database.bookDao().getActive()
            Log.d("ReaderViewModel", "Active book: $activeBook")

            if (activeBook == null) {
                _error.value = "No active book found"
                return@launch
            }

            val uid = auth.currentUser?.uid
            val userModel: UserModel? = if (uid != null) {
                try {
                    users.user(uid)
                } catch (e: Exception) {
                    Log.e("ReaderViewModel", "Failed to load user", e)
                    null
                }
            } else {
                null
            }

            val bookKey = "${activeBook.title}_${activeBook.author}"
            val activeGroupId = userModel?.activeGroup?.takeIf { it.isNotBlank() } ?: NO_GROUP
            val resolvedGroupId = resolveGroupForBook(activeGroupId, activeBook.title, activeBook.author)

            // Set the last used color from user profile if available
            userModel?.lastHighlightColor?.let {
                _selectedHighlightColor.value = it
            }

            // Load local user preferences
            val prefs = database.userPreferencesDao().getPreferences().firstOrNull()
            prefs?.let {
                _fontSize.value = it.fontSize
                _theme.value = it.theme
            }

            // If it's a different book OR the resolved active group has changed, clear state immediately
            if (activeBook.id != loadedBookId || resolvedGroupId != loadedGroupId) {
                Log.d("ReaderViewModel", "Switching book/group: book ${loadedBookId}->${activeBook.id}, group ${loadedGroupId}->${resolvedGroupId}")
                if (loadedBookId != null) {
                    endReadingSession()
                }
                groupBookListener?.remove()
                groupBookListener = null
                groupInfoListener?.remove()
                groupInfoListener = null
                annotationsListener?.remove()
                annotationsListener = null
                _isAdmin.value = false
                _publication.value = null
                _isBookReady.value = false
                hasEverLoaded = false
                _progress.value = ReadingProgress()
                _initialLocator.value = null
                _highlights.value = emptyList()
                _notes.value = emptyList()
                _groupProgress.value = emptyList()
                loadedGroupId = resolvedGroupId

                // Start listening to group info if in a group
                if (resolvedGroupId != NO_GROUP) {
                    groupInfoListener = groups.listenGroup(resolvedGroupId) { group ->
                        _isAdmin.value = group?.groupMembers?.get(uid)?.admin == true
                    }
                }
            } else if (_publication.value != null) {
                Log.d("ReaderViewModel", "Book ${activeBook.id} already loaded, resuming session")
                val currentProgress = _progress.value.value
                startReadingSession(currentProgress)
                return@launch
            }

            val bookFile = File(activeBook.filePath)
            Log.d("ReaderViewModel", "Book file path: ${activeBook.filePath}, exists: ${bookFile.exists()}")
            if (!bookFile.exists()) {
                _error.value = "Book file not found at ${activeBook.filePath}"
                return@launch
            }

            loadedBookId = activeBook.id

            val progressionToUse = storedProgression(uid, userModel, bookKey, activeBook, resolvedGroupId)
            val initialProgress = progressionToUse?.let { progressionOf(it) } ?: 0f

            startReadingSession(initialProgress)
            openBook(bookFile, progression = progressionToUse)

            if (uid != null) {
                // Annotations come from the book's own subcollection in both modes; only the
                // reading positions of other members are group-specific.
                observeAnnotations(annotationScope(resolvedGroupId, uid), bookKey)
                if (resolvedGroupId != NO_GROUP) {
                    observeGroupProgress(resolvedGroupId, bookKey, uid)
                } else {
                    _groupProgress.value = emptyList()
                }
            }
        }
    }

    /**
     * A book that has not been uploaded to the active group is read in personal mode, so its
     * annotations stay private rather than appearing in the group's shared set.
     */
    private suspend fun resolveGroupForBook(
        activeGroupId: String,
        title: String,
        author: String
    ): String {
        if (activeGroupId == NO_GROUP) return NO_GROUP
        return try {
            val groupBook = groups.groupBook(activeGroupId)
            if (groupBook == null) {
                Log.d("ReaderViewModel", "GroupBook doc for $activeGroupId not found. Using personal mode.")
                return NO_GROUP
            }
            val uploaded = groupBook.bookProgression.values.any { memberBooks ->
                memberBooks.values.any { entry ->
                    BookModel.fromAny(entry)?.let { it.title == title && it.author == author } ?: false
                }
            }
            if (uploaded) {
                activeGroupId
            } else {
                Log.d("ReaderViewModel", "Book $title not uploaded to group $activeGroupId. Using personal mode.")
                NO_GROUP
            }
        } catch (e: Exception) {
            Log.e("ReaderViewModel", "Error checking book in group", e)
            NO_GROUP
        }
    }

    /** Personal progress first, then this member's group copy, then whatever Room has. */
    private suspend fun storedProgression(
        uid: String?,
        userModel: UserModel?,
        bookKey: String,
        activeBook: com.indeavour.coreader.model.room.RoomBook,
        resolvedGroupId: String
    ): String? {
        var stored: String? = userModel?.books?.get(bookKey)?.progress

        if (stored.isNullOrBlank() && uid != null && resolvedGroupId != NO_GROUP) {
            try {
                val inGroup = groups.groupBook(resolvedGroupId)?.getBookModel(uid, bookKey)
                if (inGroup?.title == activeBook.title && inGroup.author == activeBook.author) {
                    stored = inGroup.progress
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to fetch group progress", e)
            }
        }

        return stored?.takeIf { it.isNotBlank() }
            ?: activeBook.progression?.takeIf { it.isNotBlank() }
    }

    private fun progressionOf(locatorJson: String): Float = try {
        val json = org.json.JSONObject(locatorJson)
        json.optJSONObject("locations")?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
    } catch (e: Exception) {
        0f
    }

    fun addHighlight(locator: Locator) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)
        val locatorJson = locator.toJSON().toString()
        val position = LocatorFields.of(locatorJson)

        val annotation = AnnotationModel(
            id = annotations.newId(),
            ownerUid = uid,
            type = AnnotationModel.TYPE_HIGHLIGHT,
            locator = locatorJson,
            href = position.href,
            totalProgression = position.totalProgression,
            color = _selectedHighlightColor.value,
            createdAt = System.currentTimeMillis()
        )

        saveScope.launch {
            try {
                annotations.add(scope, bookKey, annotation)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to add highlight", e)
            }
        }
    }

    fun addNote(locator: Locator, content: String) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)
        val locatorJson = locator.toJSON().toString()
        val position = LocatorFields.of(locatorJson)

        val annotation = AnnotationModel(
            id = annotations.newId(),
            ownerUid = uid,
            type = AnnotationModel.TYPE_NOTE,
            locator = locatorJson,
            href = position.href,
            totalProgression = position.totalProgression,
            color = _selectedHighlightColor.value,
            content = content,
            createdAt = System.currentTimeMillis()
        )

        saveScope.launch {
            try {
                annotations.add(scope, bookKey, annotation)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to add note", e)
            }
        }
    }

    fun deleteHighlight(highlight: HighlightData) {
        deleteAnnotation(highlight.userId, highlight.id, "highlight")
    }

    fun deleteNote(note: NoteData) {
        deleteAnnotation(note.userId, note.id, "note")
    }

    /**
     * Deleting by document id removes exactly the annotation that was tapped. The previous
     * approach matched on locator and text, so two identical notes in the same place were
     * indistinguishable and both disappeared.
     */
    private fun deleteAnnotation(ownerUid: String, annotationId: String, kind: String) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        if (ownerUid != uid && !_isAdmin.value) return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)

        viewModelScope.launch {
            try {
                annotations.delete(scope, bookKey, annotationId)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to delete $kind", e)
            }
        }
    }

    fun addReplyToNote(note: NoteData, replyText: String) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)
        val reply = ReplyModel(userId = uid, text = replyText, timestamp = System.currentTimeMillis())

        viewModelScope.launch {
            try {
                // arrayUnion appends without rewriting the note, so two readers replying at the
                // same time no longer overwrite each other.
                annotations.addReply(scope, bookKey, note.id, reply)
                resolveUsernames(listOf(uid))
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to add reply", e)
            }
        }
    }

    fun deleteReply(note: NoteData, reply: ReplyModel) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)

        viewModelScope.launch {
            try {
                annotations.removeReply(scope, bookKey, note.id, reply)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to delete reply", e)
            }
        }
    }

    private fun openBook(file: File, progression: String? = null) {
        Log.d("ReaderViewModel", "openBook: ${file.absolutePath}, progression: $progression")
        if (!hasEverLoaded) {
            _isBookReady.value = false
        }
        viewModelScope.launch {
            repository.openBook(file)
                .onSuccess { pub ->
                    Log.d("ReaderViewModel", "Book opened successfully: ${pub.metadata.title}")
                    
                    val locator = if (progression != null) {
                        try {
                            Locator.fromJSON(org.json.JSONObject(progression))
                        } catch (e: Exception) {
                            Log.e("ReaderViewModel", "Failed to parse initial progression JSON", e)
                            null
                        }
                    } else {
                        null
                    }
                    
                    _initialLocator.value = locator
                    lastLocator = locator
                    _publication.value = pub
                }.onFailure {
                    Log.e("ReaderViewModel", "Failed to open book", it)
                    _error.value = it.message
                }
        }
    }

    companion object {
        /** Sentinel for "not reading in a group"; also the value stored in [loadedGroupId]. */
        private const val NO_GROUP = "none"
        private const val UNKNOWN_AUTHOR = "Unknown Author"
        private const val COMPLETION_THRESHOLD = 0.995f

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return ReaderViewModel(
                    application,
                    ReaderRepository(application)
                ) as T
            }
        }
    }
}
