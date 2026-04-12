package com.example.healthmanager.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 健康资讯API服务
 * 接口：https://apis.tianapi.com/healthskill/index
 * 格式：application/x-www-form-urlencoded
 */
class HealthNewsApiService {

    companion object {
        private const val BASE_URL = "https://apis.tianapi.com/healthskill/index"
        private const val API_KEY = "eac7058931227c7907d68971e9e4199d"
        private const val TAG = "HealthNewsAPI"
    }

    /**
     * 获取健康文章列表
     * 使用 GET 请求
     */
    suspend fun getHealthArticles(word: String): List<HealthArticle>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "请求关键词: $word")

            // 使用 GET 请求，参数直接在 URL 中
            val encodedWord = URLEncoder.encode(word, "UTF-8")
            val urlStr = "$BASE_URL?key=$API_KEY&word=$encodedWord"
            Log.d(TAG, "请求URL: $urlStr")
            
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

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
            } else {
                Log.e(TAG, "请求失败: $responseCode")
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream, "UTF-8"))
                val errorResponse = errorReader.readText()
                Log.e(TAG, "错误响应: $errorResponse")
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "网络请求异常: ${e.message}")
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * 解析JSON响应
     */
    private fun parseJsonResponse(jsonStr: String): List<HealthArticle>? {
        try {
            val json = org.json.JSONObject(jsonStr)
            val code = json.getInt("code")
            if (code != 200) {
                Log.e(TAG, "API错误码: $code, 消息: ${json.optString("msg")}")
                return null
            }

            val newsList = json.getJSONArray("newslist")
            val articles = mutableListOf<HealthArticle>()

            for (i in 0 until newsList.length()) {
                val item = newsList.getJSONObject(i)
                val title = item.optString("title", "")
                val content = item.optString("content", "")
                val source = item.optString("source", "")
                val url = item.optString("url", "")

                if (title.isNotEmpty()) {
                    articles.add(HealthArticle(
                        id = i,
                        title = title,
                        description = content,
                        source = source,
                        link = url
                    ))
                }
            }

            Log.d(TAG, "解析到 ${articles.size} 篇文章")
            return articles.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析异常: ${e.message}")
            return null
        }
    }
}

/**
 * 健康文章数据模型
 */
data class HealthArticle(
    val id: Int,
    val title: String,
    val description: String,
    val source: String = "",
    val link: String = ""
)