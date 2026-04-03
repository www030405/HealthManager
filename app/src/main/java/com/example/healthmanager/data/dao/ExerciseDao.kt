package com.example.healthmanager.data.dao

import androidx.room.*
import com.example.healthmanager.data.entity.ExerciseRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ExerciseRecord): Long

    @Update
    suspend fun updateRecord(record: ExerciseRecord)

    @Delete
    suspend fun deleteRecord(record: ExerciseRecord)

    // 查询某用户某天的运动记录
    @Query("SELECT * FROM exercise_records WHERE userId = :userId AND date = :date ORDER BY createdAt DESC")
    fun getRecordsByDate(userId: Int, date: String): Flow<List<ExerciseRecord>>

    // 查询最近N天的记录（用于趋势图）
    @Query("SELECT * FROM exercise_records WHERE userId = :userId AND date >= :startDate ORDER BY date ASC")
    fun getRecordsSince(userId: Int, startDate: String): Flow<List<ExerciseRecord>>

    // 查询某天总步数
    @Query("SELECT COALESCE(SUM(steps), 0) FROM exercise_records WHERE userId = :userId AND date = :date")
    fun getTotalStepsByDate(userId: Int, date: String): Flow<Int>

    // 查询某天总消耗卡路里
    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM exercise_records WHERE userId = :userId AND date = :date")
    fun getTotalCaloriesByDate(userId: Int, date: String): Flow<Float>

    // 查询所有记录（分页用）
    @Query("SELECT * FROM exercise_records WHERE userId = :userId ORDER BY date DESC, createdAt DESC")
    fun getAllRecords(userId: Int): Flow<List<ExerciseRecord>>
}
