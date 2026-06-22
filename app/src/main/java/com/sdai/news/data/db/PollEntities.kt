package com.sdai.news.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "polls")
data class PollEntity(
    @PrimaryKey val id: String,
    val articleId: String,
    val question: String,
    val options: String,
    val votes: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
)

@Entity(tableName = "reactions")
data class ReactionEntity(
    @PrimaryKey val id: String,
    val articleId: String,
    val emoji: String,
    val label: String,
    val count: Int = 0,
    val userReacted: Boolean = false,
)
