package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.HealthScoreHistory
import com.example.healthmanager.data.repository.HealthScoreHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HealthScoreHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val repo = HealthScoreHistoryRepository(db.healthScoreHistoryDao())

    private val _scoreHistory = MutableStateFlow<List<HealthScoreHistory>>(emptyList())
    val scoreHistory: StateFlow<List<HealthScoreHistory>> = _scoreHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadHistory(userId: Int, days: Int = 30) {
        viewModelScope.launch {
            _isLoading.value = true
            val startDate = LocalDate.now().minusDays(days.toLong() - 1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            repo.getScoresSince(userId, startDate).collect { history ->
                _scoreHistory.value = history
                _isLoading.value = false
            }
        }
    }
}