package com.example.healthmanager.data.dao

import androidx.room.*
import com.example.healthmanager.data.entity.SleepRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SleepRecord): Long

    @Update
    suspend fun updateRecord(record: SleepRecord)

    @Delete
    suspend fun deleteRecord(record: SleepRecord)

    // 查询某用户某天的睡眠记录
    @Query("SELECT * FROM sleep_records WHERE userId = :userId AND date = :date ORDER BY createdAt DESC LIMIT 1")
    fun getRecordByDate(userId: Int, date: String): Flow<SleepRecord?>

    // 查询最近N天睡眠记录（用于趋势分析）
    @Query("SELECT * FROM sleep_records WHERE userId = :userId AND date >= :startDate ORDER BY date ASC")
    fun getRecordsSince(userId: Int, startDate: String): Flow<List<SleepRecord>>

    // 查询所有记录
    @Query("SELECT * FROM sleep_records WHERE userId = :userId ORDER BY date DESC")
    fun getAllRecords(userId: Int): Flow<List<SleepRecord>>

    // 查询平均睡眠时长（最近7天）
    @Query("SELECT AVG(durationHours) FROM sleep_records WHERE userId = :userId AND date >= :startDate")
    fun getAverageDuration(userId: Int, startDate: String): Flow<Float?>
}
