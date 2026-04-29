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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val KEY_DAILY_BASELINE = "daily_baseline"

    /**
     * 当日传感器增量（自 app 启动以来累计的步数）。
     * 与 dailyBaseline 相加得到对外暴露的"今日总步数"。
     */
    private var sensorDelta = 0

    /**
     * 由外部（HealthConnectViewModel 经 ExerciseViewModel/HealthScoreViewModel）注入的
     * "今日开始时已有步数"，从 Health Connect 单次读取得到。
     * HC 不可用 / 未授权 / 未读取时为 0，等价于纯本地传感器模式。
     */
    private var dailyBaseline = 0L

    /**
     * 跨天复位回调（兜底 ① / ② / ③ 任一触发都会调）。
     * 上层用它来：1) 让 HC 当天的"已读取"标记失效；2) 触发新一天的 HC 基准重新拉取。
     */
    var onDailyReset: ((newDate: String) -> Unit)? = null

    private val _steps = MutableStateFlow(0)
    /** 对外暴露的"今日总步数" = dailyBaseline + sensorDelta */
    val steps: StateFlow<Int> = _steps

    val isAvailable: Boolean get() = stepSensor != null

    private var lastUpdateStepCount = 0

    private val archiveScope = CoroutineScope(Dispatchers.IO)

    /**
     * 当前会话所属日期。
     * 用于在保存 prefs 时绑定数据真实归属的日期，
     * 避免零点后未及时复位时把"今日日期+昨日步数"写入 prefs。
     */
    private var currentSessionDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** 兜底③：定时检查跨天 */
    private var dateCheckJob: Job? = null

    // ── 久坐检测 ──
    companion object {
        const val SEDENTARY_THRESHOLD_MS = 30 * 60 * 1000L  // 30分钟无步数变化视为久坐
    }

    /** 上次步数变化的时间戳 */
    private var lastStepChangeTime = System.currentTimeMillis()

    /** 久坐状态：true 表示已超过阈值未检测到步数变化 */
    private val _isSedentary = MutableStateFlow(false)
    val isSedentary: StateFlow<Boolean> = _isSedentary

    /** 久坐提醒已被用户dismiss后，在下次走动前不再弹出 */
    private var sedentaryDismissed = false

    /** 用户关闭了久坐提醒弹窗 */
    fun dismissSedentary() {
        sedentaryDismissed = true
        _isSedentary.value = false
    }

    private var sedentaryCheckJob: Job? = null

    fun start() {
        restoreInitialSteps()
        lastStepChangeTime = System.currentTimeMillis()

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            Log.d("StepCounter", "传感器已启动：${it.name}")
        } ?: run {
            Log.e("StepCounter", "设备不支持计步传感器")
        }

        // 启动久坐检测定时检查（每分钟检查一次）
        sedentaryCheckJob?.cancel()
        sedentaryCheckJob = archiveScope.launch {
            while (true) {
                delay(60_000) // 每60秒检查一次
                val elapsed = System.currentTimeMillis() - lastStepChangeTime
                if (elapsed >= SEDENTARY_THRESHOLD_MS && !sedentaryDismissed) {
                    _isSedentary.value = true
                    Log.d("StepCounter", "久坐提醒：已 ${elapsed / 60000} 分钟未活动")
                }
            }
        }

        // 兜底③：每分钟检查一次系统日期，过了零点立即归档+清零
        dateCheckJob?.cancel()
        dateCheckJob = archiveScope.launch {
            while (true) {
                delay(60_000)
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                if (today != currentSessionDate) {
                    Log.d("StepCounter", "定时检测到跨天：$currentSessionDate -> $today")
                    performDailyReset(today)
                }
            }
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        sedentaryCheckJob?.cancel()
        dateCheckJob?.cancel()
        saveCurrentState()
        Log.d("StepCounter", "传感器已停止")
    }

    /**
     * 执行跨天复位：归档昨日步数、清空内存与 prefs、刷新会话日期。
     * 三条兜底路径（start / onSensorChanged / 定时）均收敛到这里，避免逻辑分叉。
     * 归档的是"今日总步数"（dailyBaseline + sensorDelta），保证图表与 UI 一致。
     */
    private fun performDailyReset(newDate: String) {
        val previousDate = currentSessionDate
        val previousTotal = _steps.value  // 已经是组合值
        if (previousTotal > 0) {
            archiveYesterdaySteps(previousDate, previousTotal)
        }
        initialSteps = -1L
        sensorDelta = 0
        dailyBaseline = 0L
        _steps.value = 0
        lastUpdateStepCount = 0
        prefs.edit().clear().apply()
        currentSessionDate = newDate
        Log.d("StepCounter", "已复位到 $newDate，归档 $previousDate: $previousTotal 步")
        // 通知上层：清空 HC 基准缓存并触发新一天的重新读取
        onDailyReset?.invoke(newDate)
    }

    /**
     * 由上层（ExerciseViewModel/HealthScoreViewModel）在收到 HC 当日步数后调用。
     * 设置后立刻刷新 _steps，让 UI 平滑跳到 baseline + delta。
     */
    fun setDailyBaseline(baseline: Long) {
        if (baseline < 0L) return
        dailyBaseline = baseline
        _steps.value = (baseline + sensorDelta).toInt()
        saveCurrentState()
        Log.d("StepCounter", "已注入 HC 基准：$baseline，当前显示步数：${_steps.value}")
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
            // 同一天，恢复之前的状态（包括 HC 基准）
            initialSteps = savedInitialSteps
            dailyBaseline = prefs.getLong(KEY_DAILY_BASELINE, 0L)
            sensorDelta = savedCurrentSteps  // 持久化的是 sensor delta（不含 baseline）
            _steps.value = (dailyBaseline + sensorDelta).toInt()
            lastUpdateStepCount = sensorDelta
            currentSessionDate = todayStr
            Log.d("StepCounter", "恢复：sensor delta=$sensorDelta, baseline=$dailyBaseline, 显示=${_steps.value}")
        } else if (lastDate != null && lastDate != todayStr) {
            // 兜底①：启动时检测到跨天
            // 归档的是昨天保存的"总步数"（lastDate 当天的 baseline + delta）
            val lastBaseline = prefs.getLong(KEY_DAILY_BASELINE, 0L)
            val lastTotal = (lastBaseline + savedCurrentSteps).toInt()
            if (lastTotal > 0) {
                archiveYesterdaySteps(lastDate, lastTotal)
            }
            initialSteps = -1L
            sensorDelta = 0
            dailyBaseline = 0L
            _steps.value = 0
            lastUpdateStepCount = 0
            prefs.edit().clear().apply()
            currentSessionDate = todayStr
            Log.d("StepCounter", "新的一天($todayStr)，已归档 $lastDate: $lastTotal 步")
            onDailyReset?.invoke(todayStr)
        } else {
            // 首次使用或无有效数据
            initialSteps = -1L
            sensorDelta = 0
            dailyBaseline = 0L
            _steps.value = 0
            lastUpdateStepCount = 0
            prefs.edit().clear().apply()
            currentSessionDate = todayStr
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
                // KEY_LAST_TOTAL_STEPS = sensor 视角的累计读数，仅用于 worker 计算 sensor delta
                putLong(KEY_LAST_TOTAL_STEPS, initialSteps + sensorDelta)
                // 用会话日期而非系统日期，避免零点后未及时复位时把昨日步数挂到今日日期下。
                putString(KEY_LAST_DATE, currentSessionDate)
                // 持久化 sensor delta（不含 baseline），便于 worker 单独取出 sensor 部分
                putInt(KEY_CURRENT_STEPS, sensorDelta)
                putLong(KEY_DAILY_BASELINE, dailyBaseline)
            }.apply()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toLong()
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 兜底②：传感器事件触发时检测跨天
            if (todayStr != currentSessionDate) {
                Log.d("StepCounter", "传感器事件检测到跨天：$currentSessionDate -> $todayStr")
                performDailyReset(todayStr)
            }

            if (initialSteps < 0) {
                initialSteps = totalSteps
                Log.d("StepCounter", "设置初始基准值：$initialSteps")
                return
            }

            // 兜底④：检测设备重启导致的 TYPE_STEP_COUNTER 计数清零
            // 同一天内 totalSteps 不可能小于 initialSteps，唯一可能就是 sensor 被重启归零。
            // 保留已累计的 sensorDelta，将 initialSteps 平移对齐到当前读数，后续累加正常。
            if (totalSteps < initialSteps) {
                Log.w(
                    "StepCounter",
                    "检测到传感器计数回退（设备重启？）：totalSteps=$totalSteps < " +
                        "initialSteps=$initialSteps，保留 sensorDelta=$sensorDelta 并重新对齐基准"
                )
                initialSteps = totalSteps - sensorDelta
                saveCurrentState()
                return
            }

            val currentDelta = (totalSteps - initialSteps).toInt()

            if (currentDelta != lastUpdateStepCount && currentDelta >= 0) {
                lastUpdateStepCount = currentDelta
                sensorDelta = currentDelta
                _steps.value = (dailyBaseline + currentDelta).toInt()
                saveCurrentState()

                // 步数变化 → 重置久坐状态
                lastStepChangeTime = System.currentTimeMillis()
                _isSedentary.value = false
                sedentaryDismissed = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun resetSteps() {
        initialSteps = -1L
        sensorDelta = 0
        dailyBaseline = 0L
        _steps.value = 0
        lastUpdateStepCount = 0
        saveCurrentState()
        Log.d("StepCounter", "步数已重置")
    }
}
