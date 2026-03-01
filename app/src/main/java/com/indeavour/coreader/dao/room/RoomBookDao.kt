package com.indeavour.coreader.dao.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.indeavour.coreader.model.room.RoomBook
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomBookDao {
    @Query("SELECT * FROM book")
    fun getAll(): Flow<List<RoomBook>>

    @Query("SELECT * FROM book WHERE title LIKE :title")
    fun findByTitle(title: String): Flow<List<RoomBook>>

    @Query("SELECT * FROM book WHERE is_favourite = 1")
    fun findByFavourite(): Flow<List<RoomBook>>

    @Query("SELECT * FROM book WHERE is_active = 1 LIMIT 1")
    fun getActive(): RoomBook

    @Query("UPDATE book SET is_active = 1 WHERE id = :id")
    fun setActive(id: Int)

    @Query("UPDATE book SET is_active = 0")
    fun setInActive()

    @Insert
    suspend fun insert(book: RoomBook)

}