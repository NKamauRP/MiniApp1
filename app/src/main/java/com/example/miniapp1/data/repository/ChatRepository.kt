package com.example.miniapp1.data.repository

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        
        // Static instance for cross-viewModel access
        @Volatile
        private var instance: ChatRepository? = null

        fun getInstance(): ChatRepository {
            return instance ?: synchronized(this) {
                instance ?: ChatRepository().also { instance = it }
            }
        }
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        if (engine != null) {
            Log.d(TAG, "Engine already initialized.")
            return@withContext
        }
        
        val file = File(modelPath)
        if (!file.exists()) {
            Log.e(TAG, "Model file NOT found at $modelPath")
            throw Exception("Model file not found. Please download it first in Settings.")
        }

        try {
            Log.d(TAG, "Attempting GPU initialization at: $modelPath")
            initializeWithBackend(modelPath, Backend.GPU())
        } catch (e: Exception) {
            Log.w(TAG, "GPU initialization failed, falling back to CPU: ${e.localizedMessage}")
            try {
                initializeWithBackend(modelPath, Backend.CPU())
            } catch (cpuEx: Exception) {
                Log.e(TAG, "CPU initialization also failed: ${cpuEx.localizedMessage}")
                throw Exception("Failed to initialize model engine: ${cpuEx.localizedMessage}")
            }
        }
    }

    private suspend fun initializeWithBackend(path: String, backend: Backend) {
        val config = EngineConfig(
            modelPath = path,
            backend = backend
        )
        
        val newEngine = Engine(config)
        newEngine.initialize()
        engine = newEngine
        conversation = newEngine.createConversation()
        Log.i(TAG, "Engine successfully initialized with ${backend.javaClass.simpleName}")
    }

    fun getStreamingResponse(userPrompt: String): Flow<String> {
        val conv = conversation ?: throw Exception("Chat engine is not ready. Please wait or check initialization.")
        
        return conv.sendMessageAsync(userPrompt).map { message ->
            message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }
    }
    
    fun isInitialized(): Boolean = engine != null
    
    fun close() {
        Log.d(TAG, "Closing engine...")
        engine?.close()
        engine = null
        conversation = null
    }
}
