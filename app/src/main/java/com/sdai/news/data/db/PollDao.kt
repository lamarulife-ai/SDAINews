package com.sdai.news.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface PollDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoll(poll: PollEntity): Long

    @Query("SELECT * FROM polls WHERE articleId = :articleId LIMIT 1")
    suspend fun getPollForArticle(articleId: String): PollEntity?

    @Query("SELECT * FROM polls WHERE expiresAtMillis > :nowMillis ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeActivePolls(nowMillis: Long, limit: Int = 20): Flow<List<PollEntity>>

    @Query("UPDATE polls SET votes = :votes WHERE id = :pollId")
    suspend fun updateVotes(pollId: String, votes: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: ReactionEntity): Long

    @Query("SELECT * FROM reactions WHERE articleId = :articleId")
    fun observeReactions(articleId: String): Flow<List<ReactionEntity>>

    @Query("SELECT * FROM reactions WHERE articleId = :articleId")
    suspend fun getReactions(articleId: String): List<ReactionEntity>

    @Query("UPDATE reactions SET count = count + 1, userReacted = 1 WHERE id = :reactionId")
    suspend fun incrementReaction(reactionId: String): Int

    @Query("DELETE FROM reactions WHERE articleId = :articleId")
    suspend fun clearReactions(articleId: String): Int
}
