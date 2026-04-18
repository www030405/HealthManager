package com.example.healthmanager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.data.entity.DietRecord
import com.example.healthmanager.ui.viewmodel.DietViewModel
import com.example.healthmanager.ui.viewmodel.ProfileViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen(navController: NavController) {
    val profileVm: ProfileViewModel = viewModel()
    val viewModel: DietViewModel = viewModel()
    val userId = profileVm.prefs.currentUserId

    LaunchedEffect(userId) { viewModel.init(userId) }

    val todayCalories by viewModel.todayCalories.collectAsState()
    val todayMacros by viewModel.todayMacros.collectAsState()
    val records by viewModel.todayRecords.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val user by profileVm.currentUser.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var showFoodReference by remember { mutableStateOf(false) }

    // 用于自动填入食物数据
    var foodName by remember { mutableStateOf("") }
    var foodAmount by remember { mutableStateOf("") }
    var foodCalories by remember { mutableStateOf("") }
    var foodProtein by remember { mutableStateOf("") }
    var foodFat by remember { mutableStateOf("") }
    var foodCarbs by remember { mutableStateOf("") }

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
                },
                actions = {
                    IconButton(onClick = { showFoodReference = true }) {
                        Icon(Icons.Default.Restaurant, contentDescription = "食物参考")
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

            // 三大营养素摄入饼图
            if (todayMacros.carbs + todayMacros.protein + todayMacros.fat > 0) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("今日饮食摘要", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                            MacroPieChart(
                                carbs = todayMacros.carbs,
                                protein = todayMacros.protein,
                                fat = todayMacros.fat
                            )
                        }
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
            onDismiss = { 
                showDialog = false
                // 清空临时数据
                foodName = ""
                foodAmount = ""
                foodCalories = ""
                foodProtein = ""
                foodFat = ""
                foodCarbs = ""
            },
            onConfirm = { mealType, fName, amount, calories, protein, fat, carbs, note ->
                viewModel.addRecord(mealType, fName, amount, calories, protein, fat, carbs, note)
                showDialog = false
            },
            initialFoodName = foodName,
            initialAmount = foodAmount,
            initialCalories = foodCalories,
            initialProtein = foodProtein,
            initialFat = foodFat,
            initialCarbs = foodCarbs
        )
    }

    // 保存后清空临时数据
    LaunchedEffect(showDialog) {
        if (!showDialog && foodName.isNotEmpty()) {
            foodName = ""
            foodAmount = ""
            foodCalories = ""
            foodProtein = ""
            foodFat = ""
            foodCarbs = ""
        }
    }

    if (showFoodReference) {
        FoodReferenceDialog(
            onDismiss = { showFoodReference = false },
            onFoodSelected = { food ->
                showFoodReference = false
                // 自动填入到添加对话框并打开
                foodName = food.name
                foodAmount = food.amount
                foodCalories = food.calories.toString()
                foodProtein = food.protein.toString()
                foodFat = food.fat.toString()
                foodCarbs = food.carbs.toString()
                showDialog = true
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
    onConfirm: (String, String, Float, Float, Float, Float, Float, String) -> Unit,
    initialFoodName: String = "",
    initialAmount: String = "",
    initialCalories: String = "",
    initialProtein: String = "",
    initialFat: String = "",
    initialCarbs: String = ""
) {
    val mealTypes = listOf("早餐", "午餐", "晚餐", "加餐")
    var selectedMeal by remember { mutableStateOf("早餐") }
    var foodName by remember { mutableStateOf(initialFoodName) }
    var amount by remember { mutableStateOf(initialAmount) }
    var calories by remember { mutableStateOf(initialCalories) }
    var protein by remember { mutableStateOf(initialProtein) }
    var fat by remember { mutableStateOf(initialFat) }
    var carbs by remember { mutableStateOf(initialCarbs) }
    var note by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var caloriesError by remember { mutableStateOf(false) }
    var proteinError by remember { mutableStateOf(false) }
    var fatError by remember { mutableStateOf(false) }
    var carbsError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

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
                OutlinedTextField(value = amount, onValueChange = { amount = it; amountError = false },
                    label = { Text("食用量（克）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = if (amountError) {{ Text("请输入0或正数") }} else null,
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = calories, onValueChange = { calories = it; caloriesError = false },
                    label = { Text("卡路里（kcal）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = caloriesError,
                    supportingText = if (caloriesError) {{ Text("请输入0或正数") }} else null,
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { protein = it; proteinError = false },
                        label = { Text("蛋白质g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = proteinError,
                        supportingText = if (proteinError) {{ Text("请输入0或正数") }} else null,
                        modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = fat, onValueChange = { fat = it; fatError = false },
                        label = { Text("脂肪g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = fatError,
                        supportingText = if (fatError) {{ Text("请输入0或正数") }} else null,
                        modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = carbs, onValueChange = { carbs = it; carbsError = false },
                        label = { Text("碳水g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = carbsError,
                        supportingText = if (carbsError) {{ Text("请输入0或正数") }} else null,
                        modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 验证输入
                val amountVal = amount.toFloatOrNull()
                val caloriesVal = calories.toFloatOrNull()
                val proteinVal = protein.toFloatOrNull()
                val fatVal = fat.toFloatOrNull()
                val carbsVal = carbs.toFloatOrNull()

                amountError = amount.isNotEmpty() && (amountVal == null || amountVal < 0)
                caloriesError = calories.isNotEmpty() && (caloriesVal == null || caloriesVal < 0)
                proteinError = protein.isNotEmpty() && (proteinVal == null || proteinVal < 0)
                fatError = fat.isNotEmpty() && (fatVal == null || fatVal < 0)
                carbsError = carbs.isNotEmpty() && (carbsVal == null || carbsVal < 0)

                if (amountError || caloriesError || proteinError || fatError || carbsError) {
                    errorMessage = "请重新输入"
                    return@TextButton
                }

                if (foodName.isNotBlank()) {
                    onConfirm(selectedMeal, foodName,
                        amountVal ?: 0f,
                        caloriesVal ?: 0f,
                        proteinVal ?: 0f,
                        fatVal ?: 0f,
                        carbsVal ?: 0f,
                        note)
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// 常见食物参考数据（每100g）
private val commonFoods = listOf(
    FoodItem("米饭", "100g", 116, 2.6f, 0.3f, 25.9f),
    FoodItem("面条", "100g", 109, 3.7f, 1.2f, 24.2f),
    FoodItem("馒头", "100g", 223, 7.8f, 1.0f, 47.0f),
    FoodItem("面包", "100g", 265, 9.0f, 3.2f, 49.0f),
    FoodItem("鸡蛋", "1个(50g)", 72, 6.3f, 5.0f, 0.4f),
    FoodItem("鸡胸肉", "100g", 165, 31.0f, 3.6f, 0f),
    FoodItem("牛肉", "100g", 250, 26.0f, 15.0f, 0f),
    FoodItem("鱼肉", "100g", 113, 20.0f, 2.6f, 0f),
    FoodItem("牛奶", "100g", 54, 3.2f, 2.5f, 5.0f),
    FoodItem("酸奶", "100g", 72, 3.3f, 2.5f, 9.3f),
    FoodItem("苹果", "1个(180g)", 95, 0.3f, 0.2f, 25.1f),
    FoodItem("香蕉", "1根(120g)", 105, 1.3f, 0.4f, 27.0f),
    FoodItem("橙子", "1个(130g)", 62, 1.2f, 0.2f, 15.4f),
    FoodItem("西瓜", "100g", 30, 0.6f, 0.1f, 7.6f),
    FoodItem("草莓", "100g", 32, 0.7f, 0.3f, 7.7f),
    FoodItem("胡萝卜", "100g", 41, 0.9f, 0.2f, 9.6f),
    FoodItem("西兰花", "100g", 34, 2.8f, 0.4f, 6.6f),
    FoodItem("番茄", "100g", 18, 0.9f, 0.2f, 3.9f),
    FoodItem("土豆", "100g", 77, 2.0f, 0.1f, 17.5f),
    FoodItem("豆腐", "100g", 76, 8.1f, 3.7f, 2.7f),
    FoodItem("花生", "100g", 589, 26.0f, 50.0f, 16.0f),
    FoodItem("核桃", "100g", 654, 15.0f, 65.0f, 14.0f),
    FoodItem("可乐", "100g", 45, 0f, 0f, 11.3f),
    FoodItem("奶茶", "100g", 67, 2.0f, 3.5f, 8.0f),
)

private data class FoodItem(
    val name: String,
    val amount: String,
    val calories: Int,
    val protein: Float,
    val fat: Float,
    val carbs: Float
)

@Composable
private fun FoodReferenceDialog(
    onDismiss: () -> Unit,
    onFoodSelected: (FoodItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("常见食物参考", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "点击食物可直接填入",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(commonFoods) { food ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onFoodSelected(food) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(food.name, fontWeight = FontWeight.Medium)
                                Text(food.amount, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${food.calories} kcal",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "蛋白质 ${food.protein}g | 脂肪 ${food.fat}g | 碳水 ${food.carbs}g",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun MacroPieChart(carbs: Float, protein: Float, fat: Float) {
    val total = carbs + protein + fat
    if (total <= 0) return

    val entries = listOf(
        PieEntry(carbs, "碳水 ${String.format("%.1f", carbs)}g"),
        PieEntry(protein, "蛋白质 ${String.format("%.1f", protein)}g"),
        PieEntry(fat, "脂肪 ${String.format("%.1f", fat)}g")
    )

    val colors = listOf(
        Color(0xFF4CAF50).toArgb(),
        Color(0xFF2196F3).toArgb(),
        Color(0xFFFF9800).toArgb()
    )

    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                isDrawHoleEnabled = true
                holeRadius = 50f
                setUsePercentValues(true)
                setEntryLabelTextSize(10f)
                legend.isEnabled = true
                legend.textSize = 10f
            }
        },
        update = { chart ->
            val dataSet = PieDataSet(entries, "三大营养素").apply {
                setColors(colors)
                sliceSpace = 2f
                valueTextSize = 10f
                valueTextColor = android.graphics.Color.WHITE
            }
            chart.data = PieData(dataSet)
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}
