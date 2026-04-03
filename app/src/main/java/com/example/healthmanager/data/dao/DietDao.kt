package com.example.healthmanager.data.dao

import androidx.room.*
import com.example.healthmanager.data.entity.DietRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DietDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DietRecord): Long

    @Update
    suspend fun updateRecord(record: DietRecord)

    @Delete
    suspend fun deleteRecord(record: DietRecord)

    // 查询某用户某天所有饮食记录
    @Query("SELECT * FROM diet_records WHERE userId = :userId AND date = :date ORDER BY mealType, createdAt")
    fun getRecordsByDate(userId: Int, date: String): Flow<List<DietRecord>>

    // 查询某天某餐次的记录
    @Query("SELECT * FROM diet_records WHERE userId = :userId AND date = :date AND mealType = :mealType")
    fun getRecordsByMeal(userId: Int, date: String, mealType: String): Flow<List<DietRecord>>

    // 查询某天总卡路里摄入
    @Query("SELECT COALESCE(SUM(calories), 0) FROM diet_records WHERE userId = :userId AND date = :date")
    fun getTotalCaloriesByDate(userId: Int, date: String): Flow<Float>

    // 查询最近N天卡路里趋势（用于报告）
    @Query("SELECT date, SUM(calories) as totalCal FROM diet_records WHERE userId = :userId AND date >= :startDate GROUP BY date ORDER BY date ASC")
    fun getDailyCaloriesSince(userId: Int, startDate: String): Flow<List<DailyCalories>>

    // 查询所有记录
    @Query("SELECT * FROM diet_records WHERE userId = :userId ORDER BY date DESC, createdAt DESC")
    fun getAllRecords(userId: Int): Flow<List<DietRecord>>
}

// 用于聚合查询结果的数据类
data class DailyCalories(
    val date: String,
    val totalCal: Float
)
