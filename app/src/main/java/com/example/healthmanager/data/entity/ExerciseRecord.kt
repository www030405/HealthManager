package com.example.healthmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 运动记录表
 * 记录每次运动的步数、卡路里等信息
 */
@Entity(tableName = "exercise_records")
data class ExerciseRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,               // 关联用户ID
    val date: String,              // 日期，格式 yyyy-MM-dd
    val steps: Int = 0,            // 步数（传感器采集或手动输入）
    val caloriesBurned: Float = 0f,// 消耗卡路里（kcal）
    val exerciseType: String = "", // 运动类型：跑步/步行/骑行/游泳/其他
    val durationMinutes: Int = 0,  // 运动时长（分钟）
    val distanceKm: Float = 0f,    // 距离（公里）
    val note: String = "",         // 备注
    val createdAt: Long = System.currentTimeMillis()
)
