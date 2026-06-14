package com.indeavour.coreader.viewmodel

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

    private var groupBooksListener: ListenerRegistration? = null

    init {
        fetchUserGroups()
    }

    fun fetchUserGroups() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid)
            .addSnapshotListener { userDoc, error ->
                if (error != null) return@addSnapshotListener

                val newActiveGroupId = userDoc?.getString("activeGroup")
                if (newActiveGroupId != _activeGroupId.value) {
                    _activeGroupId.value = newActiveGroupId
                    if (newActiveGroupId != null) {
                        listenToGroupBooks(newActiveGroupId)
                    } else {
                        groupBooksListener?.remove()
                        _groupBooks.value = emptyMap()
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
                if (error != null) return@addSnapshotListener
                val gb = snapshot?.toObject(GroupBook::class.java)
                val books = gb?.bookProgression?.values?.associateBy { "${it.title}_${it.author}" }
                    ?.mapValues { gb } ?: emptyMap()
                _groupBooks.value = books
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

        viewModelScope.launch {
            try {
                val groupBookRef = firestore.collection("groupBooks").document(docId)
                val groupBookDoc = groupBookRef.get().await()

                if (groupBookDoc.exists()) {
                    val groupBook = groupBookDoc.toObject(GroupBook::class.java)!!
                    val updatedProgression = groupBook.bookProgression.toMutableMap()
                    updatedProgression[uid] = book
                    groupBookRef.update("bookProgression", updatedProgression).await()
                } else {
                    val newGroupBook = GroupBook(
                        groupCode = groupCode,
                        bookProgression = mapOf(uid to book.copy(highlights = emptyList()))
                    )
                    groupBookRef.set(newGroupBook).await()
                }
                onResult(true)
            } catch (e: Exception) {
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
