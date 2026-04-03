package com.example.healthmanager.data.repository

import com.example.healthmanager.data.dao.ExerciseDao
import com.example.healthmanager.data.entity.ExerciseRecord
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val exerciseDao: ExerciseDao) {

    suspend fun addRecord(record: ExerciseRecord) = exerciseDao.insertRecord(record)

    suspend fun updateRecord(record: ExerciseRecord) = exerciseDao.updateRecord(record)

    suspend fun deleteRecord(record: ExerciseRecord) = exerciseDao.deleteRecord(record)

    fun getRecordsByDate(userId: Int, date: String): Flow<List<ExerciseRecord>> =
        exerciseDao.getRecordsByDate(userId, date)

    fun getRecordsSince(userId: Int, startDate: String): Flow<List<ExerciseRecord>> =
        exerciseDao.getRecordsSince(userId, startDate)

    fun getTotalStepsByDate(userId: Int, date: String): Flow<Int> =
        exerciseDao.getTotalStepsByDate(userId, date)

    fun getTotalCaloriesByDate(userId: Int, date: String): Flow<Float> =
        exerciseDao.getTotalCaloriesByDate(userId, date)

    fun getAllRecords(userId: Int): Flow<List<ExerciseRecord>> =
        exerciseDao.getAllRecords(userId)
}
