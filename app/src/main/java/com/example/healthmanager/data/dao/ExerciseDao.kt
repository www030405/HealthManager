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

    @Query("SELECT * FROM exercise_records WHERE userId = :userId AND date = :date ORDER BY createdAt DESC")
    fun getRecordsByDate(userId: Int, date: String): Flow<List<ExerciseRecord>>

    @Query("SELECT * FROM exercise_records WHERE userId = :userId AND date = :date AND exerciseType = :type ORDER BY createdAt DESC")
    fun getRecordsByDateAndType(userId: Int, date: String, type: String): Flow<List<ExerciseRecord>>

    @Query("SELECT * FROM exercise_records WHERE userId = :userId AND date >= :startDate ORDER BY date ASC")
    fun getRecordsSince(userId: Int, startDate: String): Flow<List<ExerciseRecord>>

    @Query("SELECT COALESCE(SUM(steps), 0) FROM exercise_records WHERE userId = :userId AND date = :date")
    fun getTotalStepsByDate(userId: Int, date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(steps), 0) FROM exercise_records WHERE userId = :userId AND date = :date AND exerciseType = :type")
    fun getTotalStepsByDateAndType(userId: Int, date: String, type: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM exercise_records WHERE userId = :userId AND date = :date")
    fun getTotalCaloriesByDate(userId: Int, date: String): Flow<Float>

    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM exercise_records WHERE userId = :userId AND date = :date AND exerciseType = :type")
    fun getTotalCaloriesByDateAndType(userId: Int, date: String, type: String): Flow<Float>

    @Query("SELECT * FROM exercise_records WHERE userId = :userId AND date = :date ORDER BY createdAt DESC")
    suspend fun getRecordsByDateOnce(userId: Int, date: String): List<ExerciseRecord>

    @Query("SELECT * FROM exercise_records WHERE userId = :userId ORDER BY date DESC, createdAt DESC")
    fun getAllRecords(userId: Int): Flow<List<ExerciseRecord>>
}
