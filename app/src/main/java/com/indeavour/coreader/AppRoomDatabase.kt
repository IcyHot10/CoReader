package com.indeavour.coreader

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.indeavour.coreader.dao.room.ReadingActivityDao
import com.indeavour.coreader.dao.room.RoomBookDao
import com.indeavour.coreader.dao.room.UserPreferencesDao
import com.indeavour.coreader.model.room.ReadingActivity
import com.indeavour.coreader.model.room.RoomBook
import com.indeavour.coreader.model.room.UserPreferences

@Database(entities = [RoomBook::class, UserPreferences::class, ReadingActivity::class], version = 9, exportSchema = false)
abstract class AppRoomDatabase: RoomDatabase() {
    abstract fun bookDao(): RoomBookDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun readingActivityDao(): ReadingActivityDao

    companion object {
        @Volatile private var INSTANCE: AppRoomDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_preferences` (`id` INTEGER NOT NULL, `font_size` REAL NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `reading_activity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `book_id` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `duration_seconds` INTEGER NOT NULL, `is_completed_event` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `book` ADD COLUMN `added_timestamp` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `book` ADD COLUMN `completed_timestamp` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_preferences` ADD COLUMN `theme` TEXT NOT NULL DEFAULT 'system'")
            }
        }

        fun getDatabase(context: Context): AppRoomDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext,
                    AppRoomDatabase::class.java, "library_db")
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build().also {INSTANCE = it}
            }

    }
}