package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.User
import com.example.healthmanager.data.repository.UserPreferences
import com.example.healthmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 注册/登录 ViewModel
 * 处理用户认证相关业务逻辑
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val userRepo = UserRepository(db.userDao())
    val prefs = UserPreferences(application)

    // UI状态
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("用户名和密码不能为空")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val user = userRepo.login(username.trim(), password)
            if (user != null) {
                prefs.currentUserId = user.id
                _authState.value = AuthState.Success(user)
            } else {
                _authState.value = AuthState.Error("用户名或密码错误")
            }
        }
    }

    fun register(username: String, password: String, confirmPassword: String, nickname: String) {
        when {
            username.isBlank() -> { _authState.value = AuthState.Error("用户名不能为空"); return }
            password.length < 6 -> { _authState.value = AuthState.Error("密码至少6位"); return }
            password != confirmPassword -> { _authState.value = AuthState.Error("两次密码不一致"); return }
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val user = User(
                username = username.trim(),
                password = password,
                nickname = nickname.ifBlank { username.trim() }
            )
            userRepo.register(user)
                .onSuccess { id ->
                    prefs.currentUserId = id.toInt()
                    _authState.value = AuthState.Success(user.copy(id = id.toInt()))
                }
                .onFailure { e ->
                    _authState.value = AuthState.Error(e.message ?: "注册失败")
                }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}
