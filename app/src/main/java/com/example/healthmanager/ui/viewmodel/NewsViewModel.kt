package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.network.HealthArticle
import com.example.healthmanager.network.HealthNewsApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = HealthNewsApiService()

    private val _articles = MutableStateFlow<List<HealthArticle>?>(null)
    val articles: StateFlow<List<HealthArticle>?> = _articles

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg

    fun loadArticles(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            try {
                _articles.value = apiService.getHealthArticles(category)
                if (_articles.value == null) {
                    _errorMsg.value = "暂无文章数据"
                }
            } catch (e: Exception) {
                _errorMsg.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clear() {
        _articles.value = null
        _errorMsg.value = null
    }
}