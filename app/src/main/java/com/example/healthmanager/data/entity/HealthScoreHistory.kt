package com.example.healthmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 健康评分历史记录表
 * 存储每天的健康评分数据
 */
@Entity(tableName = "health_score_history")
data class HealthScoreHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val date: String,            // 日期 yyyy-MM-dd
    val totalScore: Float,      // 综合评分
    val exerciseScore: Float,   // 运动评分
    val sleepScore: Float,      // 睡眠评分
    val dietScore: Float,       // 饮食评分
    val createdAt: Long = System.currentTimeMillis()
)