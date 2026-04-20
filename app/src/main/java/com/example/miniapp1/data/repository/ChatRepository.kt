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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    init {
        try {
            // Attempt to trigger native library loading
            val loaderClass = Class.forName("com.google.ai.edge.litertlm.NativeLibraryLoader")
            // The object instance in Kotlin is stored in a static field named INSTANCE
            val instanceField = loaderClass.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            val loaderInstance = instanceField.get(null)
            
            val loadMethod = loaderClass.getDeclaredMethod("load")
            loadMethod.isAccessible = true
            loadMethod.invoke(loaderInstance)
            Log.d(TAG, "NativeLibraryLoader.load() invoked successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to manually trigger NativeLibraryLoader: ${e.localizedMessage}")
            // Fallback: try loading the library directly if we can guess the name
            try {
                System.loadLibrary("litertlm_jni")
                Log.d(TAG, "Manually loaded litertlm_jni as fallback")
            } catch (linkError: UnsatisfiedLinkError) {
                Log.e(TAG, "Fallback System.loadLibrary(litertlm_jni) also failed: ${linkError.message}")
            }
        }
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val initMutex = Mutex()

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (engine != null) {
                Log.d(TAG, "Engine already initialized.")
                return@withLock
            }
            
            val file = File(modelPath)
            if (!file.exists()) {
                Log.e(TAG, "Model file NOT found at $modelPath")
                throw Exception("Model file not found. Please download it first in Settings.")
            }

            try {
                // Given the GPU memory constraints (Adreno 710 issues), 
                // we might want to default to CPU or handle GPU failure more gracefully.
                // For now, let's keep the fallback but the Mutex will prevent concurrent attempts.
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
            } catch (e: Error) {
                // Catching Error as well because SIGSEGV might be preceded by some LinkageError or other non-Exception errors
                Log.e(TAG, "Critical error during GPU init: ${e.localizedMessage}. Trying CPU.")
                initializeWithBackend(modelPath, Backend.CPU())
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
    
    suspend fun forceCpuInitialize(modelPath: String) = withContext(Dispatchers.IO) {
        initMutex.withLock {
            close()
            initializeWithBackend(modelPath, Backend.CPU())
        }
    }
}
