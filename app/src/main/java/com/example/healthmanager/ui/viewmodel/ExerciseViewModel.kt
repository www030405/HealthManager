package com.example.healthmanager.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.data.repository.ExerciseRepository
import com.example.healthmanager.ml.GaitClassifier
import com.example.healthmanager.ml.GaitResult
import com.example.healthmanager.sensor.StepCounterManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 运动方式定义
 * needsSteps: 是否需要计步（走路/跑步需要，骑行/游泳不需要）
 * met: 代谢当量，用于卡路里估算
 */
enum class ExerciseMode(
    val label: String,
    val needsSteps: Boolean,
    val met: Float
) {
    WALKING("步行", true, 3.5f),
    RUNNING("跑步", true, 8.0f),
    CYCLING("骑行", false, 6.0f),
    SWIMMING("游泳", false, 7.0f);
}

class ExerciseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val repo = ExerciseRepository(db.exerciseDao())

    // 硬件计步传感器
    private val stepCounter = StepCounterManager(application)
    val sensorSteps: StateFlow<Int> = stepCounter.steps
    val isSensorAvailable: Boolean = stepCounter.isAvailable

    // 步态分类器（用于实时显示识别结果）
    private val gaitClassifier = GaitClassifier(application)
    val gaitResult: StateFlow<GaitResult?> = gaitClassifier.gaitResult

    private val _userId = MutableStateFlow(0)

    // 用户体重（用于卡路里估算），默认70kg
    val userWeight: StateFlow<Float> = _userId
        .flatMapLatest { uid ->
            if (uid > 0) db.userDao().getUserById(uid).map { it?.weight?.takeIf { w -> w > 0f } ?: 70f }
            else flowOf(70f)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 70f)

    /**
     * 传感器步数估算卡路里
     */
    val sensorCalories: StateFlow<Float> =
        combine(sensorSteps, userWeight) { steps, weight ->
            steps * 0.7f / 1000f * weight * 1.036f
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // ========== 运动模式 & 会话 ==========

    // 当前选择的运动模式
    private val _currentMode = MutableStateFlow(ExerciseMode.WALKING)
    val currentMode: StateFlow<ExerciseMode> = _currentMode

    // 是否正在运动中
    private val _isExercising = MutableStateFlow(false)
    val isExercising: StateFlow<Boolean> = _isExercising

    // 本次运动已用时（秒）
    private val _sessionSeconds = MutableStateFlow(0L)
    val sessionSeconds: StateFlow<Long> = _sessionSeconds

    // 本次运动的步数（仅走路/跑步模式有效）
    private val _sessionSteps = MutableStateFlow(0)
    val sessionSteps: StateFlow<Int> = _sessionSteps

    // 本次运动的卡路里
    val sessionCalories: StateFlow<Float> =
        combine(_sessionSteps, _sessionSeconds, _currentMode, userWeight) { steps, seconds, mode, weight ->
            if (mode.needsSteps) {
                // 走路/跑步：基于步数估算
                steps * 0.7f / 1000f * weight * 1.036f
            } else {
                // 骑行/游泳：基于 MET × 体重 × 时间
                mode.met * weight * (seconds / 3600f)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // 会话开始时的传感器步数基准
    private var sessionStartSensorSteps = 0
    private var timerJob: Job? = null

    // ========== 数据查询 ==========

    private val _selectedDate = MutableStateFlow(todayStr())
    val selectedDate: StateFlow<String> = _selectedDate

    val todayRecords: StateFlow<List<ExerciseRecord>> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getRecordsByDate(uid, date) else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySteps: StateFlow<Int> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getTotalStepsByDate(uid, date) else flowOf(0)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayCalories: StateFlow<Float> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getTotalCaloriesByDate(uid, date) else flowOf(0f)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val weekRecords: StateFlow<List<ExerciseRecord>> =
        _userId.flatMapLatest { uid ->
            if (uid > 0) repo.getRecordsSince(uid, sevenDaysAgoStr()) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult

    // ========== 方法 ==========

    fun init(userId: Int) {
        _userId.value = userId
        stepCounter.start()
        gaitClassifier.loadModel()
        gaitClassifier.startListening()
    }

    /** 切换运动模式（只有在未运动时才能切换） */
    fun switchMode(mode: ExerciseMode) {
        if (!_isExercising.value) {
            _currentMode.value = mode
        }
    }

    /** 开始运动 */
    fun startExercise() {
        if (_isExercising.value) return
        _isExercising.value = true
        _sessionSeconds.value = 0L
        _sessionSteps.value = 0
        sessionStartSensorSteps = sensorSteps.value

        // 启动计时器
        timerJob = viewModelScope.launch {
            while (_isExercising.value) {
                delay(1000)
                _sessionSeconds.value += 1
                // 走路/跑步模式实时更新步数
                if (_currentMode.value.needsSteps) {
                    _sessionSteps.value = sensorSteps.value - sessionStartSensorSteps
                }
            }
        }
        Log.d("Exercise", "开始运动: ${_currentMode.value.label}")
    }

    /** 结束运动并保存记录 */
    fun stopExercise() {
        if (!_isExercising.value) return
        _isExercising.value = false
        timerJob?.cancel()
        timerJob = null

        val mode = _currentMode.value
        val seconds = _sessionSeconds.value
        val durationMinutes = (seconds / 60).toInt()
        val steps = if (mode.needsSteps) _sessionSteps.value else 0
        val calories = sessionCalories.value
        val userId = _userId.value

        if (userId <= 0 || seconds < 10) {
            // 运动时间太短不保存
            _saveResult.value = if (seconds < 10) "运动时间太短，未保存" else null
            return
        }

        val distanceKm = if (mode.needsSteps) {
            steps * 0.7f / 1000f
        } else {
            0f
        }

        viewModelScope.launch {
            repo.addRecord(
                ExerciseRecord(
                    userId = userId,
                    date = todayStr(),
                    steps = steps,
                    caloriesBurned = calories,
                    exerciseType = mode.label,
                    durationMinutes = durationMinutes,
                    distanceKm = distanceKm,
                    note = ""
                )
            )
            _saveResult.value = "${mode.label} ${durationMinutes}分钟 已保存"
            Log.d("Exercise", "保存运动记录: ${mode.label}, ${durationMinutes}分钟, $steps 步")
        }
    }

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    fun addRecord(
        exerciseType: String,
        steps: Int,
        durationMinutes: Int,
        distanceKm: Float,
        note: String
    ) {
        val userId = _userId.value
        if (userId <= 0) return
        val met = when (exerciseType) {
            "跑步" -> 8.0f
            "骑行" -> 6.0f
            "游泳" -> 7.0f
            "步行" -> 3.5f
            else -> 4.0f
        }
        val weight = userWeight.value
        val calories = met * weight * (durationMinutes / 60f)

        viewModelScope.launch {
            repo.addRecord(
                ExerciseRecord(
                    userId = userId,
                    date = _selectedDate.value,
                    steps = steps,
                    caloriesBurned = calories,
                    exerciseType = exerciseType,
                    durationMinutes = durationMinutes,
                    distanceKm = distanceKm,
                    note = note
                )
            )
            _saveResult.value = "记录已保存"
        }
    }

    fun deleteRecord(record: ExerciseRecord) {
        viewModelScope.launch { repo.deleteRecord(record) }
    }

    fun clearSaveResult() { _saveResult.value = null }

    override fun onCleared() {
        super.onCleared()
        // 如果正在运动中，自动保存
        if (_isExercising.value) {
            stopExercise()
        }
        timerJob?.cancel()
        stepCounter.stop()
        gaitClassifier.release()
    }

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private fun sevenDaysAgoStr() = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
