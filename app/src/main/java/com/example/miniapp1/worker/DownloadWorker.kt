package com.example.miniapp1.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.miniapp1.R
import com.example.miniapp1.network.DownloadApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID = 1
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val modelDir = File(applicationContext.filesDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        val outputFile = File(modelDir, "gemma-4-E2B-it.litertlm")
        val existingLength = if (outputFile.exists()) outputFile.length() else 0L

        setForeground(createForegroundInfo(0))

        return try {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://huggingface.co/")
                .client(okHttpClient)
                .build()

            val apiService = retrofit.create(DownloadApiService::class.java)
            
            // Use Range header for resumable downloads
            val rangeHeader = if (existingLength > 0) "bytes=$existingLength-" else null
            val response = apiService.downloadFile(MODEL_URL, rangeHeader)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val isResume = response.code() == 206
                    val totalToDownload = body.contentLength()
                    val fullSize = if (isResume) existingLength + totalToDownload else totalToDownload
                    
                    saveFile(body.byteStream(), fullSize, existingLength, outputFile, isResume)
                    Result.success()
                } else {
                    Result.failure(workDataOf(KEY_ERROR to "Empty response body"))
                }
            } else if (response.code() == 416) {
                // Requested range not satisfiable - likely file is already complete
                Result.success()
            } else {
                Result.failure(workDataOf(KEY_ERROR to "Download failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.localizedMessage ?: "Unknown error")))
        }
    }

    private suspend fun saveFile(
        inputStream: InputStream, 
        totalContentLength: Long, 
        alreadyDownloaded: Long,
        outputFile: File,
        append: Boolean
    ) {
        FileOutputStream(outputFile, append).use { output ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var currentDownloaded = alreadyDownloaded
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                currentDownloaded += bytesRead
                
                if (totalContentLength > 0) {
                    val progress = (currentDownloaded * 100 / totalContentLength).toInt()
                    setProgress(workDataOf(KEY_PROGRESS to progress))
                    notificationManager.notify(NOTIFICATION_ID, createNotification(progress))
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0)
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                createNotification(progress),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, createNotification(progress))
        }
    }

    private fun createNotification(progress: Int): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading Model")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setSilent(true)
            .build()
    }
}
