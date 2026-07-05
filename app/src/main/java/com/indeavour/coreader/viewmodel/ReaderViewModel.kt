package com.indeavour.coreader.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.indeavour.coreader.AppRoomDatabase
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.GroupBook
import com.indeavour.coreader.model.firebase.UserModel
import com.indeavour.coreader.model.firebase.UserStats
import com.indeavour.coreader.model.firebase.UserStatsDocument
import com.indeavour.coreader.model.room.ReadingActivity
import com.indeavour.coreader.model.room.UserPreferences
import com.indeavour.coreader.repository.ReaderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val firestore = FirebaseFirestore.getInstance()

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

    data class HighlightData(
        val locator: Locator,
        val userId: String,
        val color: Int = 0x66FFFF00
    )

    data class NoteData(
        val locator: Locator,
        val userId: String,
        val content: String,
        val color: Int = 0x66FFFF00
    )

    private val _selectedHighlightColor = MutableStateFlow(0x66FFFF00)
    val selectedHighlightColor: StateFlow<Int> = _selectedHighlightColor

    private val _fontSize = MutableStateFlow(100f)
    val fontSize: StateFlow<Float> = _fontSize

    fun setSelectedHighlightColor(color: Int) {
        _selectedHighlightColor.value = color
        
        // Save to Firestore
        val uid = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore.collection("users").document(uid)
                    .update("lastHighlightColor", color).await()
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to save lastHighlightColor", e)
            }
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            database.userPreferencesDao().insertOrUpdate(UserPreferences(fontSize = size))
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

    private var loadedBookId: Int? = null
    private var loadedGroupId: String? = "none" // Use "none" as a sentinel for personal
    private var groupBookListener: com.google.firebase.firestore.ListenerRegistration? = null

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
        wasCompletedAtStart = initialProgress >= 0.995f
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
            
            viewModelScope.launch(Dispatchers.IO) {
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
                if (uid != null) {
                    try {
                        Log.d("ReaderViewModel", "Starting userStats sync for user: $uid. Seconds: $secondsToSave")
                        val statsRef = firestore.collection("userStats").document(uid)
                        val userRef = firestore.collection("users").document(uid)
                        
                        firestore.runTransaction { transaction ->
                            val snapshot = transaction.get(statsRef)
                            val statsDoc = if (snapshot.exists()) {
                                snapshot.toObject(UserStatsDocument::class.java) ?: UserStatsDocument(userId = uid)
                            } else {
                                UserStatsDocument(userId = uid)
                            }

                            // Also update the BookModel completed timestamp if needed
                            if (newlyCompleted) {
                                val userDoc = transaction.get(userRef)
                                val userModel = userDoc.toObject(UserModel::class.java)
                                if (userModel != null) {
                                    val pub = _publication.value
                                    val authorName = pub?.metadata?.authors?.firstOrNull()?.name ?: "Unknown Author"
                                    val bookKey = "${pub?.metadata?.title}_$authorName"
                                    val userBooks = userModel.books.toMutableMap()
                                    val book = userBooks[bookKey]
                                    if (book != null && book.completedTimestamp == 0L) {
                                        userBooks[bookKey] = book.copy(completedTimestamp = now)
                                        transaction.update(userRef, "books", userBooks)
                                    }
                                }
                            }
                            
                            val calendar = java.util.Calendar.getInstance()
                            val yearKey = calendar.get(java.util.Calendar.YEAR).toString()
                            val todayMillis = calendar.apply {
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }.timeInMillis

                            // Update Yearly Stats
                            val currentYearlyStats = statsDoc.yearlyStats[yearKey] ?: UserStats()
                            
                            // Update Streak logic
                            val lastReading = currentYearlyStats.lastReadingTimestamp
                            var newStreak = currentYearlyStats.currentStreak
                            if (lastReading > 0) {
                                val lastReadingCal = java.util.Calendar.getInstance().apply { timeInMillis = lastReading }
                                lastReadingCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                lastReadingCal.set(java.util.Calendar.MINUTE, 0)
                                lastReadingCal.set(java.util.Calendar.SECOND, 0)
                                lastReadingCal.set(java.util.Calendar.MILLISECOND, 0)
                                val lastReadingDay = lastReadingCal.timeInMillis
                                
                                val yesterday = todayMillis - java.util.concurrent.TimeUnit.DAYS.toMillis(1)
                                
                                when {
                                    lastReadingDay == todayMillis -> { /* Already read today */ }
                                    lastReadingDay == yesterday -> newStreak++
                                    else -> newStreak = 1
                                }
                            } else {
                                newStreak = 1
                            }

                            val updatedYearlyStats = currentYearlyStats.copy(
                                secondsRead = currentYearlyStats.secondsRead + secondsToSave,
                                booksCompleted = currentYearlyStats.booksCompleted + (if (newlyCompleted) 1 else 0),
                                currentStreak = newStreak,
                                maxStreak = maxOf(currentYearlyStats.maxStreak, newStreak),
                                lastReadingTimestamp = now
                            )
                            
                            val updatedYearlyMap = statsDoc.yearlyStats.toMutableMap()
                            updatedYearlyMap[yearKey] = updatedYearlyStats
                            
                            // Also update lifetime
                            val updatedLifetime = statsDoc.lifetimeStats.copy(
                                secondsRead = statsDoc.lifetimeStats.secondsRead + secondsToSave,
                                booksCompleted = statsDoc.lifetimeStats.booksCompleted + (if (newlyCompleted) 1 else 0),
                                currentStreak = newStreak,
                                maxStreak = maxOf(statsDoc.lifetimeStats.maxStreak, newStreak),
                                lastReadingTimestamp = now
                            )

                            transaction.set(statsRef, statsDoc.copy(
                                yearlyStats = updatedYearlyMap,
                                lifetimeStats = updatedLifetime
                            ))
                            Log.d("ReaderViewModel", "Transaction logic prepared for $uid")
                        }.await()

                        Log.d("ReaderViewModel", "userStats sync successful for $uid")

                        // Also log raw activity for historical record
                        firestore.collection("users").document(uid).collection("readingActivity").add(
                            ReadingActivity(
                                bookId = bookId,
                                timestamp = now,
                                durationSeconds = secondsToSave,
                                isCompletedEvent = newlyCompleted
                            )
                        ).await()

                    } catch (e: Exception) {
                        Log.e("ReaderViewModel", "Failed to sync reading activity to Firestore", e)
                    }
                }
            }
        }
    }

    fun endReadingSession() {
        val currentProgress = _progress.value.value
        val isCompletedNow = currentProgress >= 0.995f
        syncReadingProgress(isCompletedNow, forceSync = true)
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
        val isCompletedNow = progression >= 0.995f
        
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

    fun saveProgressionToFirestore() {
        val pub = _publication.value ?: return
        val locator = lastLocator ?: return
        val uid = auth.currentUser?.uid ?: return
        val authorName = pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author"
        val bookKey = "${pub.metadata.title}_$authorName"
        val progressionJson = locator.toJSON().toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userRef = firestore.collection("users").document(uid)
                val userDoc = userRef.get().await()
                if (userDoc.exists()) {
                    val userModel = userDoc.toObject(UserModel::class.java) ?: return@launch
                    val effectiveGroupId = loadedGroupId?.takeIf { it != "none" && it.isNotBlank() }

                    // Always update personal progress in UserModel
                    val userBooks = userModel.books.toMutableMap()
                    val userBook = userBooks[bookKey] ?: BookModel(
                        title = pub.metadata.title ?: "",
                        author = authorName,
                        addedTimestamp = System.currentTimeMillis() // Fallback if not exists
                    )
                    
                    val currentProgress = _progress.value.value
                    val isCompletedNow = currentProgress >= 0.995f
                    val finalCompletedTimestamp = if (isCompletedNow && userBook.completedTimestamp == 0L) {
                        System.currentTimeMillis()
                    } else {
                        userBook.completedTimestamp
                    }

                    userBooks[bookKey] = userBook.copy(
                        progress = progressionJson,
                        completedTimestamp = finalCompletedTimestamp
                    )
                    userRef.update("books", userBooks).await()

                    // Update local Room database if we just completed it here
                    if (isCompletedNow && finalCompletedTimestamp > 0) {
                        viewModelScope.launch {
                            val database = AppRoomDatabase.getDatabase(getApplication())
                            database.bookDao().setCompletedTimestamp(loadedBookId ?: 0, finalCompletedTimestamp)
                        }
                    }

                    // If in an active group, also update the group's progress model
                    if (effectiveGroupId != null) {
                        val groupBookRef = firestore.collection("groupBooks").document(effectiveGroupId)
                        val groupBookDoc = groupBookRef.get().await()

                        val groupProgression = if (groupBookDoc.exists()) {
                            val gb = groupBookDoc.toObject(GroupBook::class.java)
                            gb?.bookProgression?.mapValues { it.value.toMutableMap() }?.toMutableMap() ?: mutableMapOf()
                        } else {
                            mutableMapOf()
                        }

                        val userBooks = groupProgression[uid] ?: mutableMapOf()
                        val existingInGroup = BookModel.fromAny(userBooks[bookKey])
                        val groupUserBook = if (existingInGroup != null && existingInGroup.title == pub.metadata.title && existingInGroup.author == authorName) {
                            existingInGroup.copy(
                                progress = progressionJson,
                                completedTimestamp = if (isCompletedNow && existingInGroup.completedTimestamp == 0L) finalCompletedTimestamp else existingInGroup.completedTimestamp
                            )
                        } else {
                            BookModel(
                                title = pub.metadata.title ?: "",
                                author = authorName,
                                progress = progressionJson,
                                addedTimestamp = userBook.addedTimestamp,
                                completedTimestamp = if (isCompletedNow) finalCompletedTimestamp else 0L
                            )
                        }
                        userBooks[bookKey] = groupUserBook
                        groupProgression[uid] = userBooks

                        if (groupBookDoc.exists()) {
                            groupBookRef.update("bookProgression", groupProgression).await()
                        } else {
                            groupBookRef.set(GroupBook(groupCode = effectiveGroupId, bookProgression = groupProgression)).await()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to update Firestore progress", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        groupBookListener?.remove()
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

            // Fetch user model to check current active group
            val uid = auth.currentUser?.uid
            val userDoc = if (uid != null) firestore.collection("users").document(uid).get().await() else null
            val userModel = userDoc?.toObject(UserModel::class.java)
            val currentActiveGroupId = userModel?.activeGroup?.takeIf { it.isNotBlank() } ?: "none"

            // Check if this book is actually in the group's uploaded books
            var resolvedGroupId = currentActiveGroupId
            if (resolvedGroupId != "none") {
                try {
                    val groupBookDoc = firestore.collection("groupBooks").document(resolvedGroupId).get().await()
                    if (groupBookDoc.exists()) {
                        val groupBook = groupBookDoc.toObject(GroupBook::class.java)
                        val bookExistsInGroup = groupBook?.bookProgression?.values?.any { userBooks ->
                            userBooks.values.any { BookModel.fromAny(it)?.let { bm -> bm.title == activeBook.title && bm.author == activeBook.author } ?: false }
                        } ?: false

                        if (!bookExistsInGroup) {
                            Log.d("ReaderViewModel", "Book ${activeBook.title} not uploaded to group $resolvedGroupId. Using personal mode.")
                            resolvedGroupId = "none"
                        }
                    } else {
                        Log.d("ReaderViewModel", "GroupBook doc for $resolvedGroupId not found. Using personal mode.")
                        resolvedGroupId = "none"
                    }
                } catch (e: Exception) {
                    Log.e("ReaderViewModel", "Error checking book in group", e)
                    resolvedGroupId = "none"
                }
            }

            // Set the last used color from user profile if available
            userModel?.lastHighlightColor?.let {
                _selectedHighlightColor.value = it
            }

            // Load local user preferences
            val prefs = database.userPreferencesDao().getPreferences().firstOrNull()
            prefs?.let {
                _fontSize.value = it.fontSize
            }

            // If it's a different book OR the resolved active group has changed, clear state immediately
            if (activeBook.id != loadedBookId || resolvedGroupId != loadedGroupId) {
                Log.d("ReaderViewModel", "Switching book/group: book ${loadedBookId}->${activeBook.id}, group ${loadedGroupId}->${resolvedGroupId}")
                if (loadedBookId != null) {
                    endReadingSession()
                }
                groupBookListener?.remove()
                groupBookListener = null
                _publication.value = null
                _isBookReady.value = false
                hasEverLoaded = false
                _progress.value = ReadingProgress()
                _initialLocator.value = null
                _highlights.value = emptyList()
                _notes.value = emptyList()
                loadedGroupId = resolvedGroupId
            } else if (_publication.value != null) {
                Log.d("ReaderViewModel", "Book ${activeBook.id} already loaded, resuming session")
                val currentProgress = _progress.value.value
                startReadingSession(currentProgress)
                return@launch
            }

            val bookFile = File(activeBook.filePath)
            Log.d("ReaderViewModel", "Book file path: ${activeBook.filePath}, exists: ${bookFile.exists()}")
            if (bookFile.exists()) {
                loadedBookId = activeBook.id

                // Fetch progress from Firestore
                var progressFromFirestore: String? = null
                if (uid != null) {
                    try {
                        val bookKey = "${activeBook.title}_${activeBook.author}"
                        
                        // First check personal progression (now always updated)
                        progressFromFirestore = userModel?.books?.get(bookKey)?.progress
                        
                        // Fallback to group progression if personal is missing and we are in a group
                        if (progressFromFirestore.isNullOrBlank() && loadedGroupId != "none") {
                            val groupBookDoc = firestore.collection("groupBooks").document(loadedGroupId!!).get().await()
                            val groupBook = groupBookDoc.toObject(GroupBook::class.java)
                            val userBookInGroup = groupBook?.getBookModel(uid, bookKey)
                            if (userBookInGroup?.title == activeBook.title && userBookInGroup.author == activeBook.author) {
                                progressFromFirestore = userBookInGroup.progress
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ReaderViewModel", "Failed to fetch Firestore progress", e)
                    }
                }

                val progressionToUse = if (!progressFromFirestore.isNullOrBlank()) {
                    progressFromFirestore
                } else if (!activeBook.progression.isNullOrBlank()) {
                    activeBook.progression
                } else {
                    null
                }

                val initialProgress = progressionToUse?.let {
                    try {
                        val json = org.json.JSONObject(it)
                        val locations = json.optJSONObject("locations")
                        locations?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
                    } catch (e: Exception) { 0f }
                } ?: 0f

                startReadingSession(initialProgress)
                openBook(bookFile, progression = progressionToUse)

                // Set up real-time listener for group progress and annotations
                if (currentActiveGroupId != "none") {
                    val bookKey = "${activeBook.title}_${activeBook.author}"
                    groupBookListener = firestore.collection("groupBooks").document(currentActiveGroupId)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                Log.e("ReaderViewModel", "GroupBook listener failed", e)
                                return@addSnapshotListener
                            }

                            if (snapshot != null && snapshot.exists()) {
                                Log.d("ReaderViewModel", "GroupBook snapshot received for ${snapshot.id}")
                                val groupBook = snapshot.toObject(GroupBook::class.java)
                                val highlightsList = mutableListOf<HighlightData>()
                                val notesList = mutableListOf<NoteData>()
                                val groupProgressList = mutableListOf<MemberProgress>()
                                val userIdsToFetch = mutableSetOf<String>()

                                groupBook?.bookProgression?.forEach { (memberId, userBooks) ->
                                    val bookModel = BookModel.fromAny(userBooks[bookKey])
                                    if (bookModel != null && bookModel.title == activeBook.title && bookModel.author == activeBook.author) {
                                        userIdsToFetch.add(memberId)

                                        // Load progress
                                        if (bookModel.progress.isNotBlank()) {
                                            try {
                                                val loc = Locator.fromJSON(org.json.JSONObject(bookModel.progress))
                                                if (loc != null) {
                                                    Log.d("ReaderViewModel", "Found progress for $memberId: ${loc.href}")
                                                    groupProgressList.add(MemberProgress(memberId, _usernames.value[memberId] ?: "", loc))
                                                    
                                                    // Update remote progression if it's the current user
                                                    if (memberId == uid) {
                                                        _remoteProgression.value = loc
                                                    }
                                                }
                                            } catch (e: Exception) {}
                                        }

                                        bookModel.highlights.forEach { entryJson ->
                                            try {
                                                val obj = org.json.JSONObject(entryJson)
                                                val locJson = obj.optJSONObject("locator")
                                                val hUserId = obj.optString("userId", memberId)
                                                val hColor = obj.optInt("color", 0x66FFFF00)
                                                val locator = if (locJson != null) Locator.fromJSON(locJson) else Locator.fromJSON(obj)
                                                if (locator != null) {
                                                    highlightsList.add(HighlightData(locator, hUserId, hColor))
                                                    userIdsToFetch.add(hUserId)
                                                }
                                            } catch (ex: Exception) {}
                                        }
                                        bookModel.notes.forEach { entryJson ->
                                            try {
                                                val obj = org.json.JSONObject(entryJson)
                                                val locJson = obj.optJSONObject("locator")
                                                val hUserId = obj.optString("userId", memberId)
                                                val content = obj.optString("content", "")
                                                val hColor = obj.optInt("color", 0x66FFFF00)
                                                val locator = if (locJson != null) Locator.fromJSON(locJson) else Locator.fromJSON(obj)
                                                if (locator != null) {
                                                    notesList.add(NoteData(locator, hUserId, content, hColor))
                                                    userIdsToFetch.add(hUserId)
                                                }
                                            } catch (ex: Exception) {}
                                        }
                                    }
                                }

                                // Update state
                                _highlights.value = highlightsList
                                _notes.value = notesList
                                
                                // Fetch missing usernames
                                viewModelScope.launch {
                                    val nameMap = _usernames.value.toMutableMap()
                                    var changed = false
                                    userIdsToFetch.forEach { id ->
                                        if (!nameMap.containsKey(id)) {
                                            try {
                                                val userSnapshot = firestore.collection("users").document(id).get().await()
                                                nameMap[id] = userSnapshot.getString("username") ?: "Unknown"
                                                changed = true
                                            } catch (ex: Exception) {
                                                nameMap[id] = "Unknown"
                                                changed = true
                                            }
                                        }
                                    }
                                    if (changed) {
                                        _usernames.value = nameMap
                                    }
                                    _groupProgress.value = groupProgressList.map { 
                                        it.copy(username = nameMap[it.userId] ?: "Unknown")
                                    }
                                }
                            }
                        }
                } else if (uid != null) {
                    // Load personal annotations (one-time load is fine for personal)
                    viewModelScope.launch {
                        try {
                            val bookKey = "${activeBook.title}_${activeBook.author}"
                            val highlightsList = mutableListOf<HighlightData>()
                            val notesList = mutableListOf<NoteData>()
                            
                            val userBook = userModel?.books?.get(bookKey)
                            userBook?.highlights?.forEach { entryJson ->
                                try {
                                    val obj = org.json.JSONObject(entryJson)
                                    val locJson = obj.optJSONObject("locator")
                                    val hUserId = obj.optString("userId", uid)
                                    val hColor = obj.optInt("color", 0x66FFFF00)
                                    val locator = if (locJson != null) Locator.fromJSON(locJson) else Locator.fromJSON(obj)
                                    if (locator != null) {
                                        highlightsList.add(HighlightData(locator, hUserId, hColor))
                                    }
                                } catch (e: Exception) {}
                            }
                            userBook?.notes?.forEach { entryJson ->
                                try {
                                    val obj = org.json.JSONObject(entryJson)
                                    val locJson = obj.optJSONObject("locator")
                                    val hUserId = obj.optString("userId", uid)
                                    val content = obj.optString("content", "")
                                    val hColor = obj.optInt("color", 0x66FFFF00)
                                    val locator = if (locJson != null) Locator.fromJSON(locJson) else Locator.fromJSON(obj)
                                    if (locator != null) {
                                        notesList.add(NoteData(locator, hUserId, content, hColor))
                                    }
                                } catch (e: Exception) {}
                            }

                            val nameMap = _usernames.value.toMutableMap()
                            if (!nameMap.containsKey(uid)) {
                                nameMap[uid] = userModel?.username ?: "Unknown"
                                _usernames.value = nameMap
                            }
                            _highlights.value = highlightsList
                            _notes.value = notesList
                            _groupProgress.value = emptyList() // No group progress in personal mode
                        } catch (e: Exception) {
                            Log.e("ReaderViewModel", "Failed to load personal annotations", e)
                        }
                    }
                }
            } else {
                _error.value = "Book file not found at ${activeBook.filePath}"
            }
        }
    }

    fun addHighlight(locator: Locator) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val bookKey = "${pub.metadata.title}_${pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author"}"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userRef = firestore.collection("users").document(uid)
                val userDoc = userRef.get().await()
                if (userDoc.exists()) {
                    val userModel = userDoc.toObject(UserModel::class.java) ?: return@launch
                    val effectiveGroupId = loadedGroupId?.takeIf { it != "none" && it.isNotBlank() }

                    val highlightColor = _selectedHighlightColor.value
                    val highlightEntry = org.json.JSONObject().apply {
                        put("locator", locator.toJSON())
                        put("userId", uid)
                        put("color", highlightColor)
                    }.toString()

                    // Optimistically update local state
                    val nameMap = _usernames.value.toMutableMap()
                    if (!nameMap.containsKey(uid)) {
                        nameMap[uid] = userModel.username
                        _usernames.value = nameMap
                    }
                    _highlights.value = _highlights.value + HighlightData(locator, uid, highlightColor)

                    if (effectiveGroupId != null) {
                        val groupBookRef = firestore.collection("groupBooks").document(effectiveGroupId)
                        val groupBookDoc = groupBookRef.get().await()
                        
                        val groupProgression = if (groupBookDoc.exists()) {
                            val gb = groupBookDoc.toObject(GroupBook::class.java)
                            gb?.bookProgression?.mapValues { it.value.toMutableMap() }?.toMutableMap() ?: mutableMapOf()
                        } else {
                            mutableMapOf()
                        }

                        val userBooks = groupProgression[uid] ?: mutableMapOf()
                        val existingInGroup = BookModel.fromAny(userBooks[bookKey])
                        val userBook = if (existingInGroup != null && existingInGroup.title == pub.metadata.title && existingInGroup.author == (pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author")) {
                            existingInGroup
                        } else {
                            BookModel(
                                title = pub.metadata.title ?: "",
                                author = pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author",
                                addedTimestamp = System.currentTimeMillis()
                            )
                        }

                        val currentHighlights = userBook.highlights.toMutableList()
                        currentHighlights.add(highlightEntry)
                        userBooks[bookKey] = userBook.copy(highlights = currentHighlights)
                        groupProgression[uid] = userBooks
                        
                        if (groupBookDoc.exists()) {
                            groupBookRef.update("bookProgression", groupProgression).await()
                        } else {
                            groupBookRef.set(GroupBook(groupCode = effectiveGroupId, bookProgression = groupProgression)).await()
                        }
                    } else {
                        val books = userModel.books.toMutableMap()
                        val userBook = books[bookKey] ?: BookModel(
                            title = pub.metadata.title ?: "",
                            author = pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author"
                        )
                        val currentHighlights = userBook.highlights.toMutableList()
                        currentHighlights.add(highlightEntry)
                        books[bookKey] = userBook.copy(highlights = currentHighlights)
                        userRef.update("books", books).await()
                    }
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to add highlight to Firestore", e)
            }
        }
    }

    fun addNote(locator: Locator, content: String) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val bookKey = "${pub.metadata.title}_${pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author"}"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userRef = firestore.collection("users").document(uid)
                val userDoc = userRef.get().await()
                if (userDoc.exists()) {
                    val userModel = userDoc.toObject(UserModel::class.java) ?: return@launch
                    val effectiveGroupId = loadedGroupId?.takeIf { it != "none" && it.isNotBlank() }

                    val noteColor = _selectedHighlightColor.value
                    val noteEntry = org.json.JSONObject().apply {
                        put("locator", locator.toJSON())
                        put("userId", uid)
                        put("content", content)
                        put("color", noteColor)
                    }.toString()

                    // Optimistically update local state
                    val nameMap = _usernames.value.toMutableMap()
                    if (!nameMap.containsKey(uid)) {
                        nameMap[uid] = userModel.username
                        _usernames.value = nameMap
                    }
                    _notes.value = _notes.value + NoteData(locator, uid, content, noteColor)

                    if (effectiveGroupId != null) {
                        val groupBookRef = firestore.collection("groupBooks").document(effectiveGroupId)
                        val groupBookDoc = groupBookRef.get().await()
                        
                        val groupProgression = if (groupBookDoc.exists()) {
                            val gb = groupBookDoc.toObject(GroupBook::class.java)
                            gb?.bookProgression?.mapValues { it.value.toMutableMap() }?.toMutableMap() ?: mutableMapOf()
                        } else {
                            mutableMapOf()
                        }

                        val userBooks = groupProgression[uid] ?: mutableMapOf()
                        val existingInGroup = BookModel.fromAny(userBooks[bookKey])
                        val userBook = if (existingInGroup != null && existingInGroup.title == pub.metadata.title && existingInGroup.author == (pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author")) {
                            existingInGroup
                        } else {
                            BookModel(
                                title = pub.metadata.title ?: "",
                                author = pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author",
                                addedTimestamp = System.currentTimeMillis()
                            )
                        }

                        val currentNotes = userBook.notes.toMutableList()
                        currentNotes.add(noteEntry)
                        userBooks[bookKey] = userBook.copy(notes = currentNotes)
                        groupProgression[uid] = userBooks
                        
                        if (groupBookDoc.exists()) {
                            groupBookRef.update("bookProgression", groupProgression).await()
                        } else {
                            groupBookRef.set(GroupBook(groupCode = effectiveGroupId, bookProgression = groupProgression)).await()
                        }
                    } else {
                        val books = userModel.books.toMutableMap()
                        val userBook = books[bookKey] ?: BookModel(
                            title = pub.metadata.title ?: "",
                            author = pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author",
                            addedTimestamp = System.currentTimeMillis()
                        )
                        val currentNotes = userBook.notes.toMutableList()
                        currentNotes.add(noteEntry)
                        books[bookKey] = userBook.copy(notes = currentNotes)
                        userRef.update("books", books).await()
                    }
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to add note to Firestore", e)
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
