package com.example.healthmanager.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.repository.UserPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 饮食提醒 Worker
 * 每天 11:40 和 18:00 检查用户是否已记录对应餐次
 */
class MealReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "MealReminder"
    }

    override suspend fun doWork(): Result {
        val prefs = UserPreferences(applicationContext)
        val userId = prefs.currentUserId
        if (userId <= 0) return Result.success()

        val currentHour = LocalTime.now().hour
        val currentMinute = LocalTime.now().minute

        // 判断当前时间段
        val mealType = when {
            currentHour == 11 && currentMinute >= 40 -> "午餐"
            currentHour == 18 -> "晚餐"
            else -> return Result.success()
        }

        val today = java.time.LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val db = HealthDatabase.getDatabase(applicationContext)
        
        val records = db.dietDao().getRecordsByDate(userId, today).first()
        val alreadyRecorded = records.any { it.mealType == mealType }

        if (!alreadyRecorded) {
            val message = when (mealType) {
                "午餐" -> "午餐时间到了，记得记录您的午餐摄入哦~"
                "晚餐" -> "晚餐时间到了，记得记录您的晚餐摄入，保持均衡饮食！"
                else -> "到${mealType}时间了，记得记录饮食摄入！"
            }
            HealthNotificationManager.showNotification(
                context = applicationContext,
                title = "饮食记录提醒",
                message = message,
                id = 202
            )
            Log.d(TAG, "提醒: $mealType 未记录")
        } else {
            Log.d(TAG, "$mealType 已记录，跳过提醒")
        }

        return Result.success()
    }
}