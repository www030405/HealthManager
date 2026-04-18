package com.example.healthmanager.ml

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 步态分类器（规则基础方法）
 *
 * 使用加速度计与陀螺仪数据，通过统计特征识别步态类型：
 * - 0: 站立 (Stand)
 * - 1: 步行 (Walking)
 * - 2: 跑步 (Running)
 * - 3: 上楼梯 (Stair-up)
 *
 * 分类依据：
 * - 站立：加速度方差小（几乎不动）
 * - 步行：加速度均值中等，Z轴正负峰值对称
 * - 上楼梯：加速度均值中等，Z轴正向峰值明显多于负向（向上发力）
 * - 跑步：加速度均值大
 *
 * 采样：6通道（acc + gyro），128帧滑动窗口（约2.56秒 @50Hz）
 */
class GaitClassifier(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "GaitClassifier"
        const val WINDOW_SIZE = 128       // 滑动窗口大小
        const val FEATURE_SIZE = 6        // 6通道: acc(3) + gyro(3)
        const val NUM_CLASSES = 4         // 4类: Stand, Walk, Run, Stair-up

        // 4类标签
        val LABELS = arrayOf("静止", "步行", "跑步", "上楼梯")

        val EXERCISE_TYPES = intArrayOf(
            -1,  // 站立，不写入运动记录
            56,  // 步行
            57,  // 跑步
            28   // 上楼梯
        )

        // 低通滤波器系数（用于从加速度计分离重力）
        private const val GRAVITY_ALPHA = 0.8f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // 滑动窗口数据缓冲区 — 每帧 6 个特征
    private val dataBuffer = ArrayDeque<FloatArray>()
    private val gyroBuffer = ArrayDeque<FloatArray>()
    
    // 重力估计值（低通滤波器持续更新）
    private val gravity = FloatArray(3)

    // 实时步态分类结果
    private val _gaitResult = MutableStateFlow<GaitResult?>(null)
    val gaitResult: StateFlow<GaitResult?> = _gaitResult

    // 实时步数计数（通过加速度计峰值检测）
    private val _realtimeSteps = MutableStateFlow(0)
    val realtimeSteps: StateFlow<Int> = _realtimeSteps

    private var lastMagnitude = 0f
    private var stepThreshold = 12f
    private var lastStepTime = 0L

    // 分类频率控制
    private var lastClassifyTime = 0L
    private val classifyIntervalMs = 500L

    // 标记加速度计数据是否已就绪
    private var accFrameCount = 0

    fun startListening() {
        accSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val accX = event.values[0]
                val accY = event.values[1]
                val accZ = event.values[2]
                
                // 低通滤波分离重力
                gravity[0] = GRAVITY_ALPHA * gravity[0] + (1 - GRAVITY_ALPHA) * accX
                gravity[1] = GRAVITY_ALPHA * gravity[1] + (1 - GRAVITY_ALPHA) * accY
                gravity[2] = GRAVITY_ALPHA * gravity[2] + (1 - GRAVITY_ALPHA) * accZ
                
                // body_acc = total_acc - gravity（去除重力后的线性加速度）
                val bodyAccX = accX - gravity[0]
                val bodyAccY = accY - gravity[1]
                val bodyAccZ = accZ - gravity[2]

                accFrameCount++

                // 构建 6 通道特征帧：body_acc(3) + gyro(3)
                val frame = floatArrayOf(
                    bodyAccX, bodyAccY, bodyAccZ,
                    0f, 0f, 0f
                )

                if (gyroBuffer.isNotEmpty()) {
                    val latestGyro = gyroBuffer.last()
                    frame[3] = latestGyro[0]
                    frame[4] = latestGyro[1]
                    frame[5] = latestGyro[2]
                }

                dataBuffer.addLast(frame)
                if (dataBuffer.size > WINDOW_SIZE) dataBuffer.removeFirst()
                
                // 峰值检测计步
                val rawX = event.values[0]; val rawY = event.values[1]; val rawZ = event.values[2]
                val magnitude = Math.sqrt(
                    (rawX * rawX + rawY * rawY + rawZ * rawZ).toDouble()
                ).toFloat()
                val now = System.currentTimeMillis()
                if (magnitude > stepThreshold && lastMagnitude <= stepThreshold
                    && (now - lastStepTime) > 300) {
                    _realtimeSteps.value = _realtimeSteps.value + 1
                    lastStepTime = now
                }
                lastMagnitude = magnitude
            }
            Sensor.TYPE_GYROSCOPE -> {
                val data = floatArrayOf(event.values[0], event.values[1], event.values[2])
                gyroBuffer.addLast(data)
                if (gyroBuffer.size > WINDOW_SIZE) gyroBuffer.removeFirst()
            }
        }

        // 窗口满时执行规则分类
        val now = System.currentTimeMillis()
        if (dataBuffer.size == WINDOW_SIZE && accFrameCount > 50
            && (now - lastClassifyTime) >= classifyIntervalMs) {
            lastClassifyTime = now
            classify()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun classify() {
        val classIndex = ruleBasedClassification()
        _gaitResult.value = GaitResult(
            label = LABELS[classIndex],
            classIndex = classIndex,
            confidence = 0.85f,
            exerciseType = EXERCISE_TYPES[classIndex]
        )
    }

    /**
     * 规则基础分类器
     *
     * 区分逻辑：
     * - 站立：variance < 5f（几乎不动）
     * - 步行：mean < 15f，Z轴正负峰值对称
     * - 上楼梯：mean < 15f，Z轴正向峰值明显多于负向（向上发力）
     * - 跑步：mean > 20f
     */
    private fun ruleBasedClassification(): Int {
        if (dataBuffer.size < WINDOW_SIZE) return 0

        val magnitudes = dataBuffer.map { frame ->
            Math.sqrt((frame[0] * frame[0] + frame[1] * frame[1] + frame[2] * frame[2]).toDouble()).toFloat()
        }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()

        // Z轴峰值分析：上楼梯时有明显的向上发力
        val zValues = dataBuffer.map { it[2] }
        val zPositivePeaks = zValues.count { it > 2.5f }
        val zNegativePeaks = zValues.count { it < -2.5f }

        Log.d(TAG, "规则分类: mean=${String.format("%.1f", mean)}, variance=${String.format("%.1f", variance)}, z+=$zPositivePeaks, z-=$zNegativePeaks")

        // 0=站立, 1=步行, 2=跑步, 3=上楼梯
        val classIndex = when {
            variance < 5f -> 0
            mean > 20f -> 2
            mean < 15f -> {
                if (zPositivePeaks - zNegativePeaks > 15) 3 else 1
            }
            else -> 1
        }

        Log.d(TAG, "步态结果: ${LABELS[classIndex]} (classIndex=$classIndex)")

        return classIndex
    }

    fun resetSteps() {
        _realtimeSteps.value = 0
    }

    fun release() {
        stopListening()
    }
}

data class GaitResult(
    val label: String,
    val classIndex: Int,
    val confidence: Float,
    val exerciseType: Int
)
