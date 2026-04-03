package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.User
import com.example.healthmanager.data.repository.UserPreferences
import com.example.healthmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val userRepo = UserRepository(db.userDao())
    val prefs = UserPreferences(application)

    private val _userId = MutableStateFlow(prefs.currentUserId)

    val currentUser: StateFlow<User?> = _userId
        .flatMapLatest { uid ->
            if (uid > 0) userRepo.getUserById(uid) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _updateResult = MutableStateFlow<String?>(null)
    val updateResult: StateFlow<String?> = _updateResult

    fun refreshUser() {
        _userId.value = prefs.currentUserId
    }

    fun updateProfile(nickname: String, gender: String, age: Int, height: Float, weight: Float,
                      targetSteps: Int, targetCalories: Int) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            userRepo.updateUser(
                user.copy(
                    nickname = nickname,
                    gender = gender,
                    age = age,
                    height = height,
                    weight = weight,
                    targetSteps = targetSteps,
                    targetCalories = targetCalories
                )
            )
            _updateResult.value = "个人资料已更新"
        }
    }

    fun clearUpdateResult() { _updateResult.value = null }
}
