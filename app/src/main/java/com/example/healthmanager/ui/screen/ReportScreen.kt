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
import com.example.healthmanager.ui.viewmodel.ExerciseViewModel
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
    val userId = profileVm.prefs.currentUserId

    LaunchedEffect(userId) {
        exerciseVm.init(userId)
        sleepVm.init(userId)
    }

    val weekExercise by exerciseVm.weekRecords.collectAsState()
    val weekSleep by sleepVm.weekRecords.collectAsState()
    val avgSleep by sleepVm.avgDuration.collectAsState()

    // 近7天日期标签
    val dates = (6 downTo 0).map {
        LocalDate.now().minusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val dayLabels = dates.map { it.substring(5) } // "MM-dd"

    // 按日期聚合步数（仅统计数据库中的手动记录，不包括当天传感器实时步数）
    val stepsByDate = dates.map { date ->
        weekExercise.filter { it.date == date }.sumOf { it.steps }.toFloat()
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

            // 健康建议
            HealthAdviceCard(
                avgSleep = avgSleep,
                avgSteps = if (stepsByDate.any { it > 0 })
                    stepsByDate.filter { it > 0 }.average().toFloat() else 0f
            )
        }
    }
}

@Composable
private fun WeekSummaryCard(totalSteps: Int, activeDays: Int, avgSleep: Float) {
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
                SummaryStatItem("总步数", "$totalSteps", "步")
                SummaryStatItem("运动天数", "$activeDays", "天")
                SummaryStatItem("平均睡眠", String.format("%.1f", avgSleep), "小时")
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

@Composable
private fun HealthAdviceCard(avgSleep: Float, avgSteps: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("个性化健康建议", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))

            val advices = buildList {
                if (avgSleep < 7f) add("• 近期平均睡眠 ${String.format("%.1f", avgSleep)} 小时，低于推荐的7-9小时，建议调整作息，早睡早起。")
                else add("• 睡眠时长达标（${String.format("%.1f", avgSleep)}小时），继续保持良好的睡眠习惯！")

                when {
                    avgSteps < 3000f -> add("• 日均步数仅 ${avgSteps.toInt()} 步，活动量严重不足，建议增加日常步行。")
                    avgSteps < 6000f -> add("• 日均步数 ${avgSteps.toInt()} 步，建议增加步行，目标8000步/天。")
                    avgSteps >= 8000f -> add("• 日均步数 ${avgSteps.toInt()} 步，运动量达标！继续保持。")
                    else -> add("• 日均步数 ${avgSteps.toInt()} 步，距离目标还差 ${(8000 - avgSteps).toInt()} 步，加油！")
                }

                add("• 建议保持三餐均衡，控制精加工食品和高糖饮料摄入，增加蔬菜水果比例。")
                add("• 每周保持3-5次有氧运动，每次不少于30分钟，有助于改善心肺功能。")
            }

            advices.forEach { advice ->
                Text(
                    text = advice,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                    lineHeight = 20.sp
                )
            }
        }
    }
}
