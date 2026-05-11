package com.aiassistant.data.repository

import android.content.Context
import android.net.Uri
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.network.ApiClient
import com.aiassistant.data.network.model.FileUploadResponse
import com.aiassistant.manager.AudioTranscoder
import com.aiassistant.manager.ImageCompressor
import com.aiassistant.manager.SandboxResult
import com.aiassistant.manager.TranscodeState
import com.aiassistant.manager.UriSandboxManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class ImageProcessResult {
    data class Success(val base64List: List<String>) : ImageProcessResult()
    data class Error(val message: String) : ImageProcessResult()
}

sealed class AudioProcessResult {
    data class Uploading(val percent: Int) : AudioProcessResult()
    data class Transcribing(val text: String = "") : AudioProcessResult()
    data class Cleaning(val text: String = "") : AudioProcessResult()
    data class Success(val text: String) : AudioProcessResult()
    data class Error(val message: String) : AudioProcessResult()
}

@Singleton
class FileUploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: ApiClient,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val uriSandboxManager: UriSandboxManager,
    private val imageCompressor: ImageCompressor,
    private val audioTranscoder: AudioTranscoder
) {

    suspend fun processImages(uris: List<Uri>): ImageProcessResult = withContext(Dispatchers.IO) {
        try {
            if (!imageCompressor.validateImageCount(uris.size)) {
                return@withContext ImageProcessResult.Error("图片数量必须在1-9张之间")
            }

            val sandboxResults = uriSandboxManager.sandboxMultipleFiles(uris, "image/*")
            val successFiles = sandboxResults.filterIsInstance<SandboxResult.Success>().map { it.file }
            val errors = sandboxResults.filterIsInstance<SandboxResult.Error>()

            if (successFiles.isEmpty()) {
                return@withContext ImageProcessResult.Error("所有图片处理失败: ${errors.firstOrNull()?.message}")
            }

            val compressedResults = imageCompressor.compressMultipleImages(successFiles)
            val base64List = compressedResults.map { it.base64 }

            successFiles.forEach { uriSandboxManager.cleanupSpecificFile(it) }
            compressedResults.forEach { uriSandboxManager.cleanupSpecificFile(it.file) }

            ImageProcessResult.Success(base64List)
        } catch (e: Exception) {
            ImageProcessResult.Error("图片处理失败: ${e.message}")
        }
    }

    fun processAudioUpload(uri: Uri): Flow<AudioProcessResult> = flow {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
            if (!audioTranscoder.isFormatSupported(mimeType)) {
                emit(AudioProcessResult.Error("不支持的音频格式: $mimeType"))
                return@flow
            }

            val sandboxResult = uriSandboxManager.sandboxFile(uri, mimeType)
            val sourceFile = when (sandboxResult) {
                is SandboxResult.Success -> sandboxResult.file
                is SandboxResult.Error -> {
                    emit(AudioProcessResult.Error(sandboxResult.message))
                    return@flow
                }
            }

            if (sourceFile.length() > audioTranscoder.getFileSizeLimit()) {
                emit(AudioProcessResult.Error("音频文件超过20MB限制，请压缩后重试"))
                uriSandboxManager.cleanupSpecificFile(sourceFile)
                return@flow
            }

            // Resolve STT model config; fall back to main model if not configured
            val sttModelId = encryptedPrefsManager.getSttModelId()
            val sttModelConfig = if (sttModelId.isNotBlank()) {
                encryptedPrefsManager.getModelById(sttModelId)
            } else null

            val protocolStyle = sttModelConfig?.protocolStyle
                ?: apiClient.getMainProtocolStyle()
            val model = sttModelConfig?.modelName
                ?: encryptedPrefsManager.getModelName()

            if (protocolStyle == EncryptedPrefsManager.PROTOCOL_OPENAI_COMPATIBLE) {
                try {
                    emit(AudioProcessResult.Uploading(0))
                    val responseBody = if (sttModelConfig != null) {
                        apiClient.uploadAudioFile(sourceFile, sttModelConfig, mimeType)
                    } else {
                        apiClient.uploadAudioFile(sourceFile, model, mimeType)
                    }
                    responseBody.use { body ->
                        emit(AudioProcessResult.Uploading(100))
                        val responseText = body.string()
                        val text = extractTranscriptionText(responseText)
                        emit(AudioProcessResult.Transcribing(text))
                    }
                } catch (e: Exception) {
                    emit(AudioProcessResult.Error("音频上传转写失败: ${e.message}"))
                }
            } else {
                var lastProgress = 0
                audioTranscoder.transcodeAudio(sourceFile, mimeType).collect { state ->
                    when (state) {
                        is TranscodeState.Transcoding -> {
                            if (state.progress != lastProgress) {
                                emit(AudioProcessResult.Uploading(state.progress))
                                lastProgress = state.progress
                            }
                        }
                        is TranscodeState.Success -> {
                            emit(AudioProcessResult.Uploading(100))
                            emit(AudioProcessResult.Transcribing(state.base64))
                        }
                        is TranscodeState.Error -> {
                            emit(AudioProcessResult.Error(state.message))
                        }
                        is TranscodeState.Idle -> {}
                    }
                }
            }

            uriSandboxManager.cleanupSpecificFile(sourceFile)
        } catch (e: Exception) {
            emit(AudioProcessResult.Error("音频处理失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun preUploadFile(file: File, mimeType: String): Result<FileUploadResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiClient.uploadFileForPreUpload(file, "assistants", mimeType)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun extractTranscriptionText(response: String): String {
        return try {
            val gson = com.google.gson.Gson()
            val jsonElement = gson.fromJson(response, com.google.gson.JsonElement::class.java)
            jsonElement.asJsonObject.get("text")?.asString ?: response
        } catch (_: Exception) {
            response
        }
    }
}
