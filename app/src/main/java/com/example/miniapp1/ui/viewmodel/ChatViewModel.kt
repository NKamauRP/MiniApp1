package com.example.miniapp1.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.miniapp1.data.model.ChatMessage
import com.example.miniapp1.data.model.MessageRole
import com.example.miniapp1.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed class ChatInitializationState {
    object Idle : ChatInitializationState()
    object Initializing : ChatInitializationState()
    object Ready : ChatInitializationState()
    data class Error(val message: String) : ChatInitializationState()
}

class ChatViewModel() : ViewModel() {

    private val repository: ChatRepository = ChatRepository.getInstance()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _initializationState = MutableStateFlow<ChatInitializationState>(ChatInitializationState.Idle)
    val initializationState: StateFlow<ChatInitializationState> = _initializationState.asStateFlow()

    init {
        if (repository.isInitialized()) {
            _initializationState.value = ChatInitializationState.Ready
        }
    }

    fun initializeModel(modelPath: String) {
        if (repository.isInitialized()) {
            _initializationState.value = ChatInitializationState.Ready
            return
        }
        
        viewModelScope.launch {
            _initializationState.value = ChatInitializationState.Initializing
            try {
                repository.initialize(modelPath)
                _initializationState.value = ChatInitializationState.Ready
                _error.value = null
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Unknown initialization error"
                _initializationState.value = ChatInitializationState.Error(errorMsg)
                _error.value = "Failed to initialize model: $errorMsg"
            }
        }
    }

    fun forceCpuInitialize(modelPath: String) {
        viewModelScope.launch {
            _initializationState.value = ChatInitializationState.Initializing
            try {
                repository.forceCpuInitialize(modelPath)
                _initializationState.value = ChatInitializationState.Ready
                _error.value = null
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Unknown initialization error"
                _initializationState.value = ChatInitializationState.Error(errorMsg)
                _error.value = "Failed to initialize CPU engine: $errorMsg"
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (!repository.isInitialized()) {
            _error.value = "Model not initialized. Please go to settings and download it."
            return
        }

        val userMessageId = UUID.randomUUID().toString()
        val userMessage = ChatMessage(id = userMessageId, text = text, role = MessageRole.USER)
        _messages.update { it + userMessage }

        viewModelScope.launch {
            _isTyping.value = true
            _error.value = null
            
            val modelMessageId = UUID.randomUUID().toString()
            val initialModelMessage = ChatMessage(
                id = modelMessageId,
                text = "",
                role = MessageRole.MODEL,
                isStreaming = true
            )
            _messages.update { it + initialModelMessage }

            try {
                var fullResponse = ""
                repository.getStreamingResponse(text).collect { updatedToken ->
                    fullResponse += updatedToken
                    _messages.update { currentMessages ->
                        currentMessages.map { msg ->
                            if (msg.id == modelMessageId) {
                                msg.copy(text = fullResponse)
                            } else {
                                msg
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = "Inference error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _messages.update { currentMessages ->
                    currentMessages.map { msg ->
                        if (msg.id == modelMessageId) {
                            msg.copy(isStreaming = false)
                        } else {
                            msg
                        }
                    }
                }
                _isTyping.value = false
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}
