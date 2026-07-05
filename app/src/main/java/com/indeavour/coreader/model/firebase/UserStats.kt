package com.indeavour.coreader.model.firebase

data class UserStats(
    val secondsRead: Long = 0,
    val booksCompleted: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastReadingTimestamp: Long = 0
)
