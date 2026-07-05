package com.indeavour.coreader.model.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "font_size") val fontSize: Float = 100f,
    @ColumnInfo(name = "theme") val theme: String = "system",
    @ColumnInfo(name = "library_filter") val libraryFilter: String = "All",
    @ColumnInfo(name = "group_filter") val groupFilter: String = "All"
)
