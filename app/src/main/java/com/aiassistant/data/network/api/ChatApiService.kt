package com.aiassistant.data.network.api

import com.aiassistant.data.network.model.ChatRequest
import com.aiassistant.data.network.model.ChatResponse
import com.aiassistant.data.network.model.FileUploadResponse
import com.aiassistant.data.network.model.ResponsesRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

interface ChatApiService {

    @POST("v1/chat/completions")
    @Streaming
    suspend fun streamChatCompletions(
        @Body request: ChatRequest
    ): ResponseBody

    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Body request: ChatRequest
    ): ChatResponse

    @POST("v1/responses")
    @Streaming
    suspend fun streamResponses(
        @Body request: ResponsesRequest
    ): ResponseBody

    @POST("v1/audio/transcriptions")
    @Multipart
    suspend fun uploadAudioForTranscription(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("language") language: RequestBody? = null,
        @Part("response_format") format: RequestBody? = null,
        @Part("temperature") temperature: RequestBody? = null
    ): ResponseBody

    @POST("v1/files")
    @Multipart
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("purpose") purpose: RequestBody
    ): FileUploadResponse
}
