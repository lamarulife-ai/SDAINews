package com.sdai.news.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analytics_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayEpoch: Long,
    val openedAtMillis: Long,
    val closedAtMillis: Long = 0,
    val articlesRead: Int = 0,
)

@Entity(tableName = "analytics_category_views")
data class CategoryViewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayEpoch: Long,
    val category: String,
    val viewCount: Int = 1,
)
