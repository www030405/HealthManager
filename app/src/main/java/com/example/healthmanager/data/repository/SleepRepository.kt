package com.example.healthmanager.data.repository

import com.example.healthmanager.data.dao.SleepDao
import com.example.healthmanager.data.entity.SleepRecord
import kotlinx.coroutines.flow.Flow

class SleepRepository(private val sleepDao: SleepDao) {

    suspend fun addRecord(record: SleepRecord) = sleepDao.insertRecord(record)

    suspend fun updateRecord(record: SleepRecord) = sleepDao.updateRecord(record)

    suspend fun deleteRecord(record: SleepRecord) = sleepDao.deleteRecord(record)

    fun getRecordByDate(userId: Int, date: String): Flow<SleepRecord?> =
        sleepDao.getRecordByDate(userId, date)

    fun getRecordsSince(userId: Int, startDate: String): Flow<List<SleepRecord>> =
        sleepDao.getRecordsSince(userId, startDate)

    fun getAllRecords(userId: Int): Flow<List<SleepRecord>> =
        sleepDao.getAllRecords(userId)

    fun getAverageDuration(userId: Int, startDate: String): Flow<Float?> =
        sleepDao.getAverageDuration(userId, startDate)
}
