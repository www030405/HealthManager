package com.example.healthmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.data.database.HealthDatabase
import com.example.healthmanager.data.entity.SleepRecord
import com.example.healthmanager.data.repository.SleepRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter

class SleepViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HealthDatabase.getDatabase(application)
    private val repo = SleepRepository(db.sleepDao())

    private val _userId = MutableStateFlow(0)
    private val _selectedDate = MutableStateFlow(todayStr())

    val selectedDate: StateFlow<String> = _selectedDate

    // 当天睡眠记录
    val todayRecord: StateFlow<SleepRecord?> =
        combine(_userId, _selectedDate) { uid, date -> Pair(uid, date) }
            .flatMapLatest { (uid, date) ->
                if (uid > 0) repo.getRecordByDate(uid, date) else flowOf(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 近7天记录（用于图表）
    val weekRecords: StateFlow<List<SleepRecord>> =
        _userId.flatMapLatest { uid ->
            if (uid > 0) repo.getRecordsSince(uid, sevenDaysAgoStr()) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 近7天平均睡眠时长
    val avgDuration: StateFlow<Float> =
        _userId.flatMapLatest { uid ->
            if (uid > 0) repo.getAverageDuration(uid, sevenDaysAgoStr()).map { it ?: 0f }
            else flowOf(0f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult

    fun init(userId: Int) {
        _userId.value = userId
    }

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    /**
     * 保存睡眠记录
     * @param bedTime 入睡时间 HH:mm
     * @param wakeTime 起床时间 HH:mm
     * @param quality 1-5睡眠质量
     * @param note 备注
     */
    fun saveRecord(bedTime: String, wakeTime: String, quality: Int, note: String) {
        val userId = _userId.value
        if (userId <= 0) return
        if (bedTime.isBlank() || wakeTime.isBlank()) {
            _saveResult.value = "请填写入睡和起床时间"
            return
        }
        
        // 格式化并解析时间
        val formattedBedTime = formatTimeInput(bedTime)
        val formattedWakeTime = formatTimeInput(wakeTime)
        
        if (formattedBedTime == null || formattedWakeTime == null) {
            _saveResult.value = "时间格式不正确，请使用 HH:mm 格式（如：08:00）"
            return
        }
        
        // 计算睡眠时长（考虑跨天情况）
        val bed = try {
            LocalTime.parse(formattedBedTime, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            _saveResult.value = "入睡时间格式错误"
            return
        }
        val wake = try {
            LocalTime.parse(formattedWakeTime, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            _saveResult.value = "起床时间格式错误"
            return
        }
        val durationMinutes = if (wake.isAfter(bed)) {
            Duration.between(bed, wake).toMinutes()
        } else {
            Duration.between(bed, wake).toMinutes() + 24 * 60
        }
        val durationHours = durationMinutes / 60f
        // 简单估算深睡时间（约占总睡眠20-25%）
        val deepSleep = durationHours * 0.22f

        viewModelScope.launch {
            repo.addRecord(
                SleepRecord(
                    userId = userId,
                    date = _selectedDate.value,
                    bedTime = bedTime,
                    wakeTime = wakeTime,
                    durationHours = durationHours,
                    quality = quality,
                    deepSleepHours = deepSleep,
                    note = note
                )
            )
            _saveResult.value = "睡眠记录已保存 (${String.format("%.1f", durationHours)}小时)"
        }
    }

    fun deleteRecord(record: SleepRecord) {
        viewModelScope.launch { repo.deleteRecord(record) }
    }

    fun clearSaveResult() { _saveResult.value = null }

    /**
     * 格式化时间输入，支持多种格式：
     * - "8" -> "08:00"
     * - "8:0" -> "08:00"
     * - "8:00" -> "08:00"
     * - "08:00" -> "08:00"
     * - 无效输入 -> null
     */
    private fun formatTimeInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        
        return try {
            when {
                // 只有小时，如 "8"
                !trimmed.contains(':') -> {
                    val hour = trimmed.toIntOrNull()
                    if (hour == null || hour < 0 || hour > 23) return null
                    String.format("%02d:00", hour)
                }
                // 有冒号，如 "8:0", "8:00", "08:00"
                else -> {
                    val parts = trimmed.split(":")
                    if (parts.size != 2) return null
                    
                    val hour = parts[0].toIntOrNull()
                    val minute = parts[1].toIntOrNull()
                    
                    if (hour == null || minute == null) return null
                    if (hour < 0 || hour > 23) return null
                    if (minute < 0 || minute > 59) return null
                    
                    String.format("%02d:%02d", hour, minute)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private fun sevenDaysAgoStr() = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
