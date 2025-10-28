package com.indeavour.coreader

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.indeavour.coreader.dao.room.RoomBookDao
import com.indeavour.coreader.model.room.RoomBook

@Database(entities = [RoomBook::class], version = 1, exportSchema = false)
abstract class AppRoomDatabase: RoomDatabase() {
    abstract fun bookDao(): RoomBookDao

    companion object {
        @Volatile private var INSTANCE: AppRoomDatabase? = null

        fun getDatabase(context: Context): AppRoomDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext,
                    AppRoomDatabase::class.java, "library_db")
                    .build().also {INSTANCE = it}
            }

    }
}