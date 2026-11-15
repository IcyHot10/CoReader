package com.indeavour.coreader.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.ai.AgentTool
import com.indeavour.coreader.model.ai.BookSuggestionAgent
import com.indeavour.coreader.model.ai.normalizedTitle
import com.indeavour.coreader.model.firebase.ReadingListBook
import com.indeavour.coreader.repository.ReadingListRepository
import com.indeavour.coreader.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class ReadingListViewModel : ViewModel() {

    private companion object {
        const val TAG = "ReadingListViewModel"
    }
    private val auth = FirebaseAuth.getInstance()
    private val readingLists = ReadingListRepository()
    private val users = UserRepository()

    private val _readingList = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val readingList: StateFlow<List<ReadingListBook>> = _readingList

    private val _suggestions = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val suggestions: StateFlow<List<ReadingListBook>> = _suggestions

    private val _isSuggesting = MutableStateFlow(false)
    val isSuggesting: StateFlow<Boolean> = _isSuggesting

    /** Every reading list entry, including dismissed suggestions, so the agent can avoid repeats. */
    private var allEntries: List<ReadingListBook> = emptyList()

    private var readingListListener: ListenerRegistration? = null

    private val suggestionAgent = BookSuggestionAgent(
        audience = "a single reader",
        tools = listOf(
            AgentTool(
                name = "get_library",
                description = "Books in the reader's library, most recently opened first, with whether each is finished."
            ) { libraryJson() },
            AgentTool(
                name = "get_reading_list",
                description = "The reader's to-read list in priority order, plus pending and dismissed AI suggestions."
            ) { readingListJson() }
        )
    )

    init {
        startListener()
    }

    private fun startListener() {
        val uid = auth.currentUser?.uid ?: return
        readingListListener?.remove()
        readingListListener = readingLists.listen(uid) { books ->
            allEntries = books
            _readingList.value = books.filter { !it.isSuggestion }
            _suggestions.value = books.filter { it.isSuggestion && !it.isDismissed }
        }
    }

    override fun onCleared() {
        super.onCleared()
        readingListListener?.remove()
    }

    fun addBook(title: String, author: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val book = ReadingListBook(
            id = UUID.randomUUID().toString(),
            title = title,
            author = author,
            addedTimestamp = System.currentTimeMillis(),
            position = nextPosition(),
            isSuggestion = false
        )
        viewModelScope.launch {
            try {
                readingLists.put(uid, book)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun removeBook(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { readingLists.remove(uid, bookId) }
        }
    }

    fun dismissSuggestion(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { readingLists.updateFields(uid, bookId, mapOf("isDismissed" to true)) }
        }
    }

    fun convertSuggestionToToRead(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        val position = nextPosition()
        viewModelScope.launch {
            runCatching {
                readingLists.updateFields(
                    uid,
                    bookId,
                    mapOf(
                        "isSuggestion" to false,
                        "addedTimestamp" to System.currentTimeMillis(),
                        "position" to position
                    )
                )
            }
        }
    }

    fun moveBook(bookId: String, moveUp: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val list = _readingList.value
        val currentIndex = list.indexOfFirst { it.id == bookId }
        if (currentIndex == -1) return

        val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex !in list.indices) return

        val reorderedList = list.toMutableList()
        val movedBook = reorderedList.removeAt(currentIndex)
        reorderedList.add(targetIndex, movedBook)

        val positionUpdates = mutableMapOf<String, Int>()
        reorderedList.forEachIndexed { index, book ->
            if (book.position != index) {
                positionUpdates[book.id] = index
            }
        }

        if (positionUpdates.isNotEmpty()) {
            viewModelScope.launch {
                runCatching {
                    readingLists.updatePositions(uid, positionUpdates)
                }
            }
        }
    }

    /**
     * Asks the AI agent for new books and saves them as pending suggestions.
     * [onResult] receives an error message, or null on success.
     */
    fun generateAISuggestions(request: String, onResult: (String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult("You need to be signed in")
        if (_isSuggesting.value) return
        _isSuggesting.value = true
        viewModelScope.launch {
            try {
                Log.d(TAG, "Suggest: loading library")
                val libraryTitles = users.user(uid)?.books?.values.orEmpty().map { normalizedTitle(it.title) }
                val known = (libraryTitles + allEntries.map { normalizedTitle(it.title) }).toSet()
                Log.d(TAG, "Suggest: calling agent")
                val fresh = suggestionAgent.suggest(request)
                    .filter { normalizedTitle(it.title) !in known }
                    .distinctBy { normalizedTitle(it.title) }
                if (fresh.isEmpty()) return@launch onResult("No new suggestions this time, try again")

                Log.d(TAG, "Suggest: saving ${fresh.size} suggestions")
                fresh.forEach { suggestion ->
                    readingLists.put(
                        uid,
                        ReadingListBook(
                            id = UUID.randomUUID().toString(),
                            title = suggestion.title,
                            author = suggestion.author,
                            addedTimestamp = System.currentTimeMillis(),
                            isSuggestion = true,
                            reason = suggestion.reason
                        )
                    )
                }
                Log.d(TAG, "Suggest: done")
                onResult(null)
            } catch (e: Exception) {
                Log.e(TAG, "Suggest failed", e)
                onResult(e.message ?: "Failed to get suggestions")
            } finally {
                _isSuggesting.value = false
            }
        }
    }

    private suspend fun libraryJson(): JsonArray {
        val uid = auth.currentUser?.uid ?: return JsonArray(emptyList())
        val books = users.user(uid)?.books?.values.orEmpty()
            .filter { !it.isDeleted }
            .sortedByDescending { it.progressionUpdatedTimestamp }
            .take(50)
        return buildJsonArray {
            books.forEach { book ->
                add(buildJsonObject {
                    put("title", book.title)
                    put("author", book.author)
                    put("finished", book.completedTimestamp > 0)
                })
            }
        }
    }

    private fun readingListJson(): JsonArray = buildJsonArray {
        allEntries.forEach { book ->
            add(buildJsonObject {
                put("title", book.title)
                put("author", book.author)
                put(
                    "status",
                    when {
                        !book.isSuggestion -> "to-read"
                        book.isDismissed -> "dismissed suggestion"
                        else -> "pending suggestion"
                    }
                )
            })
        }
    }

    private fun nextPosition(): Int =
        _readingList.value.maxOfOrNull { it.position }?.plus(1) ?: 0
}
