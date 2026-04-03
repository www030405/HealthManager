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
                                Text("睡眠质量：", fontSize = 13.sp)
                                repeat(5) { i ->
                                    Text(
                                        if (i < record.quality) "★" else "☆",
                                        color = if (i < record.quality)
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
                Row {
                    repeat(5) { i ->
                        Text(
                            if (i < record.quality) "★" else "☆",
                            color = if (i < record.quality)
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
                Text("睡眠质量：$quality 星", fontSize = 14.sp)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3
                )
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
            TextButton(onClick = { onConfirm(bedTime, wakeTime, quality, note) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
