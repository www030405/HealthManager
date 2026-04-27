package com.example.healthmanager.network

import android.util.Log
import com.example.healthmanager.ui.screen.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AssistantApiService {

    companion object {
        private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
        private const val API_KEY = "4f8e98cb-50d3-4da4-b5bc-fb7fdf4980e9"
        private const val MODEL = "doubao-seed-1-8-251228"
        private const val TAG = "AssistantAPI"
    }

    private val systemPrompt = """你是一个专业的健康助手，请根据用户的健康问题提供科学、实用的建议。
注意：
1. 回答要专业但易懂
2. 如涉及疾病诊断，请建议就医
3. 可以适当使用emoji让回答更生动
4. 回答尽量简洁有条理"""

    suspend fun sendMessage(messages: List<ChatMessage>): String? = withContext(Dispatchers.IO) {
        var lastError: String? = null

        for (attempt in 1..3) {
            try {
                Log.d(TAG, "尝试 $attempt/3")

                val url = URL(BASE_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.setRequestProperty("Authorization", "Bearer $API_KEY")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val requestBody = buildRequestBody(messages)
                Log.d(TAG, "发送消息数: ${messages.size}")

                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(requestBody)
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                Log.d(TAG, "响应码: $responseCode")

                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    val jsonStr = response.toString()
                    Log.d(TAG, "响应: ${jsonStr.substring(0, minOf(500, jsonStr.length))}")

                    return@withContext parseResponse(jsonStr)
                } else {
                    val errorReader = BufferedReader(InputStreamReader(connection.errorStream, "UTF-8"))
                    val errorResponse = errorReader.readText()
                    lastError = "HTTP $responseCode: $errorResponse"
                    Log.e(TAG, "HTTP错误: $lastError")
                }

                connection.disconnect()

                if (attempt < 3) {
                    kotlinx.coroutines.delay(1000 * attempt.toLong())
                }

            } catch (e: Exception) {
                lastError = e.message
                Log.e(TAG, "尝试 $attempt 失败: ${e.message}")
                if (attempt < 3) {
                    kotlinx.coroutines.delay(1000 * attempt.toLong())
                }
            }
        }

        return@withContext "请求失败: $lastError"
    }

    private fun buildRequestBody(messages: List<ChatMessage>): String {
        val messagesJson = messages.joinToString(",\n") { msg ->
            """{"role": "${msg.role}", "content": "${escapeJson(msg.content)}"}"""
        }

        return """
            {
                "model": "$MODEL",
                "messages": [
                    {"role": "system", "content": "${escapeJson(systemPrompt)}"},
                    $messagesJson
                ]
            }
        """.trimIndent()
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun parseResponse(jsonStr: String): String? {
        try {
            Log.d(TAG, "开始解析JSON: ${jsonStr.substring(0, minOf(200, jsonStr.length))}")

            val json = org.json.JSONObject(jsonStr)

            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorMessage = error.optString("message", "未知错误")
                Log.e(TAG, "API返回错误: $errorMessage")
                return "抱歉，服务暂时不可用：$errorMessage"
            }

            val code = json.optInt("code", 200)
            if (code != 200) {
                val msg = json.optString("msg", "未知错误")
                Log.e(TAG, "API错误码: $code, 消息: $msg")
                return "抱歉，服务暂时不可用"
            }

            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                if (message != null) {
                    return message.optString("content", null)
                }
            }

            Log.w(TAG, "未找到响应内容")
            return "抱歉，我暂时无法回答这个问题，请换个话题试试~"
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析异常: ${e.message}")
            return "抱歉，出现了一些问题，请稍后重试~"
        }
    }
}
