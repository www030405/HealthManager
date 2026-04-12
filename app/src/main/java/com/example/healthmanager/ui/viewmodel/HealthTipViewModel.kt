package com.example.healthmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.network.HealthTip
import com.example.healthmanager.network.HealthTipService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HealthTipViewModel : ViewModel() {
    private val apiService = HealthTipService()
    
    private val _tips = MutableStateFlow<List<HealthTip>>(emptyList())
    val tips: StateFlow<List<HealthTip>> = _tips.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadTips()
    }

    fun loadTips() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = apiService.getHealthTips()
            _tips.value = result ?: emptyList()
            _isLoading.value = false
        }
    }
}
