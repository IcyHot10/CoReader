package com.indeavour.coreader.model.firebase

data class UserStatsDocument(
    val userId: String = "",
    val yearlyStats: Map<String, UserStats> = emptyMap(),
    val lifetimeStats: UserStats = UserStats()
)
