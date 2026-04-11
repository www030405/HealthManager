package com.example.healthmanager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.data.entity.SleepRecord
import com.example.healthmanager.ui.viewmodel.ProfileViewModel
import com.example.healthmanager.ui.viewmodel.SleepViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(navController: NavController) {
    // 根据睡眠时长自动计算星级
    fun calculateQuality(durationHours: Float): Int = when {
        durationHours >= 7f -> 5
        durationHours >= 6f -> 4
        durationHours >= 5f -> 3
        durationHours >= 4f -> 2
        else -> 1
    }

    val profileVm: ProfileViewModel = viewModel()
    val viewModel: SleepViewModel = viewModel()
    val userId = profileVm.prefs.currentUserId

    LaunchedEffect(userId) { viewModel.init(userId) }

    val todayRecord by viewModel.todayRecord.collectAsState()
    val weekRecords by viewModel.weekRecords.collectAsState()
    val avgDuration by viewModel.avgDuration.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(saveResult) {
        if (saveResult != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("睡眠监测") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "记录睡眠")
            }
        },
        snackbarHost = {
            saveResult?.let {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 今日睡眠卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("今日睡眠", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (todayRecord != null) {
                            val record = todayRecord!!
                            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("入睡", fontSize = 12.sp)
                                    Text(record.bedTime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("起床", fontSize = 12.sp)
                                    Text(record.wakeTime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("时长", fontSize = 12.sp)
                                    Text(
                                        String.format("%.1f", record.durationHours) + "h",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val autoQuality = calculateQuality(record.durationHours)
                                Text("睡眠质量：", fontSize = 13.sp)
                                repeat(5) { i ->
                                    Text(
                                        if (i < autoQuality) "★" else "☆",
                                        color = if (i < autoQuality)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                "今天还没有睡眠记录",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 近7天平均
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("近7天平均睡眠", fontSize = 14.sp)
                            Text(
                                String.format("%.1f", avgDuration) + " 小时/天",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // 睡眠质量评价
                        val tip = when {
                            avgDuration >= 7f -> "✓ 睡眠充足"
                            avgDuration >= 6f -> "⚠ 略显不足"
                            else -> "✗ 需要改善"
                        }
                        Text(
                            tip,
                            color = when {
                                avgDuration >= 7f -> MaterialTheme.colorScheme.primary
                                avgDuration >= 6f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }

            item { Text("历史记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

            if (weekRecords.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        Text("暂无记录，点击右下角 + 添加", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            items(weekRecords.reversed(), key = { it.id }) { record ->
                SleepRecordItem(record = record, onDelete = { viewModel.deleteRecord(record) })
            }
        }
    }

    if (showDialog) {
        AddSleepDialog(
            onDismiss = { showDialog = false },
            onConfirm = { bedTime, wakeTime, quality, note ->
                viewModel.saveRecord(bedTime, wakeTime, quality, note)
                showDialog = false
            }
        )
    }
}

@Composable
private fun SleepRecordItem(record: SleepRecord, onDelete: () -> Unit) {
    // 根据睡眠时长自动计算星级
    fun calculateQuality(durationHours: Float): Int = when {
        durationHours >= 7f -> 5
        durationHours >= 6f -> 4
        durationHours >= 5f -> 3
        durationHours >= 4f -> 2
        else -> 1
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.date, fontWeight = FontWeight.SemiBold)
                Text(
                    "${record.bedTime} → ${record.wakeTime} · ${String.format("%.1f", record.durationHours)}h",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val autoQuality = calculateQuality(record.durationHours)
                Row {
                    repeat(5) { i ->
                        Text(
                            if (i < autoQuality) "★" else "☆",
                            color = if (i < autoQuality)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddSleepDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String) -> Unit
) {
    var bedTime by remember { mutableStateOf("22:30") }
    var wakeTime by remember { mutableStateOf("07:00") }
    var quality by remember { mutableIntStateOf(3) }
    var note by remember { mutableStateOf("") }

    // 根据睡眠时长自动计算星级
    fun calculateQuality(durationHours: Float): Int = when {
        durationHours >= 7f -> 5
        durationHours >= 6f -> 4
        durationHours >= 5f -> 3
        durationHours >= 4f -> 2
        else -> 1
    }

    // 计算睡眠时长
    fun calculateDuration(bed: String, wake: String): Float {
        return try {
            val bedParts = bed.split(":")
            val wakeParts = wake.split(":")
            val bedMinutes = bedParts[0].toInt() * 60 + bedParts[1].toInt()
            val wakeMinutes = wakeParts[0].toInt() * 60 + wakeParts[1].toInt()
            var duration = if (wakeMinutes >= bedMinutes) wakeMinutes - bedMinutes else (24 * 60 - bedMinutes + wakeMinutes)
            duration / 60f
        } catch (e: Exception) { 0f }
    }

    val autoQuality = calculateQuality(calculateDuration(bedTime, wakeTime))
    
    // 格式化时间输入的辅助函数
    fun formatTimeInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return input
        
        return try {
            when {
                // 只有小时，如 "8"
                !trimmed.contains(':') -> {
                    val hour = trimmed.toIntOrNull()
                    if (hour == null || hour < 0 || hour > 23) return input
                    String.format("%02d:00", hour)
                }
                // 有冒号，如 "8:0", "8:00", "08:00"
                else -> {
                    val parts = trimmed.split(":")
                    if (parts.size != 2) return input
                    
                    val hour = parts[0].toIntOrNull()
                    val minute = parts[1].toIntOrNull()
                    
                    if (hour == null || minute == null) return input
                    if (hour < 0 || hour > 23) return input
                    if (minute < 0 || minute > 59) return input
                    
                    String.format("%02d:%02d", hour, minute)
                }
            }
        } catch (e: Exception) {
            input
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录睡眠") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = bedTime,
                    onValueChange = { bedTime = it },
                    label = { Text("入睡时间（HH:mm）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("如：22:30") }
                )
                OutlinedTextField(
                    value = wakeTime,
                    onValueChange = { wakeTime = it },
                    label = { Text("起床时间（HH:mm）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("如：07:00") }
                )
                // 显示根据时长自动计算的星级
                val currentDuration = calculateDuration(bedTime, wakeTime)
                val currentQuality = calculateQuality(currentDuration)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("睡眠质量：", fontSize = 14.sp)
                    repeat(5) { i ->
                        Text(
                            if (i < currentQuality) "★" else "☆",
                            color = if (i < currentQuality)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            fontSize = 20.sp
                        )
                    }
                    Text(" (${String.format("%.1f", currentDuration)}h)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（如：多梦、失眠）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 保存前自动格式化时间，使用自动计算的星级
                    val formattedBedTime = formatTimeInput(bedTime)
                    val formattedWakeTime = formatTimeInput(wakeTime)
                    val finalQuality = calculateQuality(calculateDuration(formattedBedTime, formattedWakeTime))
                    onConfirm(formattedBedTime, formattedWakeTime, finalQuality, note)
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
