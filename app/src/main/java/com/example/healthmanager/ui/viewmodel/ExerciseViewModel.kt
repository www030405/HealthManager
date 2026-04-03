package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExerciseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val repo = ExerciseRepository(db.exerciseDao())

    private val _userId = MutableStateFlow(0)
    private val _selectedDate = MutableStateFlow(todayStr())

    val selectedDate: StateFlow<String> = _selectedDate

    // 当天运动记录列表
    val todayRecords: StateFlow<List<ExerciseRecord>> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getRecordsByDate(uid, date) else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当天总步数
    val todaySteps: StateFlow<Int> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getTotalStepsByDate(uid, date) else flowOf(0)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 当天消耗卡路里
    val todayCalories: StateFlow<Float> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getTotalCaloriesByDate(uid, date) else flowOf(0f)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // 近7天记录（用于图表）
    val weekRecords: StateFlow<List<ExerciseRecord>> =
        _userId.flatMapLatest { uid ->
            if (uid > 0) repo.getRecordsSince(uid, sevenDaysAgoStr()) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult

    fun init(userId: Int) {
        _userId.value = userId
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
        // 根据运动类型和时长估算消耗卡路里（MET值简单估算）
        val met = when (exerciseType) {
            "跑步" -> 8.0f
            "骑行" -> 6.0f
            "游泳" -> 7.0f
            "步行" -> 3.5f
            else -> 4.0f
        }
        // 假设体重70kg，卡路里 = MET × 体重(kg) × 时间(h)
        val calories = met * 70 * (durationMinutes / 60f)

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

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private fun sevenDaysAgoStr() = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
