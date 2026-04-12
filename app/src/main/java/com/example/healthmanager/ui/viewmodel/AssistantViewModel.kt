package com.example.healthmanager.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.network.AssistantApiService
import com.example.healthmanager.ui.screen.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    
    private val apiService = AssistantApiService()
    private val prefs: SharedPreferences = application.getSharedPreferences("assistant_chat", 0)
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(loadMessages())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val welcomeMessage = ChatMessage(
        content = "你好！我是你的健康助手，有什么健康问题都可以问我~",
        isUser = false
    )

    private fun loadMessages(): List<ChatMessage> {
        val json = prefs.getString("messages", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(ChatMessage(
                    id = obj.getLong("id"),
                    content = obj.getString("content"),
                    isUser = obj.getBoolean("isUser"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMessages(messages: List<ChatMessage>) {
        val jsonArray = JSONArray()
        messages.forEach { msg ->
            jsonArray.put(JSONObject().apply {
                put("id", msg.id)
                put("content", msg.content)
                put("isUser", msg.isUser)
                put("timestamp", msg.timestamp)
            })
        }
        prefs.edit().putString("messages", jsonArray.toString()).apply()
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        android.util.Log.d("AssistantVM", "发送消息: $content")
        
        if (_messages.value.isEmpty()) {
            _messages.value = listOf(welcomeMessage)
            saveMessages(_messages.value)
        }
        
        val userMsg = ChatMessage(content = content, isUser = true)
        _messages.value = _messages.value + userMsg
        saveMessages(_messages.value)
        
        _isLoading.value = true
        
        viewModelScope.launch {
            val response = apiService.sendMessage(content)
            _isLoading.value = false
            
            android.util.Log.d("AssistantVM", "收到回复: $response")
            
            val aiReply = response ?: "抱歉，我暂时无法回答你的问题，请稍后重试。"
            val aiMsg = ChatMessage(content = aiReply, isUser = false)
            _messages.value = _messages.value + aiMsg
            saveMessages(_messages.value)
        }
    }

    fun clearChat() {
        _messages.value = listOf(welcomeMessage)
        saveMessages(_messages.value)
    }
}
