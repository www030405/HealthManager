package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.DietRecord
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.data.entity.SleepRecord
import com.example.healthmanager.data.repository.DietRepository
import com.example.healthmanager.data.repository.ExerciseRepository
import com.example.healthmanager.data.repository.SleepRepository
import com.example.healthmanager.health.HealthScore
import com.example.healthmanager.health.HealthScoreEngine
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 健康评分 ViewModel
 *
 * 聚合运动、睡眠、饮食三个维度的数据，
 * 通过 HealthScoreEngine 计算多维度健康评分。
 */
class HealthScoreViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val exerciseRepo = ExerciseRepository(db.exerciseDao())
    private val sleepRepo = SleepRepository(db.sleepDao())
    private val dietRepo = DietRepository(db.dietDao())

    private val _userId = MutableStateFlow(0)
    private val _targetSteps = MutableStateFlow(8000)
    private val _targetCalories = MutableStateFlow(2000)

    private val today = todayStr()
    private val sevenDaysAgo = sevenDaysAgoStr()

    // ── 运动聚合数据 ──
    private data class ExerciseData(
        val todaySteps: Int = 0,
        val todayMinutes: Int = 0,
        val weekActiveDays: Int = 0
    )

    private val exerciseData: Flow<ExerciseData> = _userId.flatMapLatest { uid ->
        if (uid <= 0) return@flatMapLatest flowOf(ExerciseData())
        combine(
            exerciseRepo.getTotalStepsByDate(uid, today),
            exerciseRepo.getRecordsByDate(uid, today),
            exerciseRepo.getRecordsSince(uid, sevenDaysAgo)
        ) { steps, todayRecords, weekRecords ->
            ExerciseData(
                todaySteps = steps,
                todayMinutes = todayRecords.sumOf { it.durationMinutes },
                weekActiveDays = weekRecords.map { it.date }.distinct().size
            )
        }
    }

    // ── 睡眠数据 ──
    private val todaySleep: Flow<SleepRecord?> = _userId.flatMapLatest { uid ->
        if (uid > 0) sleepRepo.getRecordByDate(uid, today) else flowOf(null)
    }

    // ── 饮食聚合数据 ──
    private data class DietData(
        val totalCalories: Float = 0f,
        val mealCount: Int = 0,
        val hasRecord: Boolean = false
    )

    private val dietData: Flow<DietData> = _userId.flatMapLatest { uid ->
        if (uid <= 0) return@flatMapLatest flowOf(DietData())
        combine(
            dietRepo.getTotalCaloriesByDate(uid, today),
            dietRepo.getRecordsByDate(uid, today)
        ) { calories, records ->
            val mainMealCount = records
                .map { it.mealType }
                .distinct()
                .count { it in listOf("早餐", "午餐", "晚餐") }
            DietData(
                totalCalories = calories,
                mealCount = mainMealCount,
                hasRecord = records.isNotEmpty()
            )
        }
    }

    /**
     * 综合健康评分（实时响应数据变化）
     */
    val healthScore: StateFlow<HealthScore> = combine(
        exerciseData,
        todaySleep,
        dietData
    ) { exercise, sleep, diet ->
        val targetSteps = _targetSteps.value
        val targetCalories = _targetCalories.value

        val exerciseScore = HealthScoreEngine.calculateExerciseScore(
            todaySteps = exercise.todaySteps,
            targetSteps = targetSteps,
            todayExerciseMinutes = exercise.todayMinutes,
            weekActiveDays = exercise.weekActiveDays
        )

        val sleepScore = HealthScoreEngine.calculateSleepScore(
            durationHours = sleep?.durationHours ?: 0f,
            quality = sleep?.quality ?: 0,
            hasRecord = sleep != null
        )

        val dietScore = HealthScoreEngine.calculateDietScore(
            totalCalories = diet.totalCalories,
            targetCalories = targetCalories,
            mealCount = diet.mealCount,
            hasRecord = diet.hasRecord
        )

        val totalScore = HealthScoreEngine.calculateTotalScore(exerciseScore, sleepScore, dietScore)
        val level = HealthScoreEngine.getHealthLevel(totalScore)
        val advices = HealthScoreEngine.generateAdvice(exerciseScore, sleepScore, dietScore)

        HealthScore(
            totalScore = totalScore,
            exerciseScore = exerciseScore,
            sleepScore = sleepScore,
            dietScore = dietScore,
            level = level,
            advices = advices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HealthScore())

    fun init(userId: Int, targetSteps: Int = 8000, targetCalories: Int = 2000) {
        _userId.value = userId
        _targetSteps.value = if (targetSteps > 0) targetSteps else 8000
        _targetCalories.value = if (targetCalories > 0) targetCalories else 2000
    }

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private fun sevenDaysAgoStr() = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
