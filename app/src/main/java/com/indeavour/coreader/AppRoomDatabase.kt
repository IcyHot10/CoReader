package com.indeavour.coreader

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.indeavour.coreader.dao.room.RoomBookDao
import com.indeavour.coreader.dao.room.UserPreferencesDao
import com.indeavour.coreader.model.room.RoomBook
import com.indeavour.coreader.model.room.UserPreferences

@Database(entities = [RoomBook::class, UserPreferences::class], version = 6, exportSchema = false)
abstract class AppRoomDatabase: RoomDatabase() {
    abstract fun bookDao(): RoomBookDao
    abstract fun userPreferencesDao(): UserPreferencesDao

    companion object {
        @Volatile private var INSTANCE: AppRoomDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_preferences` (`id` INTEGER NOT NULL, `font_size` REAL NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun getDatabase(context: Context): AppRoomDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext,
                    AppRoomDatabase::class.java, "library_db")
                    .addMigrations(MIGRATION_5_6)
                    .build().also {INSTANCE = it}
            }

    }
}