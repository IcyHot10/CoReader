package com.indeavour.coreader.model.firebase

data class UserModel(
    val username : String = "",
    val email : String = "",
    val groupIDs : List<String> = emptyList(),
    val activeGroup : String? = null
)