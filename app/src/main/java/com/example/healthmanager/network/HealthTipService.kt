package com.example.healthmanager.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HealthTipService {

    companion object {
        private const val BASE_URL = "https://apis.tianapi.com/healthtip/index"
        private const val API_KEY = "eac7058931227c7907d68971e9e4199d"
        private const val TAG = "HealthTipAPI"
    }

    suspend fun getHealthTips(): List<HealthTip>? = withContext(Dispatchers.IO) {
        try {
            val urlStr = "$BASE_URL?key=$API_KEY"
            Log.d(TAG, "请求URL: $urlStr")
            
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            Log.d(TAG, "响应码: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonStr = response.toString()
                Log.d(TAG, "响应: ${jsonStr.substring(0, minOf(200, jsonStr.length))}")

                return@withContext parseJsonResponse(jsonStr)
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "网络请求异常: ${e.message}")
        }
        return@withContext null
    }

    private fun parseJsonResponse(jsonStr: String): List<HealthTip>? {
        try {
            val json = org.json.JSONObject(jsonStr)
            val code = json.getInt("code")
            if (code != 200) {
                Log.e(TAG, "API错误码: $code")
                return getDefaultTips()
            }

            val tips = mutableListOf<HealthTip>()

            // 尝试解析 result.content（单个内容）
            val result = json.optJSONObject("result")
            if (result != null) {
                val content = result.optString("content", "")
                if (content.isNotEmpty()) {
                    tips.add(HealthTip(id = 1, content = content))
                }
            }

            // 尝试解析 newslist（数组）
            val newsList = json.optJSONArray("newslist")
            if (newsList != null) {
                for (i in 0 until newsList.length()) {
                    val item = newsList.getJSONObject(i)
                    val content = item.optString("content", "")
                    val tip = item.optString("tip", "")
                    if (content.isNotEmpty() || tip.isNotEmpty()) {
                        tips.add(HealthTip(
                            id = i + 1,
                            content = content.ifEmpty { tip }
                        ))
                    }
                }
            }

            Log.d(TAG, "解析到 ${tips.size} 条健康提示")
            return tips.ifEmpty { getDefaultTips() }
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析异常: ${e.message}")
            return getDefaultTips()
        }
    }

    private fun getDefaultTips(): List<HealthTip> {
        return listOf(
            HealthTip(1, "每天保证7-8小时睡眠，有助于身体恢复"),
            HealthTip(2, "多喝水，每天至少8杯水"),
            HealthTip(3, "适度运动，每天30分钟中等强度运动"),
            HealthTip(4, "保持良好饮食习惯，少油少盐"),
            HealthTip(5, "定期测量血压，关注心血管健康")
        )
    }
}

data class HealthTip(
    val id: Int,
    val content: String
)
