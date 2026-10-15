package com.indeavour.coreader.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.GroupBook
import com.indeavour.coreader.model.firebase.GroupModel
import com.indeavour.coreader.repository.GroupRepository
import com.indeavour.coreader.repository.JoinResult
import com.indeavour.coreader.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val groupRepository = GroupRepository()
    private val users = UserRepository()

    private val _groups = MutableStateFlow<List<GroupModel>>(emptyList())
    val groups: StateFlow<List<GroupModel>> = _groups

    private val _activeGroupId = MutableStateFlow<String?>(null)
    val activeGroupId: StateFlow<String?> = _activeGroupId

    private val _groupBooks = MutableStateFlow<Map<String, GroupBook>>(emptyMap())
    val groupBooks: StateFlow<Map<String, GroupBook>> = _groupBooks

    private val _usernames = MutableStateFlow<Map<String, String>>(emptyMap())
    val usernames: StateFlow<Map<String, String>> = _usernames

    // One registration per concern, each replaced rather than stacked. Previously the user
    // listener was re-attached after every mutation and never removed, so each write multiplied
    // the number of callbacks and the reads they triggered.
    private var userListener: ListenerRegistration? = null
    private var groupBooksListener: ListenerRegistration? = null
    private var activeGroupListener: ListenerRegistration? = null

    private var knownGroupIds: List<String> = emptyList()

    init {
        observeUser()
    }

    /**
     * The single source of change: the user document drives the active group, the group list and
     * the listeners below, so mutations do not need to re-read anything themselves.
     */
    private fun observeUser() {
        val uid = auth.currentUser?.uid ?: return
        userListener?.remove()
        userListener = users.listenUser(uid) { user ->
            val activeGroupId = user?.activeGroup?.takeIf { it.isNotBlank() }
            if (activeGroupId != _activeGroupId.value) {
                _activeGroupId.value = activeGroupId
                observeActiveGroup(activeGroupId)
            }

            val groupIds = user?.groupIDs?.filter { it.isNotBlank() } ?: emptyList()
            if (groupIds != knownGroupIds) {
                knownGroupIds = groupIds
                loadGroups(groupIds)
            }
        }
    }

    private fun loadGroups(groupIds: List<String>) {
        if (groupIds.isEmpty()) {
            _groups.value = emptyList()
            return
        }
        viewModelScope.launch {
            // Fetched concurrently; this used to be one blocking round trip per group, re-run on
            // every change to the user document.
            val loaded = groupRepository.groupsByIds(groupIds)
            _groups.value = loaded
            resolveUsernames(loaded.flatMap { it.groupMembers.keys })
        }
    }

    private fun observeActiveGroup(groupCode: String?) {
        groupBooksListener?.remove()
        groupBooksListener = null
        activeGroupListener?.remove()
        activeGroupListener = null

        if (groupCode == null) {
            _groupBooks.value = emptyMap()
            return
        }

        activeGroupListener = groupRepository.listenGroup(groupCode) { group ->
            group?.let { resolveUsernames(it.groupMembers.keys) }
        }

        groupBooksListener = groupRepository.listenGroupBooks(groupCode) { groupBook ->
            if (groupBook == null || groupBook.bookProgression.isEmpty()) {
                _groupBooks.value = emptyMap()
                return@listenGroupBooks
            }
            resolveUsernames(groupBook.bookProgression.keys)

            // Key is "<title>_<author>": every distinct book being read in the group.
            _groupBooks.value = groupBook.bookProgression.values
                .flatMap { it.keys }
                .distinct()
                .associateWith { groupBook }
        }
    }

    /** Resolves display names in one pass; [UserRepository] caches them across ViewModels. */
    private fun resolveUsernames(userIds: Collection<String>) {
        val missing = userIds.filter { it.isNotBlank() && !_usernames.value.containsKey(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val resolved = users.usernames(missing)
            if (resolved.isNotEmpty()) _usernames.update { it + resolved }
        }
    }

    fun fetchUsername(userId: String) = resolveUsernames(listOf(userId))

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        groupBooksListener?.remove()
        activeGroupListener?.remove()
    }

    fun uploadBookToGroup(book: BookModel, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(false)
        val groupCode = _activeGroupId.value ?: return onResult(false)

        viewModelScope.launch {
            try {
                // Annotations live in a subcollection now, so nothing has to be stripped first.
                groupRepository.upsertGroupBook(groupCode, uid, "${book.title}_${book.author}", book)
                onResult(true)
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Failed to upload book to $groupCode", e)
                onResult(false)
            }
        }
    }

    fun createGroup(groupName: String, onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val code = groupRepository.createGroup(uid, groupName)
            // No refetch: the user listener delivers the new group list.
            onResult(code != null, code)
        }
    }

    fun joinGroup(groupCode: String, onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            when (val result = groupRepository.joinGroup(uid, groupCode)) {
                JoinResult.Success -> onResult(true, null)
                JoinResult.NotFound -> onResult(false, "Group not found")
                JoinResult.AlreadyMember -> onResult(false, "Already in this group")
                is JoinResult.Failed -> onResult(false, result.message)
            }
        }
    }

    fun setActiveGroup(groupCode: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                groupRepository.setActiveGroup(uid, groupCode)
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Failed to set active group", e)
            }
        }
    }

    fun leaveGroup(groupCode: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            Log.e("GroupViewModel", "Leave group failed: no user logged in")
            return onResult(false)
        }
        removeMember(groupCode, uid, onResult)
    }

    fun removeMember(groupCode: String, memberId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(groupRepository.removeMember(groupCode, memberId))
        }
    }

    fun updateAdminStatus(
        groupCode: String,
        memberId: String,
        isAdmin: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val updated = groupRepository.setAdmin(groupCode, memberId, isAdmin)
            if (updated) loadGroups(knownGroupIds)
            onResult(updated)
        }
    }
}
