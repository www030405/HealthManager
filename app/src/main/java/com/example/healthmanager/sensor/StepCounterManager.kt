package com.example.healthmanager.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps

    val isAvailable: Boolean get() = stepSensor != null

    fun start() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toLong()
            // 第一次收到数据时记录基准值
            if (initialSteps < 0) {
                initialSteps = totalSteps
            }
            _steps.value = (totalSteps - initialSteps).toInt()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
