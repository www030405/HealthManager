package com.example.healthmanager.data.repository

import com.example.healthmanager.data.dao.DietDao
import com.example.healthmanager.data.dao.DailyCalories
import com.example.healthmanager.data.entity.DietRecord
import kotlinx.coroutines.flow.Flow

class DietRepository(private val dietDao: DietDao) {

    suspend fun addRecord(record: DietRecord) = dietDao.insertRecord(record)

    suspend fun updateRecord(record: DietRecord) = dietDao.updateRecord(record)

    suspend fun deleteRecord(record: DietRecord) = dietDao.deleteRecord(record)

    fun getRecordsByDate(userId: Int, date: String): Flow<List<DietRecord>> =
        dietDao.getRecordsByDate(userId, date)

    fun getTotalCaloriesByDate(userId: Int, date: String): Flow<Float> =
        dietDao.getTotalCaloriesByDate(userId, date)

    fun getDailyCaloriesSince(userId: Int, startDate: String): Flow<List<DailyCalories>> =
        dietDao.getDailyCaloriesSince(userId, startDate)

    fun getAllRecords(userId: Int): Flow<List<DietRecord>> =
        dietDao.getAllRecords(userId)
}
