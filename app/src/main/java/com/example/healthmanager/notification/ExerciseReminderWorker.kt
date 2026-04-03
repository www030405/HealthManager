package com.example.healthmanager.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 运动提醒 Worker
 * 由 WorkManager 在后台定时调用
 */
class ExerciseReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        HealthNotificationManager.showNotification(
            context = applicationContext,
            title = "健康提醒",
            message = "今天还没有记录运动哦，动起来吧！目标：8000步",
            id = 101
        )
        return Result.success()
    }
}
