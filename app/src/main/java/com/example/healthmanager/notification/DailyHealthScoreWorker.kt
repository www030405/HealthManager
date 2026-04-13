package com.example.healthmanager.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.HealthScoreHistory
import com.example.healthmanager.data.repository.HealthScoreHistoryRepository
import com.example.healthmanager.data.repository.UserPreferences
import com.example.healthmanager.health.HealthScoreEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 每日健康评分存储 Worker
 * 每天凌晨自动计算并保存当天的健康评分，
 * 同时清理超过30天的历史数据
 */
class DailyHealthScoreWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "DailyHealthScore"
        const val WORK_NAME = "daily_health_score"
    }

    override suspend fun doWork(): Result {
        val prefs = UserPreferences(applicationContext)
        val userId = prefs.currentUserId
        if (userId <= 0) return Result.success()

        val db = HealthDatabase.getDatabase(applicationContext)
        val repo = HealthScoreHistoryRepository(db.healthScoreHistoryDao())
        val userDao = db.userDao()
        val exerciseDao = db.exerciseDao()
        val sleepDao = db.sleepDao()
        val dietDao = db.dietDao()

        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val todayStr = today.format(fmt)

        try {
            // 获取用户目标
            var targetSteps = 8000
            var targetCalories = 2000
            userDao.getUserById(userId).collect { user ->
                targetSteps = user?.targetSteps ?: 8000
                targetCalories = user?.targetCalories ?: 2000
            }

            // 获取本周运动数据（用于计算weekActiveDays）
            val sevenDaysAgo = today.minusDays(6).format(fmt)
            val exerciseRecords = mutableListOf<com.example.healthmanager.data.entity.ExerciseRecord>()
            exerciseDao.getRecordsSince(userId, sevenDaysAgo).collect { exerciseRecords.addAll(it) }
            
            val todaySteps = exerciseRecords.filter { it.date == todayStr }.sumOf { it.steps }
            val todayMinutes = exerciseRecords.filter { it.date == todayStr }.sumOf { it.durationMinutes }
            val weekActiveDays = exerciseRecords.groupBy { it.date }.keys.size

            // 获取今日睡眠数据
            var todaySleep: com.example.healthmanager.data.entity.SleepRecord? = null
            sleepDao.getRecordByDate(userId, todayStr).collect { todaySleep = it }

            // 获取今日饮食数据
            val dietRecords = mutableListOf<com.example.healthmanager.data.entity.DietRecord>()
            dietDao.getRecordsByDate(userId, todayStr).collect { dietRecords.addAll(it) }
            val totalCalories = dietRecords.sumOf { it.calories.toDouble() }.toFloat()
            val mealCount = dietRecords.map { it.mealType }.distinct().count { it in listOf("早餐", "午餐", "晚餐") }

            // 计算评分
            val exerciseScore = HealthScoreEngine.calculateExerciseScore(
                todaySteps = todaySteps,
                targetSteps = targetSteps,
                todayExerciseMinutes = todayMinutes,
                weekActiveDays = weekActiveDays
            )
            val sleepScore = HealthScoreEngine.calculateSleepScore(
                durationHours = todaySleep?.durationHours ?: 0f,
                quality = todaySleep?.quality ?: 0,
                hasRecord = todaySleep != null
            )
            val dietScore = HealthScoreEngine.calculateDietScore(
                totalCalories = totalCalories,
                targetCalories = targetCalories,
                mealCount = mealCount,
                hasRecord = dietRecords.isNotEmpty()
            )
            val totalScore = HealthScoreEngine.calculateTotalScore(exerciseScore, sleepScore, dietScore)

            // 保存评分
            val scoreRecord = HealthScoreHistory(
                userId = userId,
                date = todayStr,
                totalScore = totalScore,
                exerciseScore = exerciseScore,
                sleepScore = sleepScore,
                dietScore = dietScore
            )
            repo.saveScore(scoreRecord)
            Log.d(TAG, "已保存今日健康评分: $totalScore")

            // 清理30天前的数据
            val cutoffDate = today.minusDays(30).format(fmt)
            repo.deleteOldScores(userId, cutoffDate)
            Log.d(TAG, "已清理30天前的历史数据")

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "保存健康评分失败: ${e.message}")
            return Result.retry()
        }
    }
}