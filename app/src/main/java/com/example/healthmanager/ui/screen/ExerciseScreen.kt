package com.example.healthmanager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.ui.viewmodel.ExerciseType
import com.example.healthmanager.ui.viewmodel.ExerciseViewModel
import com.example.healthmanager.ui.viewmodel.ProfileViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(navController: NavController) {
    val profileVm: ProfileViewModel = viewModel()
    val viewModel: ExerciseViewModel = viewModel()
    val userId = profileVm.prefs.currentUserId

    LaunchedEffect(userId) { viewModel.init(userId) }

    val selectedType by viewModel.selectedType.collectAsState()
    val isExercising by viewModel.isExercising.collectAsState()
    val sessionSeconds by viewModel.sessionSeconds.collectAsState()
    val sessionSteps by viewModel.sessionSteps.collectAsState()
    val sessionCalories by viewModel.sessionCalories.collectAsState()
    val todayCalories by viewModel.todayCalories.collectAsState()
    val todaySteps by viewModel.todaySteps.collectAsState()
    val records by viewModel.todayRecords.collectAsState()
    val gaitResult by viewModel.gaitResult.collectAsState()
    val usingCnn by viewModel.usingCnn.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(saveResult) {
        if (saveResult != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveResult()
        }
    }

    val mergedRecords = remember(records, selectedType) {
        if (selectedType == ExerciseType.ALL) {
            mergeRecordsByType(records)
        } else {
            records
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运动") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "选择日期")
                    }
                }
            )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DateDisplayCard(
                    selectedDate = selectedDate,
                    onClick = { showDatePicker = true }
                )
            }

            item {
                ExerciseTypeTabBar(
                    selectedType = selectedType,
                    onTypeSelected = { viewModel.selectType(it) },
                    isExercising = isExercising
                )
            }

            if (selectedType != ExerciseType.ALL) {
                item {
                    ExerciseSessionPanel(
                        exerciseType = selectedType,
                        isExercising = isExercising,
                        sessionSeconds = sessionSeconds,
                        sessionSteps = sessionSteps,
                        sessionCalories = sessionCalories,
                        gaitLabel = gaitResult?.label,
                        gaitConfidence = gaitResult?.confidence,
                        usingCnn = usingCnn,
                        onStart = { viewModel.startExercise() },
                        onStop = { viewModel.stopExercise() }
                    )
                }
            }

            if (selectedType != ExerciseType.ALL) {
                item {
                    TodaySummaryCard(
                        totalSteps = todaySteps,
                        totalCalories = todayCalories,
                        exerciseType = selectedType.label
                    )
                }
            }

            item {
                Text(
                    text = if (selectedType == ExerciseType.ALL) "今日运动" else "${selectedType.label}记录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (mergedRecords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无记录", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            items(mergedRecords, key = { it.id }) { record ->
                ExerciseRecordItem(
                    record = record,
                    onDelete = {
                        if (record.note.contains("条记录")) {
                            val typeToDelete = record.exerciseType
                            records.filter { it.exerciseType == typeToDelete }.forEach {
                                viewModel.deleteRecord(it)
                            }
                        } else {
                            viewModel.deleteRecord(record)
                        }
                    }
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(selectedDate)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis ->
                            val newDate = java.time.Instant.ofEpochMilli(dateMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                                .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            viewModel.setDate(newDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text("选择日期") },
                showModeToggle = false
            )
        }
    }
}

private fun mergeRecordsByType(records: List<ExerciseRecord>): List<ExerciseRecord> {
    val grouped = records.groupBy { "${it.date}_${it.exerciseType}" }
    return grouped.map { (key, typeRecords) ->
        if (typeRecords.size == 1) {
            typeRecords.first()
        } else {
            val parts = key.split("_")
            ExerciseRecord(
                id = typeRecords.minOf { it.id },
                userId = typeRecords.first().userId,
                date = parts[0],
                steps = typeRecords.sumOf { it.steps.toLong() }.toInt(),
                caloriesBurned = typeRecords.sumOf { it.caloriesBurned.toDouble() }.toFloat(),
                exerciseType = parts[1],
                durationMinutes = typeRecords.sumOf { it.durationMinutes.toLong() }.toInt(),
                distanceKm = typeRecords.sumOf { it.distanceKm.toDouble() }.toFloat(),
                note = "${typeRecords.size}条记录合并",
                createdAt = typeRecords.first().createdAt
            )
        }
    }.sortedByDescending { it.createdAt }
}

@Composable
private fun DateDisplayCard(selectedDate: String, onClick: () -> Unit) {
    val displayDate = try {
        val date = LocalDate.parse(selectedDate)
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        
        when {
            date == today -> "今天"
            date == yesterday -> "昨天"
            else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        }
    } catch (e: Exception) {
        selectedDate
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📅 $displayDate",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ExerciseTypeTabBar(
    selectedType: ExerciseType,
    onTypeSelected: (ExerciseType) -> Unit,
    isExercising: Boolean
) {
    ScrollableTabRow(
        selectedTabIndex = ExerciseType.entries.indexOf(selectedType),
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        ExerciseType.entries.forEach { type ->
            Tab(
                selected = type == selectedType,
                onClick = { if (!isExercising) onTypeSelected(type) },
                enabled = !isExercising,
                text = { Text(type.label) },
                icon = {
                    Icon(
                        imageVector = typeIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

private fun typeIcon(type: ExerciseType): ImageVector = when (type) {
    ExerciseType.ALL -> Icons.Default.List
    ExerciseType.WALKING -> Icons.Default.DirectionsWalk
    ExerciseType.RUNNING -> Icons.Default.DirectionsRun
    ExerciseType.STAIR_CLIMBING -> Icons.Default.Stairs
    ExerciseType.CYCLING -> Icons.Default.DirectionsBike
    ExerciseType.SWIMMING -> Icons.Default.Pool
}

@Composable
private fun ExerciseSessionPanel(
    exerciseType: ExerciseType,
    isExercising: Boolean,
    sessionSeconds: Long,
    sessionSteps: Int,
    sessionCalories: Float,
    gaitLabel: String?,
    gaitConfidence: Float?,
    usingCnn: Boolean,
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
            Text(
                text = if (isExercising) "${exerciseType.label}中..." else "准备${exerciseType.label}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = formatTime(sessionSeconds),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = if (isExercising) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                when (exerciseType) {
                    ExerciseType.WALKING, ExerciseType.RUNNING, ExerciseType.STAIR_CLIMBING -> {
                        SessionMetric(label = "步数", value = "$sessionSteps", unit = "步")
                    }
                    ExerciseType.CYCLING, ExerciseType.SWIMMING -> {
                        val distance = sessionSteps * 0.7f / 1000f
                        SessionMetric(label = "距离", value = String.format("%.2f", distance), unit = "km")
                    }
                    else -> {}
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

            if (exerciseType.needsSteps && isExercising && gaitLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CNN / 规则分类器标识
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (usingCnn) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (usingCnn) "1D CNN" else "规则分类",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = if (usingCnn) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 步态识别结果 + 置信度
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "步态: $gaitLabel ${String.format("%.0f%%", (gaitConfidence ?: 0f) * 100)}",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isExercising) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始${exerciseType.label}", fontSize = 16.sp)
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

@Composable
private fun TodaySummaryCard(totalSteps: Int, totalCalories: Float, exerciseType: String) {
    val isDistanceType = exerciseType in listOf("骑行", "游泳")
    val displayValue = if (isDistanceType) {
        String.format("%.2f", totalSteps * 0.7f / 1000f)
    } else {
        "$totalSteps"
    }
    val unit = if (isDistanceType) "km" else "步"
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("步数", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(displayValue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("热量", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format("%.0f", totalCalories), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("kcal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExerciseRecordItem(record: ExerciseRecord, onDelete: () -> Unit) {
    val needsSteps = record.exerciseType in listOf("走路", "跑步", "上楼梯")

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (record.exerciseType) {
                    "走路" -> Icons.Default.DirectionsWalk
                    "跑步" -> Icons.Default.DirectionsRun
                    "上楼梯" -> Icons.Default.Stairs
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
                    if (record.note.contains("条记录")) {
                        append(" · ")
                        append(record.note)
                    }
                }
                Text(
                    details,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (record.note.isNotBlank() && !record.note.contains("条记录")) {
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
