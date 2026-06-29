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

    private val _usernames = MutableStateFlow<Map<String, String>>(emptyMap())
    val usernames: StateFlow<Map<String, String>> = _usernames

    private var loadedBookId: Int? = null
    private var loadedGroupId: String? = "none" // Use "none" as a sentinel for personal

    private var lastLocator: Locator? = null

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

        val progression = locator.locations.totalProgression?.toFloat() ?: (pageIndex.toFloat() / totalPages.coerceAtLeast(1))
        _progress.value = ReadingProgress(
            value = progression,
            pageLabel = "Page ${pageIndex + 1} of $totalPages",
            percentageLabel = "${(progression * 100).toInt()}%",
            chapterLabel = chapterLabel
        )

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
                        author = authorName
                    )
                    userBooks[bookKey] = userBook.copy(progress = progressionJson)
                    userRef.update("books", userBooks).await()

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
                            existingInGroup
                        } else {
                            BookModel(
                                title = pub.metadata.title ?: "",
                                author = authorName
                            )
                        }
                        userBooks[bookKey] = groupUserBook.copy(progress = progressionJson)
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
                _publication.value = null
                _isBookReady.value = false
                hasEverLoaded = false
                _progress.value = ReadingProgress()
                _initialLocator.value = null
                _highlights.value = emptyList()
                _notes.value = emptyList()
                loadedGroupId = resolvedGroupId
            } else if (_publication.value != null) {
                Log.d("ReaderViewModel", "Book ${activeBook.id} already loaded in group $resolvedGroupId, skipping")
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

                openBook(bookFile, progression = progressionToUse)

                // Load highlights and notes
                val highlightsList = mutableListOf<HighlightData>()
                val notesList = mutableListOf<NoteData>()
                val userIdsToFetch = mutableSetOf<String>()
                if (uid != null) {
                    try {
                        val bookKey = "${activeBook.title}_${activeBook.author}"
                        if (currentActiveGroupId != "none") {
                            Log.d("ReaderViewModel", "Loading ALL group annotations from group: $currentActiveGroupId")
                            val groupBookDoc = firestore.collection("groupBooks").document(currentActiveGroupId).get().await()
                            if (groupBookDoc.exists()) {
                                val groupBook = groupBookDoc.toObject(GroupBook::class.java)
                                
                                // Load annotations from EVERYONE in the group for this book
                                groupBook?.bookProgression?.forEach { (memberId, userBooks) ->
                                    val bookModel = BookModel.fromAny(userBooks[bookKey])
                                    if (bookModel != null && bookModel.title == activeBook.title && bookModel.author == activeBook.author) {
                                        userIdsToFetch.add(memberId)
                                        bookModel.highlights.forEach { entryJson ->
                                            try {
                                                val obj = org.json.JSONObject(entryJson)
                                                val locJson = obj.optJSONObject("locator")
                                                val hUserId = obj.optString("userId", memberId)
                                                val hColor = obj.optInt("color", 0x66FFFF00)
                                                val locator = if (locJson != null) {
                                                    Locator.fromJSON(locJson)
                                                } else {
                                                    Locator.fromJSON(obj)
                                                }
                                                if (locator != null) {
                                                    highlightsList.add(HighlightData(locator, hUserId, hColor))
                                                    userIdsToFetch.add(hUserId)
                                                }
                                            } catch (e: Exception) {}
                                        }
                                        bookModel.notes.forEach { entryJson ->
                                            try {
                                                val obj = org.json.JSONObject(entryJson)
                                                val locJson = obj.optJSONObject("locator")
                                                val hUserId = obj.optString("userId", memberId)
                                                val content = obj.optString("content", "")
                                                val hColor = obj.optInt("color", 0x66FFFF00)
                                                val locator = if (locJson != null) {
                                                    Locator.fromJSON(locJson)
                                                } else {
                                                    Locator.fromJSON(obj)
                                                }
                                                if (locator != null) {
                                                    notesList.add(NoteData(locator, hUserId, content, hColor))
                                                    userIdsToFetch.add(hUserId)
                                                }
                                            } catch (e: Exception) {}
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.d("ReaderViewModel", "No active group, loading personal annotations for: $bookKey")
                            userIdsToFetch.add(uid)
                            val userBook = userModel?.books?.get(bookKey)
                            userBook?.highlights?.forEach { entryJson ->
                                try {
                                    val obj = org.json.JSONObject(entryJson)
                                    val locJson = obj.optJSONObject("locator")
                                    val hUserId = obj.optString("userId", uid)
                                    val hColor = obj.optInt("color", 0x66FFFF00)
                                    val locator = if (locJson != null) {
                                        Locator.fromJSON(locJson)
                                    } else {
                                        Locator.fromJSON(obj)
                                    }
                                    if (locator != null) {
                                        highlightsList.add(HighlightData(locator, hUserId, hColor))
                                        userIdsToFetch.add(hUserId)
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
                                    val locator = if (locJson != null) {
                                        Locator.fromJSON(locJson)
                                    } else {
                                        Locator.fromJSON(obj)
                                    }
                                    if (locator != null) {
                                        notesList.add(NoteData(locator, hUserId, content, hColor))
                                        userIdsToFetch.add(hUserId)
                                    }
                                } catch (e: Exception) {}
                            }
                        }

                        // Fetch usernames for all unique user IDs
                        val nameMap = _usernames.value.toMutableMap()
                        userIdsToFetch.forEach { id ->
                            if (!nameMap.containsKey(id)) {
                                try {
                                    val userSnapshot = firestore.collection("users").document(id).get().await()
                                    val name = userSnapshot.getString("username") ?: "Unknown"
                                    nameMap[id] = name
                                } catch (e: Exception) {
                                    nameMap[id] = "Unknown"
                                }
                            }
                        }
                        _usernames.value = nameMap

                    } catch (e: Exception) {
                        Log.e("ReaderViewModel", "Failed to load annotations", e)
                    }
                }
                _highlights.value = highlightsList
                _notes.value = notesList
                Log.d("ReaderViewModel", "Total highlights loaded: ${highlightsList.size}, notes: ${notesList.size}")
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
                                author = pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author"
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
                                author = pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author"
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
                            author = pub.metadata.authors.firstOrNull()?.name ?: "Unknown Author"
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
