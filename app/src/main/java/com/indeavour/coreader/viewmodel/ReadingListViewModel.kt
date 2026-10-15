package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.firebase.ReadingListBook
import com.indeavour.coreader.repository.ReadingListRepository
import com.indeavour.coreader.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ReadingListViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val readingLists = ReadingListRepository()
    private val users = UserRepository()

    private val _readingList = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val readingList: StateFlow<List<ReadingListBook>> = _readingList

    private val _suggestions = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val suggestions: StateFlow<List<ReadingListBook>> = _suggestions

    private var readingListListener: ListenerRegistration? = null

    init {
        startListener()
    }

    private fun startListener() {
        val uid = auth.currentUser?.uid ?: return
        readingListListener?.remove()
        readingListListener = readingLists.listen(uid) { books ->
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

        val current = list[currentIndex]
        val target = list[targetIndex]

        viewModelScope.launch {
            runCatching {
                readingLists.swapPositions(uid, current.id, current.position, target.id, target.position)
            }
        }
    }

    fun generateAISuggestions() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val libraryBooks = users.user(uid)?.books?.values?.filter { !it.isDeleted }
                    ?: emptyList()
                if (libraryBooks.isEmpty()) return@launch

                // Basic mock logic for suggestions: a real implementation would send the library
                // as context to a model. For now a "similar" book is seeded from a random author.
                val seedBook = libraryBooks.random()
                val suggestion = ReadingListBook(
                    id = UUID.randomUUID().toString(),
                    title = "The Next ${seedBook.title} Story",
                    author = seedBook.author,
                    addedTimestamp = System.currentTimeMillis(),
                    position = nextPosition(),
                    isSuggestion = true
                )

                val alreadyPresent = (_readingList.value + _suggestions.value)
                    .any { it.title == suggestion.title }
                if (!alreadyPresent) readingLists.put(uid, suggestion)
            } catch (e: Exception) {
                // Suggestions are optional; a failure leaves the list unchanged.
            }
        }
    }

    private fun nextPosition(): Int =
        _readingList.value.maxOfOrNull { it.position }?.plus(1) ?: 0
}
