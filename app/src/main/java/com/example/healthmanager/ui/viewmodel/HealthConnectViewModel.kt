package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.healthconnect.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Health Connect 数据读写 ViewModel
 * 管理权限状态和数据同步
 */
class HealthConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = HealthConnectManager(application)

    val isAvailable = HealthConnectManager.isAvailable(application)

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _todayStepsFromHC = MutableStateFlow(0L)
    val todayStepsFromHC: StateFlow<Long> = _todayStepsFromHC

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    /**
     * 获取 Health Connect 权限申请合约（在 Activity/Composable 中注册）
     */
    fun getPermissionContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    fun checkPermissions() {
        viewModelScope.launch {
            _permissionGranted.value = manager.hasAllPermissions()
        }
    }

    /**
     * 从 Health Connect 同步今日步数
     */
    fun syncTodaySteps() {
        if (!isAvailable || !_permissionGranted.value) return
        viewModelScope.launch {
            try {
                val steps = manager.readSteps(LocalDate.now())
                _todayStepsFromHC.value = steps
                _syncStatus.value = "Health Connect 同步成功：今日步数 $steps 步"
            } catch (e: Exception) {
                _syncStatus.value = "同步失败：${e.message}"
            }
        }
    }

    /**
     * 将 CNN 识别出的步数写回 Health Connect
     */
    fun writeCnnStepsToHealthConnect(steps: Long) {
        if (!isAvailable || !_permissionGranted.value || steps <= 0) return
        viewModelScope.launch {
            try {
                val now = java.time.Instant.now()
                val start = now.minusSeconds(3600) // 过去1小时
                manager.writeSteps(steps, start, now)
                _syncStatus.value = "已将 $steps 步写入 Health Connect"
            } catch (e: Exception) {
                _syncStatus.value = "写入失败：${e.message}"
            }
        }
    }

    fun clearSyncStatus() { _syncStatus.value = null }
}
