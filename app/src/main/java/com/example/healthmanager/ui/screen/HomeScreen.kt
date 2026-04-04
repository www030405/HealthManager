package com.example.healthmanager.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthmanager.healthconnect.HealthConnectManager
import com.example.healthmanager.navigation.Screen
import com.example.healthmanager.ui.viewmodel.ExerciseViewModel
import com.example.healthmanager.ui.viewmodel.HealthConnectViewModel
import com.example.healthmanager.ui.viewmodel.ProfileViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val profileVm: ProfileViewModel = viewModel()
    val exerciseVm: ExerciseViewModel = viewModel()
    val hcVm: HealthConnectViewModel = viewModel()
    val user by profileVm.currentUser.collectAsState()
    val userId = profileVm.prefs.currentUserId
    val context = LocalContext.current

    LaunchedEffect(userId) {
        exerciseVm.init(userId)
        hcVm.checkPermissions()
    }

    // Health Connect 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = hcVm.getPermissionContract(),
        onResult = { grantedPermissions ->
            hcVm.checkPermissions()
            if (grantedPermissions.isNotEmpty()) {
                hcVm.syncTodaySteps()
            }
        }
    )

    val todaySteps by exerciseVm.todaySteps.collectAsState()
    val todayCalories by exerciseVm.todayCalories.collectAsState()
    val sensorSteps by exerciseVm.sensorSteps.collectAsState()
    val sensorCalories by exerciseVm.sensorCalories.collectAsState()
    val isSensorAvailable = exerciseVm.isSensorAvailable
    // 总卡路里 = 传感器估算 + 手动记录
    val displayCalories = sensorCalories + todayCalories
    val hcSteps by hcVm.todayStepsFromHC.collectAsState()
    val hcPermission by hcVm.permissionGranted.collectAsState()
    val syncStatus by hcVm.syncStatus.collectAsState()
    // 优先级：Health Connect > 传感器实时步数 > 数据库手动记录
    val displaySteps = when {
        hcPermission && hcSteps > 0 -> hcSteps.toInt()
        sensorSteps > 0 -> sensorSteps
        else -> todaySteps
    }
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))

    // 显示同步状态提示
    syncStatus?.let { status ->
        LaunchedEffect(status) {
            SnackbarDuration.Short
            // 这里可以用 Scaffold 的 snackbarHostState 显示提示
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康管理") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "个人资料")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 欢迎卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "你好，${user?.nickname ?: "用户"}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = today,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Health Connect 状态提示
                    if (!hcVm.isAvailable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AlertCard(message = "设备未安装 Health Connect，无法同步健康数据")
                    } else if (!hcPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                permissionLauncher.launch(HealthConnectManager.PERMISSIONS) 
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("授权 Health Connect")
                        }
                    }
                    
                    // 传感器状态提示
                    if (!isSensorAvailable && !hcVm.isAvailable) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ 设备不支持硬件计步器（可能是模拟器）",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (sensorSteps > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✅ 传感器工作正常：$sensorSteps 步",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 今日概览
            Text("今日概览", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = when {
                        hcPermission && hcSteps > 0 -> "步数 (HC)"
                        sensorSteps > 0 -> "步数 (实时)"
                        else -> "步数"
                    },
                    value = "$displaySteps",
                    unit = "步",
                    icon = Icons.Default.DirectionsWalk,
                    target = "${user?.targetSteps ?: 8000}"
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "消耗",
                    value = String.format("%.0f", displayCalories),
                    unit = "kcal",
                    icon = Icons.Default.LocalFireDepartment,
                    target = "目标达成"
                )
            }

            // 功能入口
            Text("功能模块", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "运动记录",
                        subtitle = "步数 · 卡路里",
                        icon = Icons.Default.FitnessCenter,
                        onClick = { navController.navigate(Screen.Exercise.route) }
                    )
                    FeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "饮食管理",
                        subtitle = "食物 · 营养",
                        icon = Icons.Default.Restaurant,
                        onClick = { navController.navigate(Screen.Diet.route) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "睡眠监测",
                        subtitle = "时长 · 质量",
                        icon = Icons.Default.Bedtime,
                        onClick = { navController.navigate(Screen.Sleep.route) }
                    )
                    FeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "健康报告",
                        subtitle = "趋势 · 分析",
                        icon = Icons.Default.BarChart,
                        onClick = { navController.navigate(Screen.Report.route) }
                    )
                }
            }
            
            // 显示同步状态
            syncStatus?.let { status ->
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { hcVm.clearSyncStatus() },
                    label = { Text(status, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AlertCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    target: String
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(target, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
