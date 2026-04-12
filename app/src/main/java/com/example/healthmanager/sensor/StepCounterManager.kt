package com.example.healthmanager.sensor

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.data.repository.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 计步传感器管理器
 * 使用 TYPE_STEP_COUNTER（累计步数传感器）
 *
 * 跨天归档逻辑：
 * 当检测到日期变更时，将前一天的步数直接写入 Room 数据库的 exercise_records 表，
 * 这样健康报告图表就能可靠地显示历史每日步数。
 */
class StepCounterManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var initialSteps = -1L

    private val prefs: SharedPreferences =
        context.getSharedPreferences("step_counter_prefs", Context.MODE_PRIVATE)
    private val KEY_INITIAL_STEPS = "initial_steps"
    private val KEY_LAST_TOTAL_STEPS = "last_total_steps"
    private val KEY_LAST_DATE = "last_date"
    private val KEY_CURRENT_STEPS = "current_steps"

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps

    val isAvailable: Boolean get() = stepSensor != null

    private var lastUpdateStepCount = 0

    private val archiveScope = CoroutineScope(Dispatchers.IO)

    fun start() {
        restoreInitialSteps()

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            Log.d("StepCounter", "传感器已启动：${it.name}")
        } ?: run {
            Log.e("StepCounter", "设备不支持计步传感器")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        saveCurrentState()
        Log.d("StepCounter", "传感器已停止")
    }

    /**
     * 从 SharedPreferences 恢复基准值
     * 检测到跨天时，将前一天步数归档到数据库
     */
    private fun restoreInitialSteps() {
        val savedInitialSteps = prefs.getLong(KEY_INITIAL_STEPS, -1L)
        val savedCurrentSteps = prefs.getInt(KEY_CURRENT_STEPS, 0)
        val lastDate = prefs.getString(KEY_LAST_DATE, null)

        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (savedInitialSteps >= 0 && lastDate == todayStr) {
            // 同一天，恢复之前的状态
            initialSteps = savedInitialSteps
            _steps.value = savedCurrentSteps
            lastUpdateStepCount = savedCurrentSteps
            Log.d("StepCounter", "恢复基准值：$initialSteps, 当前步数：${_steps.value}")
        } else if (savedInitialSteps >= 0 && savedCurrentSteps > 0 && lastDate != null) {
            // 跨天：将前一天步数归档到数据库
            archiveYesterdaySteps(lastDate, savedCurrentSteps)

            // 重置为新一天
            initialSteps = -1L
            _steps.value = 0
            lastUpdateStepCount = 0
            prefs.edit().clear().apply()
            Log.d("StepCounter", "新的一天($todayStr)，重置计步器，已归档 $lastDate: $savedCurrentSteps 步")
        } else {
            // 首次使用或无有效数据
            initialSteps = -1L
            _steps.value = 0
            lastUpdateStepCount = 0
            prefs.edit().clear().apply()
            Log.d("StepCounter", "首次启动或无数据，重置计步器")
        }
    }

    /**
     * 将前一天的步数归档到数据库
     * 防止重复归档：检查数据库中是否已有该日期的"传感器自动记录"
     */
    private fun archiveYesterdaySteps(date: String, steps: Int) {
        archiveScope.launch {
            try {
                val userPrefs = UserPreferences(context)
                val userId = userPrefs.currentUserId
                if (userId <= 0) {
                    Log.w("StepCounter", "无登录用户，跳过归档")
                    return@launch
                }

                val db = HealthDatabase.getDatabase(context)
                val existingRecords = db.exerciseDao().getRecordsByDateOnce(userId, date)
                if (existingRecords.any { it.note == "传感器自动记录" }) {
                    Log.d("StepCounter", "$date 已有归档记录，跳过")
                    return@launch
                }

                // 获取用户体重用于卡路里估算
                val user = db.userDao().getUserByIdOnce(userId)
                val weight = user?.weight?.takeIf { it > 0f } ?: 70f
                val calories = steps * 0.7f / 1000f * weight * 1.036f

                val record = ExerciseRecord(
                    userId = userId,
                    date = date,
                    steps = steps,
                    caloriesBurned = calories,
                    exerciseType = "步行",
                    durationMinutes = 0,
                    distanceKm = steps * 0.7f / 1000f,
                    note = "传感器自动记录"
                )
                db.exerciseDao().insertRecord(record)
                Log.d("StepCounter", "归档成功：$date, $steps 步, ${String.format("%.1f", calories)} kcal")
            } catch (e: Exception) {
                Log.e("StepCounter", "归档失败: ${e.message}")
            }
        }
    }

    private fun saveCurrentState() {
        if (initialSteps >= 0) {
            prefs.edit().apply {
                putLong(KEY_INITIAL_STEPS, initialSteps)
                putLong(KEY_LAST_TOTAL_STEPS, initialSteps + _steps.value)
                putString(KEY_LAST_DATE, LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                putInt(KEY_CURRENT_STEPS, _steps.value)
            }.apply()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toLong()

            if (initialSteps < 0) {
                initialSteps = totalSteps
                Log.d("StepCounter", "设置初始基准值：$initialSteps")
                return
            }

            val currentSteps = (totalSteps - initialSteps).toInt()

            if (currentSteps != lastUpdateStepCount && currentSteps >= 0) {
                lastUpdateStepCount = currentSteps
                _steps.value = currentSteps
                saveCurrentState()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun resetSteps() {
        initialSteps = -1L
        _steps.value = 0
        lastUpdateStepCount = 0
        saveCurrentState()
        Log.d("StepCounter", "步数已重置")
    }
}
