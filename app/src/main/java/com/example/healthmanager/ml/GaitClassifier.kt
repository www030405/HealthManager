package com.example.healthmanager.ml

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 1D CNN 步态分类器
 *
 * 使用 TensorFlow Lite 在手机端实时推理，识别步态类型：
 * - 0: 步行 (Walking)
 * - 1: 跑步 (Running)
 * - 2: 上楼 (Walking Upstairs)
 * - 3: 下楼 (Walking Downstairs)
 * - 4: 静止 (Sitting/Standing)
 *
 * 基于 UCI HAR 数据集预训练，输入 9 通道：
 * total_acc(3) + body_acc(3) + body_gyro(3)
 * 128 帧滑动窗口
 * 模型文件：assets/gait_classifier.tflite
 */
class GaitClassifier(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "GaitClassifier"
        const val WINDOW_SIZE = 128       // 滑动窗口大小（约1.28秒 @100Hz）
        const val FEATURE_SIZE = 9        // UCI HAR 9通道: total_acc(3) + body_acc(3) + body_gyro(3)
        const val NUM_CLASSES = 6         // UCI HAR 标准6类

        // UCI HAR 6类标签（原始顺序：WALKING, WALKING_UPSTAIRS, WALKING_DOWNSTAIRS, SITTING, STANDING, LAYING）
        val LABELS = arrayOf("步行", "上楼", "下楼", "坐下", "站立", "躺下")

        val EXERCISE_TYPES = intArrayOf(
            56,  // EXERCISE_TYPE_WALKING
            28,  // EXERCISE_TYPE_STAIR_CLIMBING（上楼）
            28,  // EXERCISE_TYPE_STAIR_CLIMBING（下楼）
            -1,  // 坐下，不写入运动记录
            -1,  // 站立，不写入运动记录
            -1   // 躺下，不写入运动记录
        )

        private const val MODEL_FILE = "gait_classifier.tflite"

        // 低通滤波器系数（用于从加速度计分离重力）
        private const val GRAVITY_ALPHA = 0.8f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // 滑动窗口数据缓冲区 — 每帧 9 个特征
    // [total_acc_x, total_acc_y, total_acc_z, body_acc_x, body_acc_y, body_acc_z, gyro_x, gyro_y, gyro_z]
    private val dataBuffer = ArrayDeque<FloatArray>()
    private val gyroBuffer = ArrayDeque<FloatArray>()

    // 重力估计值（低通滤波器持续更新）
    private val gravity = FloatArray(3)

    // TFLite 解释器
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    // 是否使用 CNN 模型（暴露给 UI 显示）
    private val _usingCnn = MutableStateFlow(false)
    val usingCnn: StateFlow<Boolean> = _usingCnn

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

    // 标记加速度计数据是否已就绪（等待重力滤波稳定）
    private var accFrameCount = 0

    fun loadModel(): Boolean {
        return try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "TFLite 模型加载成功: $MODEL_FILE")

            isModelLoaded = true
            _usingCnn.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败，将使用规则分类器: ${e.message}")
            isModelLoaded = false
            false
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

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
                val totalAcc = floatArrayOf(event.values[0], event.values[1], event.values[2])

                // 低通滤波分离重力
                gravity[0] = GRAVITY_ALPHA * gravity[0] + (1 - GRAVITY_ALPHA) * totalAcc[0]
                gravity[1] = GRAVITY_ALPHA * gravity[1] + (1 - GRAVITY_ALPHA) * totalAcc[1]
                gravity[2] = GRAVITY_ALPHA * gravity[2] + (1 - GRAVITY_ALPHA) * totalAcc[2]

                // body_acc = total_acc - gravity
                val bodyAcc = floatArrayOf(
                    totalAcc[0] - gravity[0],
                    totalAcc[1] - gravity[1],
                    totalAcc[2] - gravity[2]
                )

                accFrameCount++

                // 构建 9 通道特征帧（陀螺仪暂时占位 0，等 gyro 事件更新）
                val frame = floatArrayOf(
                    totalAcc[0], totalAcc[1], totalAcc[2],  // total_acc
                    bodyAcc[0], bodyAcc[1], bodyAcc[2],     // body_acc
                    0f, 0f, 0f                               // body_gyro（下面由 gyro 更新）
                )

                // 用最新的陀螺仪数据填充
                if (gyroBuffer.isNotEmpty()) {
                    val latestGyro = gyroBuffer.last()
                    frame[6] = latestGyro[0]
                    frame[7] = latestGyro[1]
                    frame[8] = latestGyro[2]
                }

                dataBuffer.addLast(frame)
                if (dataBuffer.size > WINDOW_SIZE) dataBuffer.removeFirst()

                // 峰值检测计步
                val magnitude = Math.sqrt(
                    (totalAcc[0] * totalAcc[0] + totalAcc[1] * totalAcc[1] + totalAcc[2] * totalAcc[2]).toDouble()
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

        // 窗口满且重力滤波已稳定（至少50帧）时执行分类
        val now = System.currentTimeMillis()
        if (dataBuffer.size == WINDOW_SIZE && accFrameCount > 50
            && (now - lastClassifyTime) >= classifyIntervalMs) {
            lastClassifyTime = now
            classify()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun classify() {
        if (isModelLoaded) {
            runCnnInference()
        } else {
            val classIndex = ruleBasedClassification()
            _gaitResult.value = GaitResult(
                label = LABELS[classIndex],
                classIndex = classIndex,
                confidence = 0.85f,
                exerciseType = EXERCISE_TYPES[classIndex]
            )
        }
    }

    /**
     * TFLite 1D CNN 推理
     * 输入形状：[1, 128, 9] — 128帧 × 9通道
     * 输出形状：[1, 5] — 5类概率
     */
    private fun runCnnInference() {
        val interp = interpreter ?: return

        try {
            // 构建输入 ByteBuffer：[1, 128, 9] float32 = 4608 bytes
            val inputBuffer = ByteBuffer.allocateDirect(4 * WINDOW_SIZE * FEATURE_SIZE)
            inputBuffer.order(ByteOrder.nativeOrder())

            val frames = dataBuffer.toList()

            for (i in 0 until WINDOW_SIZE) {
                val frame = frames[i]
                for (j in 0 until FEATURE_SIZE) {
                    inputBuffer.putFloat(frame[j])
                }
            }
            inputBuffer.rewind()

            // 输出数组：[1, NUM_CLASSES]
            val output = Array(1) { FloatArray(NUM_CLASSES) }

            interp.run(inputBuffer, output)

            val probabilities = output[0]
            var maxIndex = 0
            var maxProb = probabilities[0]
            for (i in 1 until NUM_CLASSES) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i]
                    maxIndex = i
                }
            }

            Log.d(TAG, "CNN推理: ${LABELS[maxIndex]}(${String.format("%.1f%%", maxProb * 100)}) " +
                    "[${probabilities.joinToString { String.format("%.2f", it) }}]")

            _gaitResult.value = GaitResult(
                label = LABELS[maxIndex],
                classIndex = maxIndex,
                confidence = maxProb,
                exerciseType = EXERCISE_TYPES[maxIndex]
            )

        } catch (e: Exception) {
            Log.e(TAG, "CNN推理异常: ${e.message}，回退到规则分类器")
            val classIndex = ruleBasedClassification()
            _gaitResult.value = GaitResult(
                label = LABELS[classIndex],
                classIndex = classIndex,
                confidence = 0.85f,
                exerciseType = EXERCISE_TYPES[classIndex]
            )
        }
    }

    /**
     * 规则基础备用分类器
     */
    private fun ruleBasedClassification(): Int {
        if (dataBuffer.size < WINDOW_SIZE) return 3 // 坐下(静止)

        val magnitudes = dataBuffer.map { frame ->
            Math.sqrt((frame[0] * frame[0] + frame[1] * frame[1] + frame[2] * frame[2]).toDouble()).toFloat()
        }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()

        // 索引对应: 0=步行, 1=上楼, 2=下楼, 3=坐下, 4=站立, 5=躺下
        return when {
            variance < 0.5f -> 3  // 坐下(静止)
            mean < 10f && variance < 2f -> 0  // 步行
            mean > 13f || variance > 5f -> 0  // 快步走（规则分类器无法区分跑步，归为步行）
            else -> 0  // 默认步行
        }
    }

    fun resetSteps() {
        _realtimeSteps.value = 0
    }

    fun release() {
        stopListening()
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.e(TAG, "释放Interpreter异常: ${e.message}")
        }
        isModelLoaded = false
    }
}

data class GaitResult(
    val label: String,
    val classIndex: Int,
    val confidence: Float,
    val exerciseType: Int
)
