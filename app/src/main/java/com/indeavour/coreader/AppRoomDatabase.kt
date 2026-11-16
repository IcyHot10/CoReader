package com.indeavour.coreader

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.auth.FirebaseAuth
import com.indeavour.coreader.dao.room.ReadingActivityDao
import com.indeavour.coreader.dao.room.RoomBookDao
import com.indeavour.coreader.dao.room.UserPreferencesDao
import com.indeavour.coreader.model.room.ReadingActivity
import com.indeavour.coreader.model.room.RoomBook
import com.indeavour.coreader.domain.AccountStorage
import com.indeavour.coreader.model.room.UserPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Database(entities = [RoomBook::class, UserPreferences::class, ReadingActivity::class], version = 11, exportSchema = false)
abstract class AppRoomDatabase: RoomDatabase() {
    abstract fun bookDao(): RoomBookDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun readingActivityDao(): ReadingActivityDao

    companion object {
        private const val TAG = "AppRoomDatabase"

        /** Open databases by file name, one per account that has been signed in this run. */
        private val instances = ConcurrentHashMap<String, AppRoomDatabase>()

        @Volatile private var signedOutInstance: AppRoomDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `book` ADD COLUMN `is_deleted` INTEGER NOT NULL DEFAULT 0")
            }
        }

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

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_preferences` ADD COLUMN `library_filter` TEXT NOT NULL DEFAULT 'All'")
                db.execSQL("ALTER TABLE `user_preferences` ADD COLUMN `group_filter` TEXT NOT NULL DEFAULT 'All'")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `book` ADD COLUMN `progression_updated_timestamp` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * The library of whoever is signed in. Each account has its own database file, so one
         * person's books, progress and reading history are never shown to the next.
         *
         * Resolved on every call rather than once: the same process outlives a sign-out, and a
         * database held from before it belongs to the previous account.
         */
        fun getDatabase(context: Context): AppRoomDatabase {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: return signedOutDatabase(context)
            val name = AccountStorage.databaseName(uid)
            return instances[name] ?: synchronized(this) {
                instances[name] ?: run {
                    claimLegacyDatabase(context.applicationContext, name)
                    Room.databaseBuilder(context.applicationContext, AppRoomDatabase::class.java, name)
                        .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build().also { instances[name] = it }
                }
            }
        }

        /**
         * Nobody is signed in, so there is no library to show. Anything still running from before
         * a sign-out lands in this empty, in-memory database instead of in someone's account.
         */
        private fun signedOutDatabase(context: Context): AppRoomDatabase =
            signedOutInstance ?: synchronized(this) {
                signedOutInstance ?: Room
                    .inMemoryDatabaseBuilder(context.applicationContext, AppRoomDatabase::class.java)
                    .build().also { signedOutInstance = it }
            }

        /**
         * Hands the database from before accounts were separated to the first account that opens
         * its library after the update, by renaming the file. That database has no record of
         * whose it was; on a device only one person used, this is its owner.
         *
         * The main file is moved last, so an interrupted move is picked up again next time: until
         * it has moved, the old database still counts as unclaimed.
         */
        private fun claimLegacyDatabase(context: Context, name: String) {
            val legacy = context.getDatabasePath(AccountStorage.LEGACY_DATABASE)
            val target = context.getDatabasePath(name)
            if (!legacy.exists() || target.exists()) return
            for (suffix in listOf("-wal", "-shm", "-journal", "")) {
                val from = File(legacy.path + suffix)
                if (from.exists() && !from.renameTo(File(target.path + suffix))) {
                    Log.e(TAG, "Could not move ${from.name} to the signed-in account")
                    // The -shm file is rebuilt when missing. Without any of the others the
                    // database would be incomplete, so the main file stays where it is.
                    if (suffix != "-shm") return
                }
            }
        }

    }
}
