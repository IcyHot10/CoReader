package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * The owner's private document. The display name lives in [ProfileModel] instead, so other group
 * members never need read access to this document.
 */
@IgnoreExtraProperties
data class UserModel(
    val email: String = "",
    val groupIDs: List<String> = emptyList(),
    val activeGroup: String? = null,
    val lastHighlightColor: Int = AnnotationModel.DEFAULT_COLOR,
    val books: Map<String, BookModel> = emptyMap()
)
