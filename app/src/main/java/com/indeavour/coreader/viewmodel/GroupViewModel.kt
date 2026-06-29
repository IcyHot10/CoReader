package com.indeavour.coreader.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.GroupBook
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.firebase.GroupMember
import com.indeavour.coreader.model.firebase.GroupModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class GroupViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _groups = MutableStateFlow<List<GroupModel>>(emptyList())
    val groups: StateFlow<List<GroupModel>> = _groups

    private val _activeGroupId = MutableStateFlow<String?>(null)
    val activeGroupId: StateFlow<String?> = _activeGroupId

    private val _groupBooks = MutableStateFlow<Map<String, GroupBook>>(emptyMap())
    val groupBooks: StateFlow<Map<String, GroupBook>> = _groupBooks

    private val _usernames = MutableStateFlow<Map<String, String>>(emptyMap())
    val usernames: StateFlow<Map<String, String>> = _usernames

    private var groupBooksListener: ListenerRegistration? = null

    init {
        fetchUserGroups()
    }

    fun fetchUserGroups() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid)
            .addSnapshotListener { userDoc, error ->
                if (error != null) return@addSnapshotListener

                val newActiveGroupId = userDoc?.getString("activeGroup")?.takeIf { it.isNotBlank() }
                if (newActiveGroupId != _activeGroupId.value) {
                    _activeGroupId.value = newActiveGroupId
                    if (newActiveGroupId != null) {
                        listenToGroupBooks(newActiveGroupId)
                        fetchGroupUsernames(newActiveGroupId)
                    } else {
                        groupBooksListener?.remove()
                        _groupBooks.value = emptyMap()
                        _usernames.value = emptyMap()
                    }
                }
                
                val groupIDs = (userDoc?.get("groupIDs") as? List<String> ?: emptyList()).filter { it.isNotBlank() }

                if (groupIDs.isEmpty()) {
                    _groups.value = emptyList()
                    return@addSnapshotListener
                }

                viewModelScope.launch {
                    try {
                        val groupList = mutableListOf<GroupModel>()
                        for (id in groupIDs) {
                            val groupDoc = firestore.collection("groups").document(id).get().await()
                            groupDoc.toObject(GroupModel::class.java)?.let { groupList.add(it) }
                        }
                        _groups.value = groupList
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
            }
    }

    private fun listenToGroupBooks(groupCode: String) {
        groupBooksListener?.remove()
        groupBooksListener = firestore.collection("groupBooks").document(groupCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _groupBooks.value = emptyMap()
                    return@addSnapshotListener
                }
                
                val gb = try {
                    snapshot?.toObject(GroupBook::class.java)
                } catch (e: Exception) {
                    Log.e("GroupViewModel", "Error deserializing GroupBook", e)
                    null
                }
                if (gb == null || gb.bookProgression.isEmpty()) {
                    _groupBooks.value = emptyMap()
                    return@addSnapshotListener
                }

                // Fetch names for all users found in the book progression
                gb.bookProgression.keys.forEach { userId ->
                    fetchUsername(userId)
                }

                // Create a map where key is "Title_Author" and value is the GroupBook itself
                // This identifies all unique books being read in the group
                val uniqueBooks = gb.bookProgression.values
                    .flatMap { it.keys }
                    .filter { it != "deleted" }
                    .distinct()
                    .associateWith { gb }
                
                _groupBooks.value = uniqueBooks
            }
    }

    private fun fetchGroupUsernames(groupCode: String) {
        viewModelScope.launch {
            try {
                // Listen to the group document to get member IDs in real-time
                firestore.collection("groups").document(groupCode)
                    .addSnapshotListener { snapshot, _ ->
                        val group = snapshot?.toObject(GroupModel::class.java) ?: return@addSnapshotListener
                        group.groupMembers.keys.forEach { userId ->
                            fetchUsername(userId)
                        }
                    }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    fun fetchUsername(userId: String) {
        if (userId.isBlank()) return
        if (_usernames.value.containsKey(userId) && _usernames.value[userId] != "Unknown" && _usernames.value[userId] != "Unknown User") return

        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(userId).get().await()
                if (userDoc.exists()) {
                    val name = userDoc.getString("username")
                    if (!name.isNullOrBlank()) {
                        _usernames.update { it + (userId to name) }
                        return@launch
                    }
                }
                // Fallback or if name is null
                _usernames.update { it + (userId to "Unknown User") }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error fetching username for $userId", e)
                _usernames.update { it + (userId to "Error loading name") }
            }
        }
    }

    fun uploadBookToGroup(book: BookModel, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onResult(false)
            return
        }
        val groupCode = _activeGroupId.value ?: run {
            onResult(false)
            return
        }
        val docId = groupCode
        val bookKey = "${book.title}_${book.author}"
        val bookToUpload = book.copy(highlights = emptyList(), notes = emptyList())

        viewModelScope.launch {
            try {
                val groupBookRef = firestore.collection("groupBooks").document(docId)
                val groupBookDoc = groupBookRef.get().await()

                if (groupBookDoc.exists()) {
                    val groupBook = try {
                        groupBookDoc.toObject(GroupBook::class.java)
                    } catch (e: Exception) {
                        Log.e("GroupViewModel", "Error deserializing GroupBook during upload", e)
                        null
                    }
                    if (groupBook != null) {
                        val updatedProgression = groupBook.bookProgression.mapValues { it.value.toMutableMap() }.toMutableMap()
                        val userBooks = updatedProgression[uid] ?: mutableMapOf()
                        userBooks[bookKey] = bookToUpload
                        updatedProgression[uid] = userBooks
                        groupBookRef.update("bookProgression", updatedProgression).await()
                    } else {
                        // Fallback or handle corrupt data: overwrite with new structure
                        val newGroupBook = GroupBook(
                            groupCode = groupCode,
                            bookProgression = mapOf(uid to mapOf(bookKey to bookToUpload))
                        )
                        groupBookRef.set(newGroupBook).await()
                    }
                } else {
                    val newGroupBook = GroupBook(
                        groupCode = groupCode,
                        bookProgression = mapOf(uid to mapOf(bookKey to bookToUpload))
                    )
                    groupBookRef.set(newGroupBook).await()
                }
                onResult(true)
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Failed to leave group: $groupCode", e)
                onResult(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        groupBooksListener?.remove()
    }

    fun createGroup(groupName: String, onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val groupCode = generateUniqueCode()
                val group = GroupModel(
                    groupCode = groupCode,
                    groupName = groupName,
                    groupMembers = mapOf(uid to GroupMember(isAdmin = true, highlightColour = "0x66FFFF00"))
                )

                // 1. Add group to user's group list
                val userRef = firestore.collection("users").document(uid)
                val userDoc = userRef.get().await()
                val currentGroups = userDoc.get("groupIDs") as? List<String> ?: emptyList()
                val updatedGroups = currentGroups + groupCode
                
                userRef.update("groupIDs", updatedGroups).await()
                
                // Set as active group if none exists
                if (userDoc.getString("activeGroup") == null) {
                    userRef.update("activeGroup", groupCode).await()
                }

                // 2. Create the group document
                firestore.collection("groups").document(groupCode).set(group).await()

                fetchUserGroups()
                onResult(true, groupCode)
            } catch (e: Exception) {
                onResult(false, null)
            }
        }
    }

    fun joinGroup(groupCode: String, onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val groupDoc = firestore.collection("groups").document(groupCode).get().await()
                if (!groupDoc.exists()) {
                    onResult(false, "Group not found")
                    return@launch
                }

                val userRef = firestore.collection("users").document(uid)
                val userDoc = userRef.get().await()
                val currentGroups = userDoc.get("groupIDs") as? List<String> ?: emptyList()

                if (currentGroups.contains(groupCode)) {
                    onResult(false, "Already in this group")
                    return@launch
                }

                val updatedGroups = currentGroups + groupCode
                userRef.update("groupIDs", updatedGroups).await()

                // Update group members list
                val groupRef = firestore.collection("groups").document(groupCode)
                val group = groupDoc.toObject(GroupModel::class.java)!!
                val updatedMembers = group.groupMembers.toMutableMap()
                updatedMembers[uid] = GroupMember(isAdmin = false, highlightColour = "0x66FFFF00")
                groupRef.update("groupMembers", updatedMembers).await()

                if (userDoc.getString("activeGroup") == null) {
                    userRef.update("activeGroup", groupCode).await()
                }

                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun setActiveGroup(groupCode: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update("activeGroup", groupCode).await()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun leaveGroup(groupCode: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            Log.e("GroupViewModel", "Leave group failed: No user logged in")
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                Log.d("GroupViewModel", "Attempting to leave group: $groupCode for user: $uid")
                
                // 1. Remove from user's group list
                val userRef = firestore.collection("users").document(uid)
                val userDoc = userRef.get().await()
                val currentGroups = userDoc.get("groupIDs") as? List<String> ?: emptyList()
                val updatedGroups = currentGroups.filter { it != groupCode }
                
                Log.d("GroupViewModel", "Step 1: Updating user's groupIDs. Old: $currentGroups, New: $updatedGroups")
                userRef.update("groupIDs", updatedGroups).await()
                
                // 2. Clear active group if it's the one being left
                if (userDoc.getString("activeGroup") == groupCode) {
                    val nextActive = updatedGroups.firstOrNull()
                    Log.d("GroupViewModel", "Step 2: Clearing activeGroup. Setting to: $nextActive")
                    userRef.update("activeGroup", nextActive).await()
                } else {
                    Log.d("GroupViewModel", "Step 2: activeGroup is not the group being left. Current: ${userDoc.getString("activeGroup")}")
                }

                // 3. Remove from group's member list
                val groupRef = firestore.collection("groups").document(groupCode)
                val groupDocSnapshot = groupRef.get().await()
                if (groupDocSnapshot.exists()) {
                    val group = groupDocSnapshot.toObject(GroupModel::class.java)!!
                    val updatedMembers = group.groupMembers.toMutableMap()
                    val removed = updatedMembers.remove(uid)
                    
                    Log.d("GroupViewModel", "Step 3: Removing user from group members. User removed: $removed")
                    
                    if (updatedMembers.isEmpty()) {
                        Log.d("GroupViewModel", "Step 3: No members left, deleting group and groupBooks")
                        // Optional: Delete group if no members left
                        groupRef.delete().await()
                        // Also delete groupBooks for this group
                        firestore.collection("groupBooks").document(groupCode).delete().await()
                    } else {
                        Log.d("GroupViewModel", "Step 3: Updating group members list. Remaining: ${updatedMembers.keys}")
                        groupRef.update("groupMembers", updatedMembers).await()
                    }
                } else {
                    Log.w("GroupViewModel", "Step 3: Group document $groupCode does not exist")
                }
                Log.d("GroupViewModel", "Step 4: Fetching new list")
                fetchUserGroups()
                Log.d("GroupViewModel", "Leave group successful: $groupCode")
                onResult(true)
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Failed to leave group: $groupCode", e)
                onResult(false)
            }
        }
    }

    private suspend fun generateUniqueCode(): String {
        var code: String
        var isUnique = false
        do {
            code = UUID.randomUUID().toString().substring(0, 6).uppercase()
            val doc = firestore.collection("groups").document(code).get().await()
            if (!doc.exists()) {
                isUnique = true
            }
        } while (!isUnique)
        return code
    }
}
