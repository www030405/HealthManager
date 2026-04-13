package com.example.healthmanager.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 睡眠提醒 Worker
 * 每天 23:30 提醒用户就寝
 */
class SleepReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        HealthNotificationManager.showNotification(
            context = applicationContext,
            title = "睡眠提醒",
            message = "时间不早了，建议您准备休息，保持良好的作息有助于健康~",
            id = 201
        )
        return Result.success()
    }
}