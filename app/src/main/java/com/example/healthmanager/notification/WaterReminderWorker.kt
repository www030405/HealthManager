package com.example.healthmanager.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 饮水提醒 Worker
 * 每2小时提醒用户喝水
 */
class WaterReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        HealthNotificationManager.showNotification(
            context = applicationContext,
            title = "饮水提醒",
            message = "该喝水啦！建议每天摄入8杯水，保持身体水分充足有助于健康~",
            id = 301
        )
        return Result.success()
    }
}