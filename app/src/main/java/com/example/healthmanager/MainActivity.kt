package com.example.healthmanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.healthmanager.data.repository.UserPreferences
import com.example.healthmanager.navigation.NavGraph
import com.example.healthmanager.navigation.Screen
import com.example.healthmanager.notification.HealthNotificationManager
import com.example.healthmanager.ui.theme.HealthManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求运动识别权限（Android 13+ 必需）
        requestActivityRecognitionPermission()

        // 初始化通知渠道
        HealthNotificationManager.createNotificationChannel(this)

        // 启动每日运动提醒（可在设置中关闭）
        HealthNotificationManager.scheduleExerciseReminder(this)

        // 启动午夜步数归档任务（将传感器步数保存到数据库，用于报告图表）
        HealthNotificationManager.scheduleMidnightStepArchive(this)

        // 启动每日健康评分存储任务（每天凌晨计算并保存健康评分）
        HealthNotificationManager.scheduleDailyHealthScore(this)

        // 启动睡眠提醒（每天 23:30）
        HealthNotificationManager.scheduleSleepReminder(this)

        // 启动饮食提醒（每天 11:40 和 18:00）
        HealthNotificationManager.scheduleMealReminder(this)

        // 启动饮水提醒（每2小时一次，从8:00开始）
        HealthNotificationManager.scheduleWaterReminder(this)

        // 判断是否已登录
        val prefs = UserPreferences(this)
        val startDestination = if (prefs.isLoggedIn()) Screen.Home.route else Screen.Login.route

        enableEdgeToEdge()
        setContent {
            HealthManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }

    /**
     * 请求活动识别权限（用于计步器）
     */
    private fun requestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.ACTIVITY_RECOGNITION
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                
                // 需要显示权限理由时（用户之前拒绝过）
                if (shouldShowRequestPermissionRationale(permission)) {
                    Log.d("Permission", "需要说明权限用途")
                }
                
                // 请求权限
                val requestPermissionLauncher = registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        Log.d("Permission", "ACTIVITY_RECOGNITION 已授权")
                    } else {
                        Log.w("Permission", "ACTIVITY_RECOGNITION 被拒绝，计步器可能无法工作")
                    }
                }
                requestPermissionLauncher.launch(permission)
            } else {
                Log.d("Permission", "ACTIVITY_RECOGNITION 已有权限")
            }
        } else {
            Log.d("Permission", "Android 版本 < 13，不需要动态申请 ACTIVITY_RECOGNITION")
        }
    }
}
