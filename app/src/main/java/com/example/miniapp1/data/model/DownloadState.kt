package com.example.miniapp1.data.model

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

data class ModelAsset(
    val id: String,
    val name: String,
    val size: String,
    val downloadUrl: String,
    val isDownloaded: Boolean = false
)
