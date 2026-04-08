package com.example.healthmanager.notification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.data.repository.UserPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 午夜步数归档 Worker
 * 在每天 0:00 执行，将前一天的传感器步数和卡路里保存到数据库
 * 这样传感器自动计步的数据就能在健康报告图表中显示
 */
class MidnightStepArchiveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MidnightArchive"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        // 获取当前登录用户
        val userPrefs = UserPreferences(context)
        val userId = userPrefs.currentUserId
        if (userId <= 0) {
            Log.w(TAG, "没有登录用户，跳过归档")
            return Result.success()
        }

        // 从 StepCounterManager 的 SharedPreferences 读取传感器步数
        val stepPrefs: SharedPreferences =
            context.getSharedPreferences("step_counter_prefs", Context.MODE_PRIVATE)
        val initialSteps = stepPrefs.getLong("initial_steps", -1L)
        val lastTotalSteps = stepPrefs.getLong("last_total_steps", -1L)

        if (initialSteps < 0 || lastTotalSteps < 0) {
            Log.w(TAG, "没有传感器步数数据，跳过归档")
            return Result.success()
        }

        val sensorSteps = (lastTotalSteps - initialSteps).toInt()
        if (sensorSteps <= 0) {
            Log.w(TAG, "传感器步数为0，跳过归档")
            return Result.success()
        }

        // 归档日期 = 昨天（因为 Worker 在午夜执行，归档的是前一天的数据）
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 获取用户体重用于卡路里估算
        val db = HealthDatabase.getDatabase(context)
        val user = db.userDao().getUserByIdOnce(userId)
        val weight = user?.weight?.takeIf { it > 0f } ?: 70f

        // 卡路里估算：steps × 0.7m / 1000 × weight(kg) × 1.036
        val calories = sensorSteps * 0.7f / 1000f * weight * 1.036f

        // 检查是否已经有当天的传感器归档记录（避免重复）
        val existingRecords = db.exerciseDao().getRecordsByDateOnce(userId, yesterday)
        val alreadyArchived = existingRecords.any { it.note == "传感器自动记录" }
        if (alreadyArchived) {
            Log.w(TAG, "昨天($yesterday)已有归档记录，跳过")
            return Result.success()
        }

        // 保存到数据库
        val record = ExerciseRecord(
            userId = userId,
            date = yesterday,
            steps = sensorSteps,
            caloriesBurned = calories,
            exerciseType = "步行",
            durationMinutes = 0,
            distanceKm = sensorSteps * 0.7f / 1000f,
            note = "传感器自动记录"
        )
        db.exerciseDao().insertRecord(record)
        Log.d(TAG, "归档成功：$yesterday, $sensorSteps 步, ${String.format("%.1f", calories)} kcal")

        // 清除传感器的 SharedPreferences，为新一天做准备
        stepPrefs.edit().clear().apply()
        Log.d(TAG, "已清除传感器基准值，准备新一天计步")

        return Result.success()
    }
}
