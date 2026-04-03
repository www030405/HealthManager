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
import com.example.healthmanager.data.entity.DietRecord
import com.example.healthmanager.ui.viewmodel.DietViewModel
import com.example.healthmanager.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen(navController: NavController) {
    val profileVm: ProfileViewModel = viewModel()
    val viewModel: DietViewModel = viewModel()
    val userId = profileVm.prefs.currentUserId

    LaunchedEffect(userId) { viewModel.init(userId) }

    val todayCalories by viewModel.todayCalories.collectAsState()
    val records by viewModel.todayRecords.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val user by profileVm.currentUser.collectAsState()

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
                title = { Text("饮食管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加饮食")
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
            // 卡路里摄入进度
            item {
                val target = (user?.targetCalories ?: 2000).toFloat()
                val progress = (todayCalories / target).coerceIn(0f, 1f)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("今日摄入", fontWeight = FontWeight.SemiBold)
                            Text("${String.format("%.0f", todayCalories)} / ${target.toInt()} kcal")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (progress > 1f) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (progress >= 1f) "已超出每日热量目标"
                                   else "还可摄入 ${(target - todayCalories).toInt()} kcal",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 按餐次分组显示
            val mealTypes = listOf("早餐", "午餐", "晚餐", "加餐")
            mealTypes.forEach { mealType ->
                val mealRecords = records.filter { it.mealType == mealType }
                if (mealRecords.isNotEmpty()) {
                    item {
                        Text(
                            text = "$mealType（${String.format("%.0f", mealRecords.sumOf { it.calories.toDouble() }.toFloat())} kcal）",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(mealRecords, key = { it.id }) { record ->
                        DietRecordItem(record = record, onDelete = { viewModel.deleteRecord(record) })
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        Text("暂无饮食记录，点击右下角 + 添加", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddDietDialog(
            onDismiss = { showDialog = false },
            onConfirm = { mealType, foodName, amount, calories, protein, fat, carbs, note ->
                viewModel.addRecord(mealType, foodName, amount, calories, protein, fat, carbs, note)
                showDialog = false
            }
        )
    }
}

@Composable
private fun DietRecordItem(record: DietRecord, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.foodName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${record.amount}g · ${String.format("%.0f", record.calories)}kcal · " +
                    "蛋白质${String.format("%.1f", record.protein)}g",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun AddDietDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Float, Float, Float, Float, Float, String) -> Unit
) {
    val mealTypes = listOf("早餐", "午餐", "晚餐", "加餐")
    var selectedMeal by remember { mutableStateOf("早餐") }
    var foodName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加饮食记录") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedMeal,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("餐次") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        mealTypes.forEach { meal ->
                            DropdownMenuItem(
                                text = { Text(meal) },
                                onClick = { selectedMeal = meal; expanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(value = foodName, onValueChange = { foodName = it },
                    label = { Text("食物名称 *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = amount, onValueChange = { amount = it },
                    label = { Text("食用量（克）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = calories, onValueChange = { calories = it },
                    label = { Text("卡路里（kcal）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { protein = it },
                        label = { Text("蛋白质g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = fat, onValueChange = { fat = it },
                        label = { Text("脂肪g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = carbs, onValueChange = { carbs = it },
                        label = { Text("碳水g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (foodName.isNotBlank()) {
                    onConfirm(selectedMeal, foodName,
                        amount.toFloatOrNull() ?: 0f,
                        calories.toFloatOrNull() ?: 0f,
                        protein.toFloatOrNull() ?: 0f,
                        fat.toFloatOrNull() ?: 0f,
                        carbs.toFloatOrNull() ?: 0f,
                        note)
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
