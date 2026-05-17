package com.indeavour.coreader.model.firebase

data class UserModel(
    val id: String = "",
    val username : String = "",
    val email : String = "",
    val groupIDs : List<Int> = emptyList()
)