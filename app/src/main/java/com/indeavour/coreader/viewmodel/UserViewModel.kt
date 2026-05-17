package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.indeavour.coreader.model.firebase.UserModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _user = MutableStateFlow<UserModel?>(null)
    val user: StateFlow<UserModel?> = _user

    init {
        fetchCurrentUser()
    }

    private fun fetchCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val document = firestore.collection("users").document(uid).get().await()
                val userModel = document.toObject(UserModel::class.java)
                _user.value = userModel
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateUsername(newUsername: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update("username", newUsername).await()
                _user.value = _user.value?.copy(username = newUsername)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
