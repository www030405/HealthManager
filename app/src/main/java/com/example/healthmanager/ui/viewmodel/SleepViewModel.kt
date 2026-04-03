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
        // 计算睡眠时长（考虑跨天情况）
        val bed = LocalTime.parse(bedTime, DateTimeFormatter.ofPattern("HH:mm"))
        val wake = LocalTime.parse(wakeTime, DateTimeFormatter.ofPattern("HH:mm"))
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

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private fun sevenDaysAgoStr() = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
