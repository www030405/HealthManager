package com.example.healthmanager.healthconnect

import android.annotation.SuppressLint
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata as RecordMetadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


/**
 * Health Connect 统一健康数据管理器
 * 实现跨设备数据采集与写入（步数、睡眠、营养、运动）
 *
 * Health Connect 是 Android 的统一健康数据平台（Google Fit 已迁移至此）
 * 支持从手机传感器、第三方APP（Keep、小米运动等）聚合数据
 */
class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    companion object {
        // 申请的所有权限集合
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        )

        /**
         * 检查设备是否安装了 Health Connect
         */
        fun isAvailable(context: Context): Boolean {
            return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }
    }

    /**
     * 检查是否已获取所有所需权限
     */
    suspend fun hasAllPermissions(): Boolean {
        return client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }

    // ─────────────────────────────────────────────
    // 步数相关
    // ─────────────────────────────────────────────

    /**
     * 从 Health Connect 读取指定日期的步数（汇总来自所有数据源）
     */
    suspend fun readSteps(date: LocalDate): Long {
        val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return readStepsInRange(startTime, endTime)
    }

    suspend fun readStepsInRange(start: Instant, end: Instant): Long {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.sumOf { it.count }
    }

    /**
     * 向 Health Connect 写入步数（CNN计步结果写入）
     */
    suspend fun writeSteps(steps: Long, start: Instant, end: Instant) {
        val record = StepsRecord(
            count = steps,
            startTime = start,
            endTime = end,
            startZoneOffset = java.time.ZoneOffset.systemDefault().getRules()
                .getOffset(start),
            endZoneOffset = java.time.ZoneOffset.systemDefault().getRules()
                .getOffset(end),
            metadata = RecordMetadata()
        )
        client.insertRecords(listOf(record))
    }

    // ─────────────────────────────────────────────
    // 睡眠相关
    // ─────────────────────────────────────────────

    /**
     * 读取指定日期的睡眠记录
     */
    suspend fun readSleep(date: LocalDate): List<SleepSessionRecord> {
        val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    /**
     * 写入睡眠记录
     */
    suspend fun writeSleep(startTime: Instant, endTime: Instant, notes: String = "") {
        val record = SleepSessionRecord(
            startTime = startTime,
            endTime = endTime,
            startZoneOffset = java.time.ZoneOffset.systemDefault().getRules()
                .getOffset(startTime),
            endZoneOffset = java.time.ZoneOffset.systemDefault().getRules()
                .getOffset(endTime),
            notes = notes,
            metadata = RecordMetadata()
        )
        client.insertRecords(listOf(record))
    }

    // ─────────────────────────────────────────────
    // 营养相关
    // ─────────────────────────────────────────────

    /**
     * 读取指定日期的营养记录（饮食摄入）
     */
    suspend fun readNutrition(date: LocalDate): List<NutritionRecord> {
        val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = NutritionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    /**
     * 写入营养/饮食记录
     * @param calories 卡路里（kcal）
     * @param protein 蛋白质（克）
     * @param fat 脂肪（克）
     * @param carbs 碳水化合物（克）
     * @param mealType 餐次类型（使用 MealType 常量）
     */
    suspend fun writeNutrition(
        mealName: String,
        calories: Double,
        protein: Double,
        fat: Double,
        carbs: Double,
        mealType: Int,
        time: Instant
    ) {
        val record = NutritionRecord(
            startTime = time,
            endTime = time.plusSeconds(1),
            startZoneOffset = java.time.ZoneOffset.systemDefault().getRules().getOffset(time),
            endZoneOffset = java.time.ZoneOffset.systemDefault().getRules().getOffset(time),
            name = mealName,
            energy = androidx.health.connect.client.units.Energy.kilocalories(calories),
            protein = androidx.health.connect.client.units.Mass.grams(protein),
            totalFat = androidx.health.connect.client.units.Mass.grams(fat),
            totalCarbohydrate = androidx.health.connect.client.units.Mass.grams(carbs),
            mealType = mealType,
            metadata = RecordMetadata()
        )
        client.insertRecords(listOf(record))
    }

    // ─────────────────────────────────────────────
    // 运动记录相关
    // ─────────────────────────────────────────────

    /**
     * 写入运动会话（由CNN识别步态后调用）
     * @param exerciseType 使用 ExerciseSessionRecord.EXERCISE_TYPE_* 常量
     */
    @SuppressLint("RestrictedApi")
    suspend fun writeExerciseSession(
        startTime: Instant,
        endTime: Instant,
        exerciseType: Int,
        title: String = ""
    ) {
        val record = ExerciseSessionRecord(
            startTime = startTime,
            endTime = endTime,
            startZoneOffset = java.time.ZoneOffset.systemDefault().getRules().getOffset(startTime),
            endZoneOffset = java.time.ZoneOffset.systemDefault().getRules().getOffset(endTime),
            exerciseType = exerciseType,
            title = title,
            metadata = RecordMetadata()
        )
        client.insertRecords(listOf(record))
    }

    /**
     * 读取近N天的运动记录
     */
    suspend fun readExerciseSessions(days: Int = 7): List<ExerciseSessionRecord> {
        val endTime = Instant.now()
        val startTime = endTime.minusSeconds(days * 24L * 3600)
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }
}
