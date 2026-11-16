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

    @Query("UPDATE book SET progression = :progression, progression_updated_timestamp = :timestamp WHERE title = :title AND author = :author")
    suspend fun updateProgressionByTitleAndAuthor(title: String, author: String, progression: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE book SET progression = :progression, progression_updated_timestamp = :timestamp WHERE id = :id")
    suspend fun updateProgressionById(id: Int, progression: String, timestamp: Long)

    @Query("SELECT * FROM book WHERE title = :title AND author = :author LIMIT 1")
    suspend fun findByTitleAndAuthor(title: String, author: String): RoomBook?

    /** Brings a deleted book back with the newly imported file and cover. */
    @Query("UPDATE book SET is_deleted = 0, file_path = :filePath, uri = :uri, cover_path = :cover WHERE id = :id")
    suspend fun restoreBook(id: Int, filePath: String, uri: String, cover: String?)

    @Insert
    suspend fun insert(book: RoomBook)

    @Query("SELECT * FROM book WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): RoomBook?

    /** The row is kept, so a later re-import finds it; its file and cover are forgotten. */
    @Query("UPDATE book SET is_deleted = 1, file_path = '', cover_path = NULL WHERE id = :id")
    suspend fun markAsDeleted(id: Int)

    /** How many books in the library are stored in the file at [filePath]. */
    @Query("SELECT COUNT(*) FROM book WHERE file_path = :filePath AND is_deleted = 0")
    suspend fun countUsingFile(filePath: String): Int

    /** How many books in the library show the cover at [coverPath]. */
    @Query("SELECT COUNT(*) FROM book WHERE cover_path = :coverPath AND is_deleted = 0")
    suspend fun countUsingCover(coverPath: String): Int

    @Query("UPDATE book SET is_favourite = :isFavourite WHERE id = :id")
    suspend fun setFavourite(id: Int, isFavourite: Boolean)

    @Query("UPDATE book SET completed_timestamp = :timestamp WHERE id = :id AND completed_timestamp = 0")
    suspend fun setCompletedTimestamp(id: Int, timestamp: Long)
}