package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    init {
        fetchUserGroups()
    }

    fun fetchUserGroups() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid)
            .addSnapshotListener { userDoc, error ->
                if (error != null) return@addSnapshotListener

                _activeGroupId.value = userDoc?.getString("activeGroup")
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

    fun createGroup(groupName: String, onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val groupCode = generateUniqueCode()
                val group = GroupModel(
                    groupCode = groupCode,
                    groupName = groupName
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
