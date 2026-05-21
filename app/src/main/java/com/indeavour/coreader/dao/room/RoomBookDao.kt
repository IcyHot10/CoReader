package com.indeavour.coreader.dao.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.indeavour.coreader.model.room.RoomBook
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomBookDao {
    @Query("SELECT * FROM book WHERE is_deleted = 0")
    fun getAll(): Flow<List<RoomBook>>

    @Query("SELECT * FROM book WHERE title LIKE :title AND is_deleted = 0")
    fun findByTitle(title: String): Flow<List<RoomBook>>

    @Query("SELECT * FROM book WHERE is_favourite = 1 AND is_deleted = 0")
    fun findByFavourite(): Flow<List<RoomBook>>

    @Query("SELECT * FROM book WHERE is_active = 1 AND is_deleted = 0 LIMIT 1")
    suspend fun getActive(): RoomBook?

    @Query("SELECT * FROM book WHERE is_active = 1 AND is_deleted = 0 LIMIT 1")
    fun getActiveFlow(): Flow<RoomBook?>

    @Query("UPDATE book SET is_active = 1 WHERE id = :id")
    suspend fun setActive(id: Int)

    @Query("UPDATE book SET is_active = 0")
    suspend fun setInActive()

    @Query("UPDATE book SET progression = :progression WHERE is_active = 1")
    suspend fun updateActiveBookProgression(progression: String)

    @Insert
    suspend fun insert(book: RoomBook)

    @Query("SELECT * FROM book WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): RoomBook?

    @Query("UPDATE book SET is_deleted = 1, file_path = '' WHERE id = :id")
    suspend fun markAsDeleted(id: Int)
}