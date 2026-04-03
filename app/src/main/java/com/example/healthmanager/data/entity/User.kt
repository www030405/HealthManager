package com.example.healthmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户信息表
 * 存储用户基本资料，用于个性化健康建议计算
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val username: String,          // 用户名（登录账号）
    val password: String,          // 密码（实际项目应加密存储）
    val nickname: String = "",     // 昵称（显示用）
    val gender: String = "未设置", // 性别：男/女/未设置
    val age: Int = 0,              // 年龄
    val height: Float = 0f,        // 身高（cm）
    val weight: Float = 0f,        // 体重（kg）
    val targetSteps: Int = 8000,   // 每日目标步数
    val targetCalories: Int = 2000,// 每日目标卡路里摄入
    val createdAt: Long = System.currentTimeMillis()
)
