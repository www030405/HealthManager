package com.example.healthmanager.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.DietRecord
import com.example.healthmanager.data.repository.DietRepository
import com.example.healthmanager.healthconnect.HealthConnectManager
import com.example.healthmanager.healthconnect.HealthConnectManager.Companion.isAvailable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DietViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val repo = DietRepository(db.dietDao())

    // Health Connect 管理器
    private val hcManager = if (isAvailable(application)) {
        HealthConnectManager(application)
    } else null

    private val _userId = MutableStateFlow(0)
    private val _selectedDate = MutableStateFlow(todayStr())

    val selectedDate: StateFlow<String> = _selectedDate

    // 当天所有饮食记录
    val todayRecords: StateFlow<List<DietRecord>> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getRecordsByDate(uid, date) else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当天总摄入卡路里
    val todayCalories: StateFlow<Float> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getTotalCaloriesByDate(uid, date) else flowOf(0f)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // 当天三大营养素摄入量
    data class MacroNutrients(val carbs: Float, val protein: Float, val fat: Float)
    val todayMacros: StateFlow<MacroNutrients> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getRecordsByDate(uid, date).map { records ->
                    MacroNutrients(
                        carbs = records.sumOf { it.carbohydrates.toDouble() }.toFloat(),
                        protein = records.sumOf { it.protein.toDouble() }.toFloat(),
                        fat = records.sumOf { it.fat.toDouble() }.toFloat()
                    )
                } else flowOf(MacroNutrients(0f, 0f, 0f))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MacroNutrients(0f, 0f, 0f))

    // 本周饮食记录
    val weekRecords: StateFlow<List<DietRecord>> =
        _userId.flatMapLatest { uid ->
            if (uid > 0) repo.getAllRecords(uid).map { records ->
                val sevenDaysAgo = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
                records.filter { it.date >= sevenDaysAgo }
            } else flowOf(emptyList())
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
        mealType: String,
        foodName: String,
        amount: Float,
        calories: Float,
        protein: Float,
        fat: Float,
        carbohydrates: Float,
        note: String
    ) {
        val userId = _userId.value
        if (userId <= 0) return
        viewModelScope.launch {
            repo.addRecord(
                DietRecord(
                    userId = userId,
                    date = _selectedDate.value,
                    mealType = mealType,
                    foodName = foodName,
                    amount = amount,
                    calories = calories,
                    protein = protein,
                    fat = fat,
                    carbohydrates = carbohydrates,
                    note = note
                )
            )
            _saveResult.value = "饮食记录已保存"

            // 同步到 Health Connect
            syncToHealthConnect(mealType, foodName, calories, protein, fat, carbohydrates)
        }
    }

    /**
     * 同步饮食记录到 Health Connect
     */
    private fun syncToHealthConnect(
        mealType: String,
        foodName: String,
        calories: Float,
        protein: Float,
        fat: Float,
        carbohydrates: Float
    ) {
        viewModelScope.launch {
            try {
                hcManager?.let { manager ->
                    val hasPermissions = manager.hasAllPermissions()
                    Log.d("DietViewModel", "HC权限: $hasPermissions")
                    if (!hasPermissions) return@launch

                    val now = Instant.now()
                    val mealTypeInt = when (mealType) {
                        "早餐" -> 1  // MEAL_TYPE_BREAKFAST
                        "午餐" -> 2  // MEAL_TYPE_LUNCH
                        "晚餐" -> 3  // MEAL_TYPE_DINNER
                        "加餐" -> 4  // MEAL_TYPE_SNACK
                        else -> 1
                    }

                    manager.writeNutrition(
                        mealName = foodName,
                        calories = calories.toDouble(),
                        protein = protein.toDouble(),
                        fat = fat.toDouble(),
                        carbs = carbohydrates.toDouble(),
                        mealType = mealTypeInt,
                        time = now
                    )
                    Log.d("DietViewModel", "HC饮食写入成功: $foodName")
                } ?: run {
                    Log.d("DietViewModel", "HC未安装")
                }
            } catch (e: Exception) {
                Log.e("DietViewModel", "HC同步失败: ${e.message}")
            }
        }
    }

    fun deleteRecord(record: DietRecord) {
        viewModelScope.launch { repo.deleteRecord(record) }
    }

    fun clearSaveResult() { _saveResult.value = null }

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
}
