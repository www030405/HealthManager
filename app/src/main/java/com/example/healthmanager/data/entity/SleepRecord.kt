package com.example.healthmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 睡眠记录表
 * 记录每天的睡眠时间及质量评估
 */
@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,               // 关联用户ID
    val date: String,              // 日期（入睡日期），格式 yyyy-MM-dd
    val bedTime: String,           // 入睡时间，格式 HH:mm
    val wakeTime: String,          // 起床时间，格式 HH:mm
    val durationHours: Float = 0f, // 睡眠时长（小时，自动计算）
    val quality: Int = 3,          // 睡眠质量：1-5（1最差，5最好）
    val deepSleepHours: Float = 0f,// 深睡时长（小时，估算）
    val note: String = "",         // 备注（如：多梦、失眠等）
    val createdAt: Long = System.currentTimeMillis()
)
