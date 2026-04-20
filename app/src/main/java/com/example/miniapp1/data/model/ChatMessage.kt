package com.example.miniapp1.data.model

import java.util.UUID

enum class MessageRole {
    USER, MODEL
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)
