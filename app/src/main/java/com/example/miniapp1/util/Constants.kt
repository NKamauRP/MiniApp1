package com.example.miniapp1.util

import android.content.Context
import java.io.File

object Constants {
    const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    const val MODEL_DIR = "models"
    
    fun getModelPath(context: Context): String {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return File(modelDir, MODEL_FILENAME).absolutePath
    }
}
