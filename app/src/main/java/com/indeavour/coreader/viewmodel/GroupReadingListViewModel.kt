package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.firebase.GroupReadingListBook
import com.indeavour.coreader.model.firebase.GroupReadingListComment
import com.indeavour.coreader.repository.GroupReadingListRepository
import com.indeavour.coreader.repository.GroupRepository
import com.indeavour.coreader.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GroupReadingListViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val groupReadingListRepo = GroupReadingListRepository()
    private val userRepository = UserRepository()
    private val groupRepository = GroupRepository()

    private val _activeGroupCode = MutableStateFlow<String?>(null)
    val activeGroupCode: StateFlow<String?> = _activeGroupCode

    private val _groupName = MutableStateFlow<String>("")
    val groupName: StateFlow<String> = _groupName

    private val _books = MutableStateFlow<List<GroupReadingListBook>>(emptyList())
    val books: StateFlow<List<GroupReadingListBook>> = _books

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private var userListener: ListenerRegistration? = null
    private var groupReadingListListener: ListenerRegistration? = null
    private var groupListener: ListenerRegistration? = null

    init {
        listenToUser()
    }

    private fun listenToUser() {
        val uid = auth.currentUser?.uid ?: return
        userListener = userRepository.listenUser(uid) { userModel ->
            var code = userModel?.activeGroup?.takeIf { it.isNotBlank() }
            if (code == null && !userModel?.groupIDs.isNullOrEmpty()) {
                code = userModel.groupIDs.firstOrNull()
            }
            _activeGroupCode.value = code
            if (code != null) {
                attachGroupListeners(code, uid)
            } else {
                _books.value = emptyList()
                _groupName.value = ""
                _isAdmin.value = false
            }
        }
    }

    private fun attachGroupListeners(code: String, uid: String) {
        groupReadingListListener?.remove()
        groupListener?.remove()

        groupReadingListListener = groupReadingListRepo.listen(code) { list ->
            _books.value = list
        }

        groupListener = groupRepository.listenGroup(code) { groupModel ->
            _groupName.value = groupModel?.groupName ?: code
            _isAdmin.value = groupModel?.groupMembers?.get(uid)?.admin == true
        }
    }

    fun addProposedBook(title: String, author: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(false)
        val code = _activeGroupCode.value ?: return onResult(false)
        if (title.isBlank()) return onResult(false)

        viewModelScope.launch {
            try {
                val username = userRepository.username(uid)
                val newBook = GroupReadingListBook(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    author = author,
                    addedByUid = uid,
                    addedByUsername = username,
                    addedTimestamp = System.currentTimeMillis(),
                    votes = listOf(uid)
                )
                groupReadingListRepo.put(code, newBook)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun toggleVote(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        val code = _activeGroupCode.value ?: return
        val book = _books.value.find { it.id == bookId } ?: return
        val hasVoted = book.votes.contains(uid)

        viewModelScope.launch {
            try {
                groupReadingListRepo.toggleVote(code, bookId, uid, hasVoted)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    fun addComment(bookId: String, commentText: String) {
        val uid = auth.currentUser?.uid ?: return
        val code = _activeGroupCode.value ?: return
        if (commentText.isBlank()) return

        viewModelScope.launch {
            try {
                val username = userRepository.username(uid)
                val newComment = GroupReadingListComment(
                    id = UUID.randomUUID().toString(),
                    userId = uid,
                    username = username,
                    text = commentText.trim(),
                    timestamp = System.currentTimeMillis()
                )
                groupReadingListRepo.addComment(code, bookId, newComment)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    fun removeComment(bookId: String, comment: GroupReadingListComment) {
        val code = _activeGroupCode.value ?: return
        viewModelScope.launch {
            try {
                groupReadingListRepo.removeComment(code, bookId, comment)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    fun removeBook(bookId: String) {
        val code = _activeGroupCode.value ?: return
        viewModelScope.launch {
            try {
                groupReadingListRepo.remove(code, bookId)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        groupReadingListListener?.remove()
        groupListener?.remove()
    }
}
