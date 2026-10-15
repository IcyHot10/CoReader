package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class GroupModel(
    val groupCode: String = "",
    val groupName: String = "",
    val groupMembers: Map<String, GroupMember> = emptyMap()
)
