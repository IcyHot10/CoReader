package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserModel(
    val username : String = "",
    val email : String = "",
    val groupIDs : List<String> = emptyList(),
    val activeGroup : String? = null,
    val lastHighlightColor: Int = 0x66FFFF00,
    val books : Map<String, BookModel> = emptyMap()
)
