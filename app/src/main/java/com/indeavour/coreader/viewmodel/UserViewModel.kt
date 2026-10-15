package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.UserModel
import com.indeavour.coreader.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val users = UserRepository()

    private val _user = MutableStateFlow<UserModel?>(null)
    val user: StateFlow<UserModel?> = _user

    /** Display name, read from `profiles/{uid}` rather than the private user document. */
    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username

    private var userListener: ListenerRegistration? = null
    private var profileListener: ListenerRegistration? = null

    init {
        startListeners()
    }

    private fun startListeners() {
        val uid = auth.currentUser?.uid ?: return
        userListener?.remove()
        profileListener?.remove()
        userListener = users.listenUser(uid) { _user.value = it }
        profileListener = users.listenProfile(uid) { _username.value = it }
        viewModelScope.launch {
            users.ensureOwnProfile(uid, auth.currentUser?.email)
        }
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        profileListener?.remove()
    }

    fun updateUsername(newUsername: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                users.setUsername(uid, newUsername)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    /**
     * Adds a book to the cloud library, writing only that book's entry instead of reading the
     * whole map and writing it back — which previously discarded any change made meanwhile.
     */
    fun addBook(book: BookModel, onResult: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val bookKey = "${book.title}_${book.author}"
                val existing = users.book(uid, bookKey)

                when {
                    existing == null -> {
                        users.mergeBookFields(
                            uid,
                            bookKey,
                            mapOf(
                                "title" to book.title,
                                "author" to book.author,
                                "progress" to book.progress,
                                "isDeleted" to false,
                                "addedTimestamp" to book.addedTimestamp,
                                "completedTimestamp" to book.completedTimestamp
                            )
                        )
                        onResult(true, "Book added to library")
                    }
                    existing.isDeleted -> {
                        users.setBookDeleted(uid, bookKey, false)
                        onResult(true, "Book re-added to library")
                    }
                    else -> onResult(false, "Book is already in the library")
                }
            } catch (e: Exception) {
                onResult(false, "Failed to update library: ${e.message}")
            }
        }
    }
}
