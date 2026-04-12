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
import com.example.healthmanager.sensor.LocationTracker
import com.example.healthmanager.sensor.StepCounterManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ExerciseType(val label: String, val needsSteps: Boolean, val met: Float) {
    ALL("总记录", false, 0f),
    WALKING("走路", true, 3.5f),
    RUNNING("跑步", true, 8.0f),
    STAIR_CLIMBING("上楼梯", true, 8.0f),
    CYCLING("骑行", false, 6.0f),
    SWIMMING("游泳", false, 7.0f);
}

class ExerciseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val repo = ExerciseRepository(db.exerciseDao())

    private val stepCounter = StepCounterManager(application)
    val sensorSteps: StateFlow<Int> = stepCounter.steps
    val isSensorAvailable: Boolean = stepCounter.isAvailable
    val isSedentary: StateFlow<Boolean> = stepCounter.isSedentary

    fun dismissSedentary() = stepCounter.dismissSedentary()

    private val gaitClassifier = GaitClassifier(application)
    val gaitResult: StateFlow<GaitResult?> = gaitClassifier.gaitResult
    val usingCnn: StateFlow<Boolean> = gaitClassifier.usingCnn

    // GPS 定位追踪（骑行/游泳距离）
    private val locationTracker = LocationTracker(application)
    val gpsDistance: StateFlow<Float> = locationTracker.distanceKm
    val isGpsTracking: StateFlow<Boolean> = locationTracker.isTracking

    private val _userId = MutableStateFlow(0)

    val userWeight: StateFlow<Float> = _userId
        .flatMapLatest { uid ->
            if (uid > 0) db.userDao().getUserById(uid).map { it?.weight?.takeIf { w -> w > 0f } ?: 70f }
            else flowOf(70f)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 70f)

    val sensorCalories: StateFlow<Float> =
        combine(sensorSteps, userWeight) { steps, weight ->
            steps * 0.7f / 1000f * weight * 1.036f
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private val _selectedType = MutableStateFlow(ExerciseType.ALL)
    val selectedType: StateFlow<ExerciseType> = _selectedType

    private val _isExercising = MutableStateFlow(false)
    val isExercising: StateFlow<Boolean> = _isExercising

    private val _sessionSeconds = MutableStateFlow(0L)
    val sessionSeconds: StateFlow<Long> = _sessionSeconds

    private val _sessionSteps = MutableStateFlow(0)
    val sessionSteps: StateFlow<Int> = _sessionSteps

    // 骑行/游泳的距离（km），用户手动输入
    private val _sessionDistance = MutableStateFlow(0f)
    val sessionDistance: StateFlow<Float> = _sessionDistance

    val sessionCalories: StateFlow<Float> =
        combine(_sessionSteps, _sessionSeconds, _selectedType, userWeight) { steps, seconds, type, weight ->
            if (type.needsSteps) {
                steps * 0.7f / 1000f * weight * 1.036f
            } else if (type != ExerciseType.ALL) {
                type.met * weight * (seconds / 3600f)
            } else {
                0f
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private var sessionStartSensorSteps = 0
    private var timerJob: Job? = null

    private val _selectedDate = MutableStateFlow(todayStr())
    val selectedDate: StateFlow<String> = _selectedDate

    val todayRecords: StateFlow<List<ExerciseRecord>> =
        combine(_userId, _selectedDate, _selectedType) { uid, date, type -> Triple(uid, date, type) }
            .flatMapLatest { (uid, date, type) ->
                if (uid > 0) {
                    if (type == ExerciseType.ALL) {
                        repo.getRecordsByDate(uid, date)
                    } else {
                        repo.getRecordsByDateAndType(uid, date, type.label)
                    }
                } else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySteps: StateFlow<Int> =
        combine(_userId, _selectedDate, _selectedType) { uid, date, type -> Triple(uid, date, type) }
            .flatMapLatest { (uid, date, type) ->
                if (uid > 0) {
                    if (type == ExerciseType.ALL) {
                        repo.getTotalStepsByDate(uid, date)
                    } else {
                        repo.getTotalStepsByDateAndType(uid, date, type.label)
                    }
                } else flowOf(0)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayCalories: StateFlow<Float> =
        combine(_userId, _selectedDate, _selectedType) { uid, date, type -> Triple(uid, date, type) }
            .flatMapLatest { (uid, date, type) ->
                if (uid > 0) {
                    if (type == ExerciseType.ALL) {
                        repo.getTotalCaloriesByDate(uid, date)
                    } else {
                        repo.getTotalCaloriesByDateAndType(uid, date, type.label)
                    }
                } else flowOf(0f)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val weekRecords: StateFlow<List<ExerciseRecord>> =
        _userId.flatMapLatest { uid ->
            if (uid > 0) repo.getRecordsSince(uid, sevenDaysAgoStr()) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult

    fun init(userId: Int) {
        _userId.value = userId
        stepCounter.start()
        gaitClassifier.loadModel()
        gaitClassifier.startListening()
    }

    fun selectType(type: ExerciseType) {
        if (!_isExercising.value) {
            _selectedType.value = type
            _sessionSeconds.value = 0L
            _sessionSteps.value = 0
            _sessionDistance.value = 0f
            sessionStartSensorSteps = 0
        }
    }

    /** 设置骑行/游泳距离（km） */
    fun setDistance(km: Float) {
        _sessionDistance.value = km
    }

    fun startExercise() {
        val currentType = _selectedType.value
        if (_isExercising.value || currentType == ExerciseType.ALL) return

        _isExercising.value = true
        _sessionSeconds.value = 0L
        _sessionSteps.value = 0
        _sessionDistance.value = 0f
        sessionStartSensorSteps = sensorSteps.value

        // 骑行/游泳：启动 GPS 追踪距离
        if (!currentType.needsSteps) {
            locationTracker.startTracking()
        }

        timerJob = viewModelScope.launch {
            while (_isExercising.value) {
                delay(1000)
                _sessionSeconds.value += 1

                if (currentType.needsSteps) {
                    val detectedGait = gaitClassifier.gaitResult.value
                    val detectedClass = detectedGait?.classIndex ?: 0

                    val shouldCountSteps = when (currentType) {
                        ExerciseType.WALKING -> detectedClass in 1..3
                        ExerciseType.RUNNING -> detectedClass == 2
                        ExerciseType.STAIR_CLIMBING -> detectedClass == 3
                        else -> false
                    }

                    if (shouldCountSteps) {
                        _sessionSteps.value = sensorSteps.value - sessionStartSensorSteps
                    }
                } else {
                    // 骑行/游泳：实时更新 GPS 距离
                    _sessionDistance.value = locationTracker.distanceKm.value
                }
            }
        }
        Log.d("Exercise", "开始运动: ${currentType.label}")
    }

    fun stopExercise() {
        if (!_isExercising.value) return
        _isExercising.value = false
        timerJob?.cancel()
        timerJob = null

        val type = _selectedType.value

        // 骑行/游泳：停止 GPS 追踪，获取最终距离
        if (!type.needsSteps) {
            val finalDistance = locationTracker.stopTracking()
            _sessionDistance.value = finalDistance
        }

        val seconds = _sessionSeconds.value
        val durationMinutes = (seconds / 60).toInt()
        val steps = if (type.needsSteps) _sessionSteps.value else 0
        val calories = sessionCalories.value
        val userId = _userId.value

        if (userId <= 0 || seconds < 10) {
            _saveResult.value = if (seconds < 10) "运动时间太短，未保存" else null
            return
        }

        val distanceKm = if (type.needsSteps) {
            steps * 0.7f / 1000f
        } else {
            _sessionDistance.value
        }

        viewModelScope.launch {
            repo.addRecord(
                ExerciseRecord(
                    userId = userId,
                    date = todayStr(),
                    steps = steps,
                    caloriesBurned = calories,
                    exerciseType = type.label,
                    durationMinutes = durationMinutes,
                    distanceKm = distanceKm,
                    note = ""
                )
            )
            _saveResult.value = "${type.label} ${durationMinutes}分钟 已保存"
            Log.d("Exercise", "保存运动记录: ${type.label}, ${durationMinutes}分钟, $steps 步")
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
            "上楼梯" -> 8.0f
            "骑行" -> 6.0f
            "游泳" -> 7.0f
            "走路" -> 3.5f
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
        if (_isExercising.value) {
            stopExercise()
        }
        timerJob?.cancel()
        locationTracker.stopTracking()
        stepCounter.stop()
        gaitClassifier.release()
    }

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private fun sevenDaysAgoStr() = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
