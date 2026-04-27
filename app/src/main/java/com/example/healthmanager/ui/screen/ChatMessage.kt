package com.example.healthmanager.ui.screen

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    val role: String get() = if (isUser) "user" else "assistant"
}