package com.example.healthmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 饮食记录表
 * 记录每餐食物及营养素摄入
 */
@Entity(tableName = "diet_records")
data class DietRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,               // 关联用户ID
    val date: String,              // 日期，格式 yyyy-MM-dd
    val mealType: String,          // 餐次：早餐/午餐/晚餐/加餐
    val foodName: String,          // 食物名称
    val amount: Float = 0f,        // 食用量（克）
    val calories: Float = 0f,      // 卡路里（kcal）
    val carbohydrates: Float = 0f, // 碳水化合物（克）
    val protein: Float = 0f,       // 蛋白质（克）
    val fat: Float = 0f,           // 脂肪（克）
    val note: String = "",         // 备注
    val createdAt: Long = System.currentTimeMillis()
)
