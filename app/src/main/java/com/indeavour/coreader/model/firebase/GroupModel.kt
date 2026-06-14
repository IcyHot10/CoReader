package com.indeavour.coreader.model.firebase

data class GroupModel(
    val groupCode: String = "",
    val groupName: String = "",
    val groupMembers: Map<String, GroupMember> = emptyMap()
)