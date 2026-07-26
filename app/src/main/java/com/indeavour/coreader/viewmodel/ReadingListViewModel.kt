package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.ReadingListBook
import com.indeavour.coreader.model.firebase.ReadingListDocument
import com.indeavour.coreader.model.firebase.UserModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ReadingListViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _readingList = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val readingList: StateFlow<List<ReadingListBook>> = _readingList

    private val _suggestions = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val suggestions: StateFlow<List<ReadingListBook>> = _suggestions

    private var readingListListener: ListenerRegistration? = null

    init {
        startReadingListListener()
    }

    private fun startReadingListListener() {
        val uid = auth.currentUser?.uid ?: return
        readingListListener?.remove()
        readingListListener = firestore.collection("readingLists").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val doc = snapshot?.toObject(ReadingListDocument::class.java)
                val items = doc?.books?.values?.sortedBy { it.position } ?: emptyList()
                _readingList.value = items.filter { !it.isSuggestion }
                _suggestions.value = items.filter { it.isSuggestion && !it.isDismissed }
            }
    }

    override fun onCleared() {
        super.onCleared()
        readingListListener?.remove()
    }

    fun addBook(title: String, author: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val bookId = UUID.randomUUID().toString()
        val nextPosition = _readingList.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        val book = ReadingListBook(
            id = bookId,
            title = title,
            author = author,
            addedTimestamp = System.currentTimeMillis(),
            position = nextPosition,
            isSuggestion = false
        )
        viewModelScope.launch {
            try {
                val docRef = firestore.collection("readingLists").document(uid)
                try {
                    docRef.update("books.$bookId", book).await()
                } catch (e: Exception) {
                    // If update fails, document might not exist, try to set it
                    firestore.collection("readingLists").document(uid).set(
                        mapOf(
                            "userId" to uid,
                            "books" to mapOf(bookId to book)
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun removeBook(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (bookId.isBlank()) return
        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid)
                    .update("books.$bookId", com.google.firebase.firestore.FieldValue.delete()).await()
            } catch (_: Exception) {}
        }
    }

    fun dismissSuggestion(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (bookId.isBlank()) return
        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid)
                    .update("books.$bookId.isDismissed", true).await()
            } catch (_: Exception) {}
        }
    }

    fun convertSuggestionToToRead(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (bookId.isBlank()) return
        val nextPosition = _readingList.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid).update(
                    "books.$bookId.isSuggestion", false,
                    "books.$bookId.addedTimestamp", System.currentTimeMillis(),
                    "books.$bookId.position", nextPosition
                ).await()
            } catch (_: Exception) {}
        }
    }

    fun moveBook(bookId: String, moveUp: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val list = _readingList.value
        val currentIndex = list.indexOfFirst { it.id == bookId }
        if (currentIndex == -1) return

        val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex < 0 || targetIndex >= list.size) return

        val currentBook = list[currentIndex]
        val targetBook = list[targetIndex]

        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid).update(
                    "books.${currentBook.id}.position", targetBook.position,
                    "books.${targetBook.id}.position", currentBook.position
                ).await()
            } catch (_: Exception) {}
        }
    }

    fun generateAISuggestions() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                // Fetch user's library to get preferences
                val userDoc = firestore.collection("users").document(uid).get().await()
                val user = userDoc.toObject(UserModel::class.java)
                val libraryBooks = user?.books?.values?.filter { !it.isDeleted } ?: emptyList()
                
                if (libraryBooks.isEmpty()) return@launch

                // Basic Mock Logic for Suggestions:
                // In a real app, this would call a Gemini API with the context of libraryBooks.
                // For now, we simulate a "similar" book suggestion based on a random author in the library.
                val seedBook = libraryBooks.random()
                val bookId = UUID.randomUUID().toString()
                val nextPosition = _readingList.value.maxOfOrNull { it.position }?.plus(1) ?: 0
                val suggestion = ReadingListBook(
                    id = bookId,
                    title = "The Next ${seedBook.title} Story", // Mock title
                    author = seedBook.author, // Same author as suggestion
                    addedTimestamp = System.currentTimeMillis(),
                    position = nextPosition,
                    isSuggestion = true
                )

                // Only add if not already suggested/present
                val exists = _readingList.value.any { it.title == suggestion.title } || 
                             _suggestions.value.any { it.title == suggestion.title }
                
                if (!exists) {
                    val docRef = firestore.collection("readingLists").document(uid)
                    try {
                        docRef.update("books.$bookId", suggestion).await()
                    } catch (e: Exception) {
                        docRef.set(
                            mapOf(
                                "userId" to uid,
                                "books" to mapOf(bookId to suggestion)
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        ).await()
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
