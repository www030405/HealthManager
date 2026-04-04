package com.example.healthmanager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.ui.viewmodel.ExerciseViewModel
import com.example.healthmanager.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(navController: NavController) {
    val profileVm: ProfileViewModel = viewModel()
    val viewModel: ExerciseViewModel = viewModel()
    val userId = profileVm.prefs.currentUserId

    LaunchedEffect(userId) { viewModel.init(userId) }

    val todaySteps by viewModel.todaySteps.collectAsState()
    val sensorSteps by viewModel.sensorSteps.collectAsState()
    val sensorCalories by viewModel.sensorCalories.collectAsState()
    val todayCalories by viewModel.todayCalories.collectAsState()
    val records by viewModel.todayRecords.collectAsState()
    // 传感器实时步数 + 手动记录步数
    val totalSteps = sensorSteps + todaySteps
    val totalCalories = sensorCalories + todayCalories
    val saveResult by viewModel.saveResult.collectAsState()

    var showDialog by remember { mutableStateOf(false) }

    // 保存结果提示
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
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加记录")
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
            // 今日统计
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("今日步数", fontSize = 12.sp)
                            Text("$totalSteps", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("步", fontSize = 12.sp)
                            if (sensorSteps > 0) {
                                Text(
                                    "传感器: $sensorSteps",
                                    fontSize = 10.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.height(60.dp).width(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("消耗热量", fontSize = 12.sp)
                            Text(String.format("%.0f", totalCalories), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("kcal", fontSize = 12.sp)
                            if (sensorCalories > 0) {
                                Text(
                                    "传感器: ${String.format("%.0f", sensorCalories)}",
                                    fontSize = 10.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            item { Text("今日运动记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

            if (records.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        Text("暂无记录，点击右下角 + 添加", color = MaterialTheme.colorScheme.outline)
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

@Composable
private fun ExerciseRecordItem(record: ExerciseRecord, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.exerciseType.ifBlank { "运动" }, fontWeight = FontWeight.SemiBold)
                Text(
                    "${record.steps}步 · ${record.durationMinutes}分钟 · ${
                        String.format("%.1f", record.caloriesBurned)
                    }kcal",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.note.isNotBlank()) {
                    Text(record.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Float, String) -> Unit
) {
    val exerciseTypes = listOf("步行", "跑步", "骑行", "游泳", "其他")
    var selectedType by remember { mutableStateOf("步行") }
    var steps by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加运动记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 运动类型下拉
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("运动类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
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
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("步数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
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
                    steps.toIntOrNull() ?: 0,
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
