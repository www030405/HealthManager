package com.example.healthmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.healthmanager.data.repository.UserPreferences
import com.example.healthmanager.navigation.NavGraph
import com.example.healthmanager.navigation.Screen
import com.example.healthmanager.notification.HealthNotificationManager
import com.example.healthmanager.ui.theme.HealthManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化通知渠道
        HealthNotificationManager.createNotificationChannel(this)

        // 启动每日运动提醒（可在设置中关闭）
        HealthNotificationManager.scheduleExerciseReminder(this)

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
}
