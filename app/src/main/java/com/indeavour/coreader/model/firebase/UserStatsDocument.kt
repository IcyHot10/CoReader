package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

/** Contents of `userStats/{uid}`; the uid is the document id and is not repeated inside. */
@IgnoreExtraProperties
data class UserStatsDocument(
    val yearlyStats: Map<String, UserStats> = emptyMap(),
    val lifetimeStats: UserStats = UserStats()
)
