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
import com.example.healthmanager.ui.viewmodel.ExerciseViewModel
import com.example.healthmanager.ui.viewmodel.HealthScoreViewModel
import com.example.healthmanager.ui.viewmodel.ProfileViewModel
import com.example.healthmanager.ui.viewmodel.SleepViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(navController: NavController) {
    val profileVm: ProfileViewModel = viewModel()
    val exerciseVm: ExerciseViewModel = viewModel()
    val sleepVm: SleepViewModel = viewModel()
    val scoreVm: HealthScoreViewModel = viewModel()
    val userId = profileVm.prefs.currentUserId
    val user by profileVm.currentUser.collectAsState()

    LaunchedEffect(userId) {
        exerciseVm.init(userId)
        sleepVm.init(userId)
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

            // 多维度健康评分
            HealthScoreSection(healthScore = healthScore)

            // 个性化健康建议（基于评分引擎生成）
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
