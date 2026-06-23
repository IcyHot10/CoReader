package com.indeavour.coreader.dao.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.indeavour.coreader.model.room.UserPreferences
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun getPreferences(): Flow<UserPreferences?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(preferences: UserPreferences)

    @Query("UPDATE user_preferences SET font_size = :fontSize WHERE id = 1")
    suspend fun updateFontSize(fontSize: Float)
}
