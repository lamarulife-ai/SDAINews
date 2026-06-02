package com.sdai.news.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ArticleEntity::class, BookmarkEntity::class],
    // v2: added `weight` and `tier` columns on articles for per-source
    // quality scoring + tier-filter chips. Destructive migration is
    // acceptable — the articles table is regenerated every refresh and
    // capped at 24 h; bookmarks survive via the OnConflictStrategy on
    // their own table since their schema hasn't changed.
    version = 2,
    exportSchema = false,
)
abstract class SDAIDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile private var INSTANCE: SDAIDatabase? = null

        fun get(context: Context): SDAIDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SDAIDatabase::class.java,
                "sdai.db",
            )
                // Explicit migration for the v1→v2 articles-table column
                // bump so existing bookmarks survive the upgrade. Any
                // future schema break that we don't explicitly migrate
                // falls back to a destructive rebuild — acceptable
                // because the articles table is recomputed on every
                // refresh and capped at 24h anyway.
                .addMigrations(MIGRATION_1_2)
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
    }
}
