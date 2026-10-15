package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Publicly readable display name, kept out of [UserModel] so that fetching another member's
 * name does not require read access to their email address or library.
 */
@IgnoreExtraProperties
data class ProfileModel(
    val username: String = ""
)
