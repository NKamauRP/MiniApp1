package com.example.miniapp1.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.miniapp1.data.model.DownloadState
import com.example.miniapp1.data.model.ModelAsset
import com.example.miniapp1.data.repository.ChatRepository
import com.example.miniapp1.util.Constants
import com.example.miniapp1.worker.DownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class DownloadViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val chatRepository: ChatRepository = ChatRepository.getInstance()
    private val workManager = WorkManager.getInstance(application)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _availableModels = MutableStateFlow(
        listOf(
            ModelAsset(
                id = "gemma-4-e2b",
                name = "Gemma 4 E2B",
                size = "1.2 GB",
                downloadUrl = DownloadWorker.MODEL_URL,
                isDownloaded = false
            )
        )
    )
    val availableModels: StateFlow<List<ModelAsset>> = _availableModels.asStateFlow()

    init {
        checkCurrentState()
        observeWorkInfo()
    }

    private fun checkCurrentState() {
        val modelPath = Constants.getModelPath(getApplication())
        val file = File(modelPath)
        val exists = file.exists() && file.length() > 0
        
        _availableModels.value = _availableModels.value.map { 
            if (it.id == "gemma-4-e2b") it.copy(isDownloaded = exists) else it
        }

        if (exists) {
            _downloadState.value = DownloadState.Completed
            viewModelScope.launch {
                try {
                    chatRepository.initialize(modelPath)
                } catch (e: Exception) {
                }
            }
        } else {
            _downloadState.value = DownloadState.Idle
        }
    }

    private fun observeWorkInfo() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData("model_download")
                .asFlow()
                .collectLatest { workInfos ->
                    val workInfo = workInfos.firstOrNull() ?: return@collectLatest
                    
                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                            _downloadState.value = DownloadState.Downloading(progress / 100f)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _downloadState.value = DownloadState.Completed
                            _availableModels.value = _availableModels.value.map { 
                                if (it.id == "gemma-4-e2b") it.copy(isDownloaded = true) else it
                            }
                            chatRepository.initialize(Constants.getModelPath(getApplication()))
                        }
                        WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString(DownloadWorker.KEY_ERROR) ?: "Download failed"
                            _downloadState.value = DownloadState.Error(error)
                        }
                        WorkInfo.State.CANCELLED -> {
                            _downloadState.value = DownloadState.Idle
                        }
                        else -> {}
                    }
                }
        }
    }

    fun startDownload(model: ModelAsset) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "model_download",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val path = Constants.getModelPath(getApplication())
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    fun getModelPath(modelId: String): String {
        return Constants.getModelPath(getApplication())
    }

    fun deleteModel(modelId: String) {
        val path = Constants.getModelPath(getApplication())
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        _availableModels.value = _availableModels.value.map { 
            if (it.id == modelId) it.copy(isDownloaded = false) else it
        }
        chatRepository.close() // Close engine if model is deleted
        _downloadState.value = DownloadState.Idle
    }
}
