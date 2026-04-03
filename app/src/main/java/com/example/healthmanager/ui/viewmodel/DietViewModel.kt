package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.DietRecord
import com.example.healthmanager.data.repository.DietRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DietViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val repo = DietRepository(db.dietDao())

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
        }
    }

    fun deleteRecord(record: DietRecord) {
        viewModelScope.launch { repo.deleteRecord(record) }
    }

    fun clearSaveResult() { _saveResult.value = null }

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
}
