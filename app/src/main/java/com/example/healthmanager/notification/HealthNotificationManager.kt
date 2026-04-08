package com.example.healthmanager.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.healthmanager.R
import java.util.concurrent.TimeUnit

/**
 * 健康提醒通知管理器
 * 使用 WorkManager 实现定期健康提醒
 */
object HealthNotificationManager {

    private const val CHANNEL_ID = "health_reminder_channel"
    private const val CHANNEL_NAME = "健康提醒"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "健康管理每日提醒"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showNotification(context: Context, title: String, message: String, id: Int = 1) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    /**
     * 设置每日运动提醒（使用 WorkManager 周期任务）
     */
    fun scheduleExerciseReminder(context: Context) {
        val request = PeriodicWorkRequestBuilder<ExerciseReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "exercise_reminder",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelExerciseReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("exercise_reminder")
    }

    /**
     * 设置午夜步数归档任务
     * 每天 0:00 执行，将传感器步数保存到数据库，使图表能显示历史步数
     */
    fun scheduleMidnightStepArchive(context: Context) {
        val request = PeriodicWorkRequestBuilder<MidnightStepArchiveWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(calculateDelayToMidnight(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "midnight_step_archive",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * 计算距离下一个午夜 0:00 的毫秒数
     */
    private fun calculateDelayToMidnight(): Long {
        val now = java.util.Calendar.getInstance()
        val midnight = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return midnight.timeInMillis - now.timeInMillis
    }

    /**
     * 计算距离下次提醒时间（默认每天早上8点提醒）
     */
    private fun calculateInitialDelay(): Long {
        val now = java.util.Calendar.getInstance()
        val next = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 8)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            if (before(now)) add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
