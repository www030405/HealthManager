package com.example.healthmanager.data.dao

import androidx.room.*
import com.example.healthmanager.data.entity.HealthScoreHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthScoreHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: HealthScoreHistory)

    @Query("SELECT * FROM health_score_history WHERE userId = :userId AND date = :date")
    fun getScoreByDate(userId: Int, date: String): Flow<HealthScoreHistory?>

    @Query("SELECT * FROM health_score_history WHERE userId = :userId AND date >= :startDate ORDER BY date ASC")
    fun getScoresSince(userId: Int, startDate: String): Flow<List<HealthScoreHistory>>

    @Query("SELECT * FROM health_score_history WHERE userId = :userId ORDER BY date DESC")
    fun getAllScores(userId: Int): Flow<List<HealthScoreHistory>>

    @Query("DELETE FROM health_score_history WHERE userId = :userId AND date < :cutoffDate")
    suspend fun deleteOldScores(userId: Int, cutoffDate: String)

    @Query("DELETE FROM health_score_history WHERE userId = :userId")
    suspend fun deleteAll(userId: Int)
}