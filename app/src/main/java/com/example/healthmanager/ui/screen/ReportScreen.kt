package com.example.healthmanager.ui.screen

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.health.HealthScore
import com.example.healthmanager.health.HealthScoreEngine
import com.example.healthmanager.ui.viewmodel.DietViewModel
import com.example.healthmanager.ui.viewmodel.ExerciseViewModel
import com.example.healthmanager.ui.viewmodel.HealthScoreViewModel
import com.example.healthmanager.ui.viewmodel.ProfileViewModel
import com.example.healthmanager.ui.viewmodel.SleepViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(navController: NavController) {
    val profileVm: ProfileViewModel = viewModel()
    val exerciseVm: ExerciseViewModel = viewModel()
    val sleepVm: SleepViewModel = viewModel()
    val dietVm: DietViewModel = viewModel()
    val scoreVm: HealthScoreViewModel = viewModel()
    val userId = profileVm.prefs.currentUserId
    val user by profileVm.currentUser.collectAsState()

    LaunchedEffect(userId) {
        exerciseVm.init(userId)
        sleepVm.init(userId)
        dietVm.init(userId)
    }

    LaunchedEffect(user) {
        scoreVm.init(
            userId = userId,
            targetSteps = user?.targetSteps ?: 8000,
            targetCalories = user?.targetCalories ?: 2000
        )
    }

    val sensorSteps by exerciseVm.sensorSteps.collectAsState()
    val weekExercise by exerciseVm.weekRecords.collectAsState()
    val weekSleep by sleepVm.weekRecords.collectAsState()
    val avgSleep by sleepVm.avgDuration.collectAsState()
    val healthScore by scoreVm.healthScore.collectAsState()

    // 近7天日期标签
    val dates = (6 downTo 0).map {
        LocalDate.now().minusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val dayLabels = dates.map { it.substring(5) }
    val todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    // 今日步数：优先使用传感器实时数据
    val displayTodaySteps = if (sensorSteps > 0) sensorSteps
        else weekExercise.filter { it.date == todayDate }.sumOf { it.steps }

    // 按日期聚合步数：过去的天使用数据库归档记录，今天使用传感器实时步数
    val stepsByDate = dates.map { date ->
        if (date == todayDate) {
            displayTodaySteps.toFloat()
        } else {
            weekExercise.filter { it.date == date }.sumOf { it.steps }.toFloat()
        }
    }

    // 按日期聚合睡眠时长
    val sleepByDate = dates.map { date ->
        weekSleep.filter { it.date == date }.sumOf { it.durationHours.toDouble() }.toFloat()
    }

    // 获取上周数据
    val lastWeekDates = (13 downTo 7).map {
        LocalDate.now().minusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val lastWeekSteps = lastWeekDates.map { date ->
        weekExercise.filter { it.date == date }.sumOf { it.steps }.toFloat()
    }
    val lastWeekSleep = lastWeekDates.map { date ->
        weekSleep.filter { it.date == date }.sumOf { it.durationHours.toDouble() }.toFloat()
    }

    // 运动类型分布（本周）- 统计走路/跑步/上楼梯/骑行/游泳
    val exerciseTypes = listOf("走路", "跑步", "上楼梯", "骑行", "游泳")
    val exerciseTypeDistribution = weekExercise
        .filter { it.exerciseType in exerciseTypes }
        .groupBy { it.exerciseType }
        .mapValues { (_, records) -> records.sumOf { it.steps }.toFloat() }
    val exerciseTypeCalories = weekExercise
        .filter { it.exerciseType in exerciseTypes }
        .groupBy { it.exerciseType }
        .mapValues { (_, records) -> records.sumOf { it.caloriesBurned.toDouble() }.toFloat() }

    // 健康评分趋势（本周每日计算）
    val scoreTrend = dates.mapIndexed { i, date ->
        // 今日使用传感器步数，历史天使用数据库
        val daySteps = if (date == todayDate && sensorSteps > 0) {
            sensorSteps
        } else {
            weekExercise.filter { it.date == date }.sumOf { it.steps }
        }
        
        val daySleep = weekSleep.filter { it.date == date }.firstOrNull()
        val dayDiet = dietVm.weekRecords.value.filter { it.date == date }
        
        // 使用用户目标
        val targetSteps = user?.targetSteps ?: 8000
        val targetCalories = user?.targetCalories ?: 2000
        
        val exerciseScore = HealthScoreEngine.calculateExerciseScore(
            todaySteps = daySteps,
            targetSteps = targetSteps,
            todayExerciseMinutes = weekExercise.filter { it.date == date }.sumOf { it.durationMinutes },
            weekActiveDays = 1
        )
        val sleepScore = HealthScoreEngine.calculateSleepScore(
            durationHours = daySleep?.durationHours ?: 0f,
            quality = daySleep?.quality ?: 0,  // 与首页一致，null时用0
            hasRecord = daySleep != null
        )
        val dietScore = HealthScoreEngine.calculateDietScore(
            totalCalories = dayDiet.sumOf { it.calories.toDouble() }.toFloat(),
            targetCalories = targetCalories,
            mealCount = dayDiet.map { it.mealType }.distinct().count { it in listOf("早餐", "午餐", "晚餐") },
            hasRecord = dayDiet.isNotEmpty()
        )
        
        HealthScoreEngine.calculateTotalScore(exerciseScore, sleepScore, dietScore)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康报告") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 本周统计概览
            WeekSummaryCard(
                todaySteps = displayTodaySteps,
                totalSteps = stepsByDate.sum().toInt(),
                activeDays = weekExercise.groupBy { it.date }.size,
                avgSleep = avgSleep
            )

            // 步数柱状图（MPAndroidChart）
            Text("近7天步数", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Card(modifier = Modifier.fillMaxWidth()) {
                StepsBarChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp),
                    values = stepsByDate,
                    labels = dayLabels,
                    targetSteps = 8000f
                )
            }

            // 睡眠折线图（MPAndroidChart）
            Text("近7天睡眠时长（小时）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Card(modifier = Modifier.fillMaxWidth()) {
                SleepLineChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp),
                    values = sleepByDate,
                    labels = dayLabels
                )
            }

            // 运动类型分布饼图
            Text("本周运动类型分布", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Card(modifier = Modifier.fillMaxWidth()) {
                if (exerciseTypeDistribution.isNotEmpty() && exerciseTypeDistribution.values.any { it > 0 }) {
                    ExerciseTypePieChart(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(8.dp),
                        typeSteps = exerciseTypeDistribution,
                        typeCalories = exerciseTypeCalories
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无运动数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 健康评分趋势图
            Text("本周健康评分趋势", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Card(modifier = Modifier.fillMaxWidth()) {
                ScoreTrendChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp),
                    values = scoreTrend,
                    labels = dayLabels
                )
            }

            // 周对比：本周 vs 上周
            Text("本周 vs 上周对比", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Card(modifier = Modifier.fillMaxWidth()) {
                WeekComparisonCard(
                    thisWeekSteps = stepsByDate.sum().toInt(),
                    lastWeekSteps = lastWeekSteps.sum().toInt(),
                    thisWeekSleep = avgSleep,
                    lastWeekSleep = lastWeekSleep.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
                )
            }

            // 个性化健康建议（基于评分引擎生成）- 最下方
            HealthAdviceCard(healthScore = healthScore)
        }
    }
}

@Composable
private fun WeekSummaryCard(todaySteps: Int, totalSteps: Int, activeDays: Int, avgSleep: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("本周健康概览", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SummaryStatItem("今日步数", "$todaySteps", "步")
                SummaryStatItem("总步数", "$totalSteps", "步")
                SummaryStatItem("平均睡眠", String.format("%.1f", avgSleep), "小时")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val target = 8000
                val progress = (todaySteps.toFloat() / target * 100).coerceAtMost(100f)
                Text(
                    text = "今日目标完成率: ${progress.toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryStatItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 步数柱状图 - 使用 MPAndroidChart BarChart
 * 通过 AndroidView 在 Compose 中嵌入传统 View
 */
@Composable
private fun StepsBarChart(
    modifier: Modifier = Modifier,
    values: List<Float>,
    labels: List<String>,
    targetSteps: Float = 8000f
) {
    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                isHighlightPerTapEnabled = false
                setPinchZoom(false)
                setScaleEnabled(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter(labels.toTypedArray())
                    textSize = 10f
                }
                axisLeft.apply {
                    setDrawGridLines(true)
                    axisMinimum = 0f
                    textSize = 10f
                    // 添加目标线
                    addLimitLine(
                        com.github.mikephil.charting.components.LimitLine(
                            targetSteps,
                            "目标 ${targetSteps.toInt()}步"
                        ).apply {
                            lineColor = AndroidColor.RED
                            lineWidth = 1f
                            textColor = AndroidColor.RED
                            textSize = 9f
                        }
                    )
                }
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
            val dataSet = BarDataSet(entries, "步数").apply {
                color = AndroidColor.parseColor("#2E7D32") // 绿色
                setDrawValues(true)
                valueTextSize = 9f
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        if (value > 0) value.toInt().toString() else ""
                }
            }
            chart.data = BarData(dataSet).apply { barWidth = 0.6f }
            chart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * 睡眠折线图 - 使用 MPAndroidChart LineChart
 */
@Composable
private fun SleepLineChart(
    modifier: Modifier = Modifier,
    values: List<Float>,
    labels: List<String>
) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)
                isHighlightPerTapEnabled = false
                setPinchZoom(false)
                setScaleEnabled(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter(labels.toTypedArray())
                    textSize = 10f
                }
                axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = 12f
                    textSize = 10f
                    // 8小时推荐睡眠线
                    addLimitLine(
                        com.github.mikephil.charting.components.LimitLine(8f, "推荐8h").apply {
                            lineColor = AndroidColor.parseColor("#00796B")
                            lineWidth = 1f
                            enableDashedLine(10f, 10f, 0f)
                            textColor = AndroidColor.parseColor("#00796B")
                            textSize = 9f
                        }
                    )
                }
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v) }
            val dataSet = LineDataSet(entries, "睡眠时长").apply {
                color = AndroidColor.parseColor("#00796B")
                setCircleColor(AndroidColor.parseColor("#00796B"))
                circleRadius = 4f
                lineWidth = 2f
                setDrawFilled(true)
                fillColor = AndroidColor.parseColor("#B2DFDB")
                fillAlpha = 100
                setDrawValues(true)
                valueTextSize = 9f
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        if (value > 0) String.format("%.1f", value) else ""
                }
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * 多维度健康评分卡片
 * 展示总分、各维度得分条、评分等级
 */
@Composable
private fun HealthScoreSection(healthScore: HealthScore) {
    Text("多维度健康评分", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 总分 + 等级
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 大号总分
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.0f", healthScore.totalScore),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "综合评分",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // 等级 + 维度分数
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${healthScore.level.emoji} ${healthScore.level.label}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "运动40% + 睡眠30% + 饮食30%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 三维度得分
                    ScoreBar("运动", healthScore.exerciseScore, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    ScoreBar("睡眠", healthScore.sleepScore, MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    ScoreBar("饮食", healthScore.dietScore, MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(
    label: String,
    score: Float,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            modifier = Modifier.width(30.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = String.format("%.0f", score),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(26.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/**
 * 个性化健康建议（基于 HealthScoreEngine 评分结果生成）
 */
@Composable
private fun HealthAdviceCard(healthScore: HealthScore) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("个性化健康建议", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))

            healthScore.advices.forEach { advice ->
                Text(
                    text = "• $advice",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * 运动类型分布饼图
 */
@Composable
private fun ExerciseTypePieChart(
    modifier: Modifier = Modifier,
    typeSteps: Map<String, Float>,
    typeCalories: Map<String, Float>
) {
    val colors = listOf(
        AndroidColor.parseColor("#4CAF50"),
        AndroidColor.parseColor("#2196F3"),
        AndroidColor.parseColor("#FF9800"),
        AndroidColor.parseColor("#9C27B0"),
        AndroidColor.parseColor("#607D8B")
    )
    
    val entries = typeSteps.filter { it.value > 0 }.map { com.github.mikephil.charting.data.PieEntry(it.value, it.key) }
    
    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                isDrawHoleEnabled = true
                holeRadius = 45f
                transparentCircleRadius = 50f
                setUsePercentValues(true)
                setEntryLabelTextSize(11f)
                setEntryLabelColor(AndroidColor.WHITE)
                legend.isEnabled = true
                legend.textSize = 11f
            }
        },
        update = { chart ->
            val dataSet = com.github.mikephil.charting.data.PieDataSet(entries, "运动类型").apply {
                setColors(colors.take(entries.size))
                sliceSpace = 2f
                valueTextSize = 11f
                valueTextColor = AndroidColor.WHITE
                valueFormatter = PercentFormatter(chart)
            }
            chart.data = com.github.mikephil.charting.data.PieData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * 健康评分趋势图
 */
@Composable
private fun ScoreTrendChart(
    modifier: Modifier = Modifier,
    values: List<Float>,
    labels: List<String>
) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)
                isHighlightPerTapEnabled = false
                setPinchZoom(false)
                setScaleEnabled(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter(labels.toTypedArray())
                    textSize = 10f
                }
                axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = 100f
                    textSize = 10f
                }
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = values.mapIndexed { i, v -> com.github.mikephil.charting.data.Entry(i.toFloat(), v) }
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, "健康评分").apply {
                color = AndroidColor.parseColor("#FF5722")
                setCircleColor(AndroidColor.parseColor("#FF5722"))
                circleRadius = 4f
                lineWidth = 2f
                setDrawFilled(true)
                fillColor = AndroidColor.parseColor("#FFCCBC")
                fillAlpha = 100
                setDrawValues(true)
                valueTextSize = 9f
                mode = com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER
            }
            chart.data = com.github.mikephil.charting.data.LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * 周对比卡片
 */
@Composable
private fun WeekComparisonCard(
    thisWeekSteps: Int,
    lastWeekSteps: Int,
    thisWeekSleep: Float,
    lastWeekSleep: Float
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ComparisonItem(
                label = "总步数",
                thisValue = thisWeekSteps,
                lastValue = lastWeekSteps,
                unit = "步"
            )
            ComparisonItem(
                label = "平均睡眠",
                thisValue = thisWeekSleep,
                lastValue = lastWeekSleep,
                unit = "小时"
            )
        }
    }
}

@Composable
private fun ComparisonItem(
    label: String,
    thisValue: Number,
    lastValue: Number,
    unit: String
) {
    val diff = when (label) {
        "总步数" -> thisValue.toInt() - lastValue.toInt()
        "平均睡眠" -> ((thisValue.toFloat() - lastValue.toFloat()) * 10).toInt()
        else -> 0
    }
    val diffText = when {
        diff > 0 -> "+$diff"
        diff < 0 -> "$diff"
        else -> "持平"
    }
    
    Column(
        modifier = Modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            when (label) {
                "平均睡眠" -> String.format("%.1f", thisValue)
                else -> "$thisValue"
            },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "vs上周: $diffText",
            fontSize = 11.sp,
            color = when {
                diff > 0 -> MaterialTheme.colorScheme.primary
                diff < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
