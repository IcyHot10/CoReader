package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.UserModel
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _user = MutableStateFlow<UserModel?>(null)
    val user: StateFlow<UserModel?> = _user

    private var userListener: ListenerRegistration? = null

    init {
        startUserListener()
    }

    private fun startUserListener() {
        val uid = auth.currentUser?.uid ?: return
        userListener?.remove()
        userListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _user.value = snapshot?.toObject(UserModel::class.java)
            }
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
    }

    fun updateUsername(newUsername: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update("username", newUsername).await()
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun addBook(book: BookModel, onResult: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val userRef = firestore.collection("users").document(uid)
                val userDoc = userRef.get().await()
                
                val currentBooks = if (userDoc.exists()) {
                    userDoc.toObject(UserModel::class.java)?.books?.toMutableMap() ?: mutableMapOf()
                } else {
                    mutableMapOf()
                }

                val bookKey = "${book.title}_${book.author}"

                if (currentBooks.containsKey(bookKey)) {
                    val existingBook = currentBooks[bookKey]!!
                    if (existingBook.isDeleted) {
                        currentBooks[bookKey] = existingBook.copy(isDeleted = false)
                        userRef.update("books", currentBooks).await()
                        onResult(true, "Book re-added to library")
                    } else {
                        onResult(false, "Book is already in the library")
                    }
                } else {
                    currentBooks[bookKey] = book
                    userRef.set(mapOf("books" to currentBooks), com.google.firebase.firestore.SetOptions.merge()).await()
                    onResult(true, "Book added to library")
                }
            } catch (e: Exception) {
                onResult(false, "Failed to update library: ${e.message}")
            }
        }
    }
}
