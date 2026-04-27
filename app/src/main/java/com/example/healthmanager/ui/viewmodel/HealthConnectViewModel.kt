package com.example.healthmanager.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.healthconnect.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Health Connect 数据读写 ViewModel
 * 管理权限状态和数据同步
 */
class HealthConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = HealthConnectManager(application)

    /**
     * 用于跨进程持久化"今日是否已从 HC 读过基准"的标记。
     * 仅作幂等用途，体积极小，单独存放以避免与其他模块的 prefs 冲突。
     */
    private val prefs = application.getSharedPreferences("hc_vm_prefs", Context.MODE_PRIVATE)
    private val KEY_BASELINE_FETCHED_DATE = "baseline_fetched_date"

    val isAvailable = HealthConnectManager.isAvailable(application)

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _todayStepsFromHC = MutableStateFlow(0L)
    val todayStepsFromHC: StateFlow<Long> = _todayStepsFromHC

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    /**
     * 已成功读取 HC 步数的日期（ISO LOCAL_DATE）。
     * 用于保证"每天只从 HC 读取一次基准"——避免同一天多次读取造成 baseline 重复包含 sensor delta。
     * 持久化到 prefs，使该幂等承诺在 app 被杀+同日重启场景下依旧成立。
     */
    private var baselineFetchedDate: String? = prefs.getString(KEY_BASELINE_FETCHED_DATE, null)

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
     * 从 Health Connect 同步今日步数。
     * 幂等：同一天内多次调用只会真实读取一次。
     * 跨天后由 [invalidateBaseline] 清除已读标记，下次调用会重新读取。
     */
    fun syncTodaySteps() {
        if (!isAvailable || !_permissionGranted.value) return
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (baselineFetchedDate == today) return  // 当日已读，跳过
        viewModelScope.launch {
            try {
                val steps = manager.readSteps(LocalDate.now())
                _todayStepsFromHC.value = steps
                baselineFetchedDate = today
                prefs.edit().putString(KEY_BASELINE_FETCHED_DATE, today).apply()
            } catch (e: Exception) {
                _syncStatus.value = "同步失败：${e.message}"
            }
        }
    }

    /**
     * 让 HC 基准失效，由跨天复位回调触发。
     * 下次 [syncTodaySteps] 调用时会重新从 HC 拉取新一天的步数。
     */
    fun invalidateBaseline() {
        baselineFetchedDate = null
        _todayStepsFromHC.value = 0L
        prefs.edit().remove(KEY_BASELINE_FETCHED_DATE).apply()
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
