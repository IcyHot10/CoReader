package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class GroupMember(
    var admin: Boolean = false,
    var highlightColour: String = "0x66FFFF00"
)
