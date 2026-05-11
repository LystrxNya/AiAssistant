
package com.aiassistant.data.network

import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.ModelConfigItem
import com.aiassistant.data.network.api.ChatApiService
import com.aiassistant.data.network.model.ChatRequest
import com.aiassistant.data.network.model.ChatResponse
import com.aiassistant.data.network.model.FileUploadResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UploadProgress(val percent: Int, val currentBytes: Long, val totalBytes: Long)

@Singleton
class ApiClient @Inject constructor(
    private val gson: Gson,
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    @Volatile
    private var mainRetrofit: Retrofit? = null

    @Volatile
    private var mainApiService: ChatApiService? = null

    @Volatile
    private var auxRetrofit: Retrofit? = null

    @Volatile
    private var auxApiService: ChatApiService? = null

    @Volatile
    private var visionRetrofit: Retrofit? = null

    @Volatile
    private var visionApiService: ChatApiService? = null

    private val dynamicServices = mutableMapOf<String, ChatApiService>()
    private val dynamicRetrofits = mutableMapOf<String, Retrofit>()

    private val _uploadProgress = MutableStateFlow(UploadProgress(0, 0, 0))
    val uploadProgress: StateFlow<UploadProgress> = _uploadProgress

    fun getMainApiService(): ChatApiService {
        mainApiService?.let { return it }
        synchronized(this) {
            mainApiService?.let { return it }
            val service = createRetrofit(
                baseUrl = encryptedPrefsManager.getBaseUrl(),
                apiKey = encryptedPrefsManager.getApiKey("default")
            ).create(ChatApiService::class.java)
            mainApiService = service
            return service
        }
    }

    fun getAuxApiService(): ChatApiService {
        auxApiService?.let { return it }
        synchronized(this) {
            auxApiService?.let { return it }
            val auxBaseUrl = encryptedPrefsManager.getAuxBaseUrl()
            val auxApiKey = encryptedPrefsManager.getAuxApiKey()
            if (auxBaseUrl.isBlank() || auxApiKey.isNullOrBlank()) {
                return getMainApiService()
            }
            val service = createRetrofit(
                baseUrl = auxBaseUrl,
                apiKey = auxApiKey
            ).create(ChatApiService::class.java)
            auxApiService = service
            return service
        }
    }

    fun getVisionApiService(): ChatApiService {
        visionApiService?.let { return it }
        synchronized(this) {
            visionApiService?.let { return it }
            val visionBaseUrl = encryptedPrefsManager.getVisionBaseUrl()
            val visionApiKey = encryptedPrefsManager.getVisionApiKey()
            if (visionBaseUrl.isBlank() || visionApiKey.isNullOrBlank()) {
                return getMainApiService()
            }
            val service = createRetrofit(
                baseUrl = visionBaseUrl,
                apiKey = visionApiKey
            ).create(ChatApiService::class.java)
            visionApiService = service
            return service
        }
    }

    fun getModelApiService(modelConfig: ModelConfigItem): ChatApiService {
        val cacheKey = "${modelConfig.baseUrl}|${modelConfig.apiKey}"
        synchronized(this) {
            dynamicServices[cacheKey]?.let { return it }
            if (modelConfig.baseUrl.isBlank() || modelConfig.apiKey.isBlank()) {
                return getMainApiService()
            }
            val service = createRetrofit(
                baseUrl = modelConfig.baseUrl,
                apiKey = modelConfig.apiKey
            ).create(ChatApiService::class.java)
            dynamicServices[cacheKey] = service
            return service
        }
    }

    fun invalidateDynamicCache() {
        synchronized(this) {
            dynamicServices.clear()
            dynamicRetrofits.clear()
        }
    }

    fun getMainProtocolStyle(): String = encryptedPrefsManager.getMainProtocolStyle()

    fun getAuxProtocolStyle(): String = encryptedPrefsManager.getAuxProtocolStyle()

    fun getVisionProtocolStyle(): String = encryptedPrefsManager.getVisionProtocolStyle()

    suspend fun sendChatMessage(request: ChatRequest): ResponseBody {
        return getMainApiService().streamChatCompletions(request)
    }

    suspend fun sendAuxChatMessage(request: ChatRequest): ChatResponse {
        return getAuxApiService().chatCompletions(request)
    }

    suspend fun uploadAudioFile(
        file: File,
        model: String,
        mimeType: String
    ): ResponseBody = withContext(Dispatchers.IO) {
        _uploadProgress.value = UploadProgress(0, 0, file.length())

        val requestBody = ProgressRequestBody(file, mimeType) { bytesWritten, totalBytes ->
            val percent = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else 0
            _uploadProgress.value = UploadProgress(percent, bytesWritten, totalBytes)
        }

        val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
        val modelBody = model.toRequestBody("text/plain".toMediaTypeOrNull())

        val result = getMainApiService().uploadAudioForTranscription(filePart, modelBody)
        _uploadProgress.value = UploadProgress(100, file.length(), file.length())
        result
    }

    suspend fun uploadAudioFile(
        file: File,
        modelConfig: ModelConfigItem,
        mimeType: String
    ): ResponseBody = withContext(Dispatchers.IO) {
        _uploadProgress.value = UploadProgress(0, 0, file.length())

        val requestBody = ProgressRequestBody(file, mimeType) { bytesWritten, totalBytes ->
            val percent = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else 0
            _uploadProgress.value = UploadProgress(percent, bytesWritten, totalBytes)
        }

        val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
        val modelBody = modelConfig.modelName.toRequestBody("text/plain".toMediaTypeOrNull())

        val result = getModelApiService(modelConfig).uploadAudioForTranscription(filePart, modelBody)
        _uploadProgress.value = UploadProgress(100, file.length(), file.length())
        result
    }

    suspend fun uploadFileForPreUpload(
        file: File,
        purpose: String = "assistants",
        mimeType: String
    ): FileUploadResponse = withContext(Dispatchers.IO) {
        val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
        val purposeBody = purpose.toRequestBody("text/plain".toMediaTypeOrNull())

        getMainApiService().uploadFile(filePart, purposeBody)
    }

    fun invalidateMainCache() {
        synchronized(this) {
            mainRetrofit = null
            mainApiService = null
        }
    }

    fun invalidateAuxCache() {
        synchronized(this) {
            auxRetrofit = null
            auxApiService = null
        }
    }

    fun invalidateVisionCache() {
        synchronized(this) {
            visionRetrofit = null
            visionApiService = null
        }
    }

    fun invalidateAll() {
        invalidateMainCache()
        invalidateAuxCache()
        invalidateVisionCache()
    }

    private fun createRetrofit(baseUrl: String, apiKey: String?): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                if (original.header("Content-Type") == null) {
                    requestBuilder.addHeader("Content-Type", "application/json")
                }

                if (!apiKey.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val url = baseUrl.trimEnd('/').removeSuffix("/v1").removeSuffix("/v1/") + "/"

        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
