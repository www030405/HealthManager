package com.example.healthmanager.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * 用 SharedPreferences 保存当前登录用户ID
 * 实现"记住登录状态"功能
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("health_manager_prefs", Context.MODE_PRIVATE)

    var currentUserId: Int
        get() = prefs.getInt(KEY_USER_ID, -1)
        set(value) = prefs.edit().putInt(KEY_USER_ID, value).apply()

    fun isLoggedIn(): Boolean = currentUserId != -1

    fun logout() = prefs.edit().putInt(KEY_USER_ID, -1).apply()

    companion object {
        private const val KEY_USER_ID = "current_user_id"
    }
}
