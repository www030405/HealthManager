package com.example.healthmanager.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.healthmanager.ui.screen.*

/**
 * 全局导航图
 * 根据是否已登录决定起始页面
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }

        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.Exercise.route) {
            ExerciseScreen(navController = navController)
        }

        composable(Screen.Diet.route) {
            DietScreen(navController = navController)
        }

        composable(Screen.Sleep.route) {
            SleepScreen(navController = navController)
        }

        composable(Screen.Report.route) {
            ReportScreen(navController = navController)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }
    }
}
