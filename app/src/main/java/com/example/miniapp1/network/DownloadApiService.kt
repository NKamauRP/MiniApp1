package com.example.miniapp1.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import retrofit2.http.Url

interface DownloadApiService {
    @Streaming
    @GET
    suspend fun downloadFile(
        @Url url: String,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>
}
