package com.sdai.news.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ArticleEntity::class, BookmarkEntity::class, SeenEntity::class,
        SessionEntity::class, CategoryViewEntity::class,
        PollEntity::class, ReactionEntity::class,
        ScanHistoryEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class SDAIDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun seenDao(): SeenDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun pollDao(): PollDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile private var INSTANCE: SDAIDatabase? = null

        fun get(context: Context): SDAIDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SDAIDatabase::class.java,
                "sdai.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }

        /**
         * v1→v2: add quality `weight` + `tier` columns to articles.
         * Bookmarks untouched. We default existing rows to weight=0
         * and tier=NULL; they'll re-rank on the next refresh.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN weight INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN tier TEXT")
            }
        }

        /**
         * v2→v3: add `section` column for general news sections.
         * Existing AI articles get NULL (treated as AI section).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN section TEXT")
            }
        }

        /** v3→v4: add the read-state `seen` table. Articles/bookmarks untouched. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS seen " +
                        "(id TEXT NOT NULL PRIMARY KEY, seenAtMillis INTEGER NOT NULL)"
                )
            }
        }

        /** v4→v5: add `isVideo` flag to articles. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN isVideo INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5→v6: add `lang` column to articles (defaults to English). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN lang TEXT NOT NULL DEFAULT 'en'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS scan_history " +
                        "(barcode TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "brand TEXT NOT NULL, " +
                        "overallRating REAL NOT NULL, " +
                        "safetyLabel TEXT NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "scannedAtMs INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS analytics_sessions " +
                        "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "dayEpoch INTEGER NOT NULL, " +
                        "openedAtMillis INTEGER NOT NULL, " +
                        "closedAtMillis INTEGER NOT NULL DEFAULT 0, " +
                        "articlesRead INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS analytics_category_views " +
                        "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "dayEpoch INTEGER NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "viewCount INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS polls " +
                        "(id TEXT NOT NULL PRIMARY KEY, " +
                        "articleId TEXT NOT NULL, " +
                        "question TEXT NOT NULL, " +
                        "options TEXT NOT NULL, " +
                        "votes TEXT NOT NULL, " +
                        "createdAtMillis INTEGER NOT NULL, " +
                        "expiresAtMillis INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS reactions " +
                        "(id TEXT NOT NULL PRIMARY KEY, " +
                        "articleId TEXT NOT NULL, " +
                        "emoji TEXT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "count INTEGER NOT NULL DEFAULT 0, " +
                        "userReacted INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }
    }
}
