package com.example.healthmanager.sensor

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 计步传感器管理器
 * 使用 TYPE_STEP_COUNTER（累计步数传感器）
 * 注意：此传感器返回的是自设备重启后的总步数，需要记录初始值来计算当天步数
 */
class StepCounterManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // 设备重启后传感器的初始值（第一次读到时记录）
    private var initialSteps = -1L
    
    // 使用 SharedPreferences 持久化存储基准值
    private val prefs: SharedPreferences = context.getSharedPreferences("step_counter_prefs", Context.MODE_PRIVATE)
    private val KEY_INITIAL_STEPS = "initial_steps"
    private val KEY_LAST_TOTAL_STEPS = "last_total_steps"
    private val KEY_LAST_DATE = "last_date"

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps

    val isAvailable: Boolean get() = stepSensor != null
    
    // 用于防抖，避免过于频繁的更新
    private var lastUpdateStepCount = 0
    
    // 记录上次检查的日期，用于检测跨天
    private var lastCheckedDate: String? = null

    fun start() {
        // 从持久化存储恢复基准值（如果有）
        restoreInitialSteps()
        
        stepSensor?.let {
            // 使用最快的采样率
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            Log.d("StepCounter", "传感器已启动：${it.name} (FASTEST)")
        } ?: run {
            Log.e("StepCounter", "设备不支持计步传感器")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        // 保存当前状态
        saveCurrentState()
        Log.d("StepCounter", "传感器已停止")
    }
    
    /**
     * 从 SharedPreferences 恢复之前的基准值
     * 以 0 点为界限判断是否同一天
     */
    private fun restoreInitialSteps() {
        val savedInitialSteps = prefs.getLong(KEY_INITIAL_STEPS, -1L)
        val lastTotalSteps = prefs.getLong(KEY_LAST_TOTAL_STEPS, -1L)
        val lastDate = prefs.getString(KEY_LAST_DATE, null)

        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val isSameDay = lastDate == todayStr

        if (savedInitialSteps >= 0 && lastTotalSteps >= 0 && isSameDay) {
            // 同一天，恢复之前的基准值和相对步数
            initialSteps = savedInitialSteps
            _steps.value = (lastTotalSteps - savedInitialSteps).toInt()
            lastUpdateStepCount = _steps.value
            Log.d("StepCounter", "恢复之前的基准值：$initialSteps, 当前步数：${_steps.value}")
        } else {
            // 新的一天或首次使用，清除旧数据，重新开始计步
            prefs.edit().clear().apply()
            initialSteps = -1L
            _steps.value = 0
            lastUpdateStepCount = 0
            Log.d("StepCounter", "新的一天($todayStr)，重置计步器")
        }
    }
    
    /**
     * 保存当前状态到 SharedPreferences
     */
    private fun saveCurrentState() {
        if (initialSteps >= 0) {
            prefs.edit().apply {
                putLong(KEY_INITIAL_STEPS, initialSteps)
                putLong(KEY_LAST_TOTAL_STEPS, initialSteps + _steps.value)
                putString(KEY_LAST_DATE, LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
            }.apply()
            Log.d("StepCounter", "保存状态：基准值=$initialSteps, 相对步数=${_steps.value}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toLong()
            
            // 第一次收到数据时记录基准值
            if (initialSteps < 0) {
                initialSteps = totalSteps
                Log.d("StepCounter", "设置初始基准值：$initialSteps")
                return
            }
            
            val currentSteps = (totalSteps - initialSteps).toInt()
            
            // 只有当步数真正变化时才更新
            if (currentSteps != lastUpdateStepCount && currentSteps >= 0) {
                lastUpdateStepCount = currentSteps
                _steps.value = currentSteps
                Log.d("StepCounter", "步数更新：$currentSteps 步 (总：$totalSteps)")
                
                // 实时更新持久化存储
                saveCurrentState()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    /**
     * 重置步数（用户手动清零时调用）
     */
    fun resetSteps() {
        if (initialSteps >= 0) {
            // 重新设置基准值为当前的总步数
            initialSteps = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?.let { sensor ->
                    // 读取当前最新的总步数
                    var currentTotal = 0L
                    sensorManager.registerListener(object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                                currentTotal = event.values[0].toLong()
                                sensorManager.unregisterListener(this)
                            }
                        }
                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
                    currentTotal
                } ?: initialSteps
            
            _steps.value = 0
            lastUpdateStepCount = 0
            saveCurrentState()
            Log.d("StepCounter", "步数已重置，新基准值：$initialSteps")
        }
    }
}
