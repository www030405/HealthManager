package com.example.healthmanager.navigation

/**
 * 定义所有页面路由
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Exercise : Screen("exercise")
    object Diet : Screen("diet")
    object Sleep : Screen("sleep")
    object Report : Screen("report")
    object Profile : Screen("profile")
    object Assistant : Screen("assistant")
    object AssistantTab : Screen("assistant_tab")
    object HomeTab : Screen("home_tab")
}
