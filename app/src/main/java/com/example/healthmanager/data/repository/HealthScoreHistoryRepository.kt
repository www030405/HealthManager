package com.example.healthmanager.data.repository

import com.example.healthmanager.data.dao.HealthScoreHistoryDao
import com.example.healthmanager.data.entity.HealthScoreHistory
import kotlinx.coroutines.flow.Flow

class HealthScoreHistoryRepository(private val dao: HealthScoreHistoryDao) {

    suspend fun saveScore(score: HealthScoreHistory) = dao.insert(score)

    fun getScoreByDate(userId: Int, date: String): Flow<HealthScoreHistory?> =
        dao.getScoreByDate(userId, date)

    fun getScoresSince(userId: Int, startDate: String): Flow<List<HealthScoreHistory>> =
        dao.getScoresSince(userId, startDate)

    fun getAllScores(userId: Int): Flow<List<HealthScoreHistory>> =
        dao.getAllScores(userId)

    suspend fun deleteOldScores(userId: Int, cutoffDate: String) =
        dao.deleteOldScores(userId, cutoffDate)

    suspend fun deleteAll(userId: Int) = dao.deleteAll(userId)
}