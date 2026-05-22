package com.sdai.news.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ArticleEntity::class, BookmarkEntity::class],
    version = 1,
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
                // On schema mismatch wipe the whole DB and rebuild —
                // acceptable here because the only persisted user data
                // is bookmarks, which we'd migrate explicitly if we
                // ever cared to preserve across breaking schema changes.
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
