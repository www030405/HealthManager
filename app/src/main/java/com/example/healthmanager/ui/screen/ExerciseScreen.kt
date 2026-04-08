package com.example.healthmanager.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.ui.viewmodel.ExerciseMode
import com.example.healthmanager.ui.viewmodel.ExerciseViewModel
import com.example.healthmanager.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(navController: NavController) {
    val profileVm: ProfileViewModel = viewModel()
    val viewModel: ExerciseViewModel = viewModel()
    val userId = profileVm.prefs.currentUserId

    LaunchedEffect(userId) { viewModel.init(userId) }

    val currentMode by viewModel.currentMode.collectAsState()
    val isExercising by viewModel.isExercising.collectAsState()
    val sessionSeconds by viewModel.sessionSeconds.collectAsState()
    val sessionSteps by viewModel.sessionSteps.collectAsState()
    val sessionCalories by viewModel.sessionCalories.collectAsState()
    val sensorSteps by viewModel.sensorSteps.collectAsState()
    val todayCalories by viewModel.todayCalories.collectAsState()
    val todaySteps by viewModel.todaySteps.collectAsState()
    val records by viewModel.todayRecords.collectAsState()
    val gaitResult by viewModel.gaitResult.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(saveResult) {
        if (saveResult != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运动记录") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isExercising) {
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "手动添加")
                }
            }
        },
        snackbarHost = {
            saveResult?.let {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ========== 运动模式选择 ==========
            item {
                Text("选择运动方式", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            item {
                ExerciseModeSelector(
                    currentMode = currentMode,
                    isExercising = isExercising,
                    onModeSelected = { viewModel.switchMode(it) }
                )
            }

            // ========== 运动面板 ==========
            item {
                ExerciseSessionPanel(
                    currentMode = currentMode,
                    isExercising = isExercising,
                    sessionSeconds = sessionSeconds,
                    sessionSteps = sessionSteps,
                    sessionCalories = sessionCalories,
                    gaitLabel = gaitResult?.label,
                    onStart = { viewModel.startExercise() },
                    onStop = { viewModel.stopExercise() }
                )
            }

            // ========== 今日汇总 ==========
            item {
                TodaySummaryCard(
                    totalSteps = sensorSteps + todaySteps,
                    totalCalories = todayCalories,
                    sensorSteps = sensorSteps
                )
            }

            // ========== 运动记录列表 ==========
            item {
                Text("今日运动记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            if (records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无记录，选择运动方式后开始运动", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            items(records, key = { it.id }) { record ->
                ExerciseRecordItem(record = record, onDelete = { viewModel.deleteRecord(record) })
            }
        }
    }

    if (showDialog) {
        AddExerciseDialog(
            onDismiss = { showDialog = false },
            onConfirm = { type, steps, duration, distance, note ->
                viewModel.addRecord(type, steps, duration, distance, note)
                showDialog = false
            }
        )
    }
}

// ==================== 运动模式选择器 ====================

@Composable
private fun ExerciseModeSelector(
    currentMode: ExerciseMode,
    isExercising: Boolean,
    onModeSelected: (ExerciseMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExerciseMode.entries.forEach { mode ->
            val selected = mode == currentMode
            val containerColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                label = "modeColor"
            )
            Card(
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(mode) },
                enabled = !isExercising,
                colors = CardDefaults.cardColors(containerColor = containerColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = modeIcon(mode),
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        mode.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun modeIcon(mode: ExerciseMode): ImageVector = when (mode) {
    ExerciseMode.WALKING -> Icons.Default.DirectionsWalk
    ExerciseMode.RUNNING -> Icons.Default.DirectionsRun
    ExerciseMode.CYCLING -> Icons.Default.DirectionsBike
    ExerciseMode.SWIMMING -> Icons.Default.Pool
}

// ==================== 运动会话面板 ====================

@Composable
private fun ExerciseSessionPanel(
    currentMode: ExerciseMode,
    isExercising: Boolean,
    sessionSeconds: Long,
    sessionSteps: Int,
    sessionCalories: Float,
    gaitLabel: String?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isExercising) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 当前模式标题
            Text(
                text = if (isExercising) "${currentMode.label}中..." else "准备${currentMode.label}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 计时器
            Text(
                text = formatTime(sessionSeconds),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = if (isExercising) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 运动数据指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 走路/跑步模式显示步数
                if (currentMode.needsSteps) {
                    SessionMetric(label = "步数", value = "$sessionSteps", unit = "步")
                }
                SessionMetric(
                    label = "热量",
                    value = String.format("%.1f", sessionCalories),
                    unit = "kcal"
                )
                SessionMetric(
                    label = "时长",
                    value = "${sessionSeconds / 60}",
                    unit = "分钟"
                )
            }

            // 走路/跑步模式下显示步态识别
            if (currentMode.needsSteps && isExercising && gaitLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "步态识别: $gaitLabel",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 开始 / 结束按钮
            if (!isExercising) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始${currentMode.label}", fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("结束运动", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SessionMetric(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

// ==================== 今日汇总 ====================

@Composable
private fun TodaySummaryCard(totalSteps: Int, totalCalories: Float, sensorSteps: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("今日总步数", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$totalSteps", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("步", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("今日总热量", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format("%.0f", totalCalories), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("kcal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ==================== 运动记录列表项 ====================

@Composable
private fun ExerciseRecordItem(record: ExerciseRecord, onDelete: () -> Unit) {
    val needsSteps = record.exerciseType in listOf("步行", "跑步")

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 运动类型图标
            Icon(
                imageVector = when (record.exerciseType) {
                    "步行" -> Icons.Default.DirectionsWalk
                    "跑步" -> Icons.Default.DirectionsRun
                    "骑行" -> Icons.Default.DirectionsBike
                    "游泳" -> Icons.Default.Pool
                    else -> Icons.Default.FitnessCenter
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(record.exerciseType.ifBlank { "运动" }, fontWeight = FontWeight.SemiBold)

                // 根据运动类型显示不同数据
                val details = buildString {
                    if (needsSteps && record.steps > 0) {
                        append("${record.steps}步")
                        append(" · ")
                    }
                    if (record.durationMinutes > 0) {
                        append("${record.durationMinutes}分钟")
                        append(" · ")
                    }
                    if (record.distanceKm > 0) {
                        append(String.format("%.1fkm", record.distanceKm))
                        append(" · ")
                    }
                    append(String.format("%.1fkcal", record.caloriesBurned))
                }
                Text(
                    details,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (record.note.isNotBlank()) {
                    Text(record.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ==================== 手动添加对话框 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Float, String) -> Unit
) {
    val exerciseTypes = listOf("步行", "跑步", "骑行", "游泳", "其他")
    var selectedType by remember { mutableStateOf("步行") }
    val needsSteps = selectedType in listOf("步行", "跑步")
    var steps by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加运动记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("运动类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        exerciseTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = { selectedType = type; expanded = false }
                            )
                        }
                    }
                }
                // 只有步行/跑步才显示步数输入
                if (needsSteps) {
                    OutlinedTextField(
                        value = steps,
                        onValueChange = { steps = it },
                        label = { Text("步数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("时长（分钟）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = distance,
                    onValueChange = { distance = it },
                    label = { Text("距离（公里，选填）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（选填）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    selectedType,
                    if (needsSteps) steps.toIntOrNull() ?: 0 else 0,
                    duration.toIntOrNull() ?: 0,
                    distance.toFloatOrNull() ?: 0f,
                    note
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
