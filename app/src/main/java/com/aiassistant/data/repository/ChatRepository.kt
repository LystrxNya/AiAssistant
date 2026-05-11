package com.aiassistant.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.ModelConfigItem
import com.aiassistant.data.network.ApiClient
import com.aiassistant.data.network.model.ChatMessage
import com.aiassistant.data.network.model.ChatRequest
import com.aiassistant.data.network.model.ResponsesInputMessage
import com.aiassistant.data.network.model.ResponsesRequest
import com.aiassistant.data.network.parser.StreamEvent
import com.aiassistant.data.network.parser.StreamResult
import com.aiassistant.data.network.parser.SseStreamParser
import com.aiassistant.data.network.parser.ResponsesApiStreamParser
import com.aiassistant.data.network.model.ToolDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val apiClient: ApiClient,
    private val sseParser: SseStreamParser,
    private val responsesParser: ResponsesApiStreamParser
) {

    fun streamChat(
        messages: List<ChatMessage>,
        temperature: Float? = null,
        topP: Float? = null,
        modelType: String = "main",
        tools: List<ToolDefinition>? = null,
        extraBody: Map<String, Any>? = null
    ): Flow<StreamResult> = flow {
        try {
            val apiService = when (modelType) {
                "aux" -> apiClient.getAuxApiService()
                "vision" -> apiClient.getVisionApiService()
                else -> apiClient.getMainApiService()
            }
            val modelName = when (modelType) {
                "aux" -> encryptedPrefsManager.getAuxModelName().ifEmpty { encryptedPrefsManager.getModelName() }
                "vision" -> encryptedPrefsManager.getVisionModelName().ifEmpty { encryptedPrefsManager.getModelName() }
                else -> encryptedPrefsManager.getModelName()
            }
            val request = ChatRequest(
                model = modelName,
                messages = messages,
                stream = true,
                temperature = temperature,
                topP = topP,
                tools = tools,
                extraBody = extraBody
            )
            val responseBody = apiService.streamChatCompletions(request)
            sseParser.parseStream(responseBody).collect { event ->
                when (event) {
                    is StreamEvent.Content -> emit(StreamResult(content = event.text))
                    is StreamEvent.Thinking -> emit(StreamResult(thinking = event.text))
                    is StreamEvent.Done -> emit(StreamResult(isDone = true))
                    is StreamEvent.Error -> emit(StreamResult(error = event.exception.message))
                    is StreamEvent.ToolProgress -> emit(StreamResult(toolProgress = event.message))
                    is StreamEvent.ToolCalls -> emit(StreamResult(toolCalls = event.toolCalls))
                    is StreamEvent.Finish -> {}
                }
            }
        } catch (e: Exception) {
            emit(StreamResult(error = e.message ?: "Unknown error"))
        }
    }

    fun streamChatWithModel(
        modelConfig: ModelConfigItem,
        messages: List<ChatMessage>,
        temperature: Float? = null,
        topP: Float? = null,
        tools: List<ToolDefinition>? = null,
        toolChoice: String? = null,
        extraBody: Map<String, Any>? = null
    ): Flow<StreamResult> = flow {
        try {
            val apiService = apiClient.getModelApiService(modelConfig)
            val request = ChatRequest(
                model = modelConfig.modelName,
                messages = messages,
                stream = true,
                temperature = temperature,
                topP = topP,
                tools = tools,
                toolChoice = toolChoice,
                extraBody = extraBody
            )
            val responseBody = apiService.streamChatCompletions(request)
            sseParser.parseStream(responseBody).collect { event ->
                when (event) {
                    is StreamEvent.Content -> emit(StreamResult(content = event.text))
                    is StreamEvent.Thinking -> emit(StreamResult(thinking = event.text))
                    is StreamEvent.Done -> emit(StreamResult(isDone = true))
                    is StreamEvent.Error -> emit(StreamResult(error = event.exception.message))
                    is StreamEvent.ToolProgress -> emit(StreamResult(toolProgress = event.message))
                    is StreamEvent.ToolCalls -> emit(StreamResult(toolCalls = event.toolCalls))
                    is StreamEvent.Finish -> {}
                }
            }
        } catch (e: Exception) {
            emit(StreamResult(error = e.message ?: "Unknown error"))
        }
    }

    fun streamResponsesWithModel(
        modelConfig: ModelConfigItem,
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>? = null,
        enableThinking: Boolean? = null,
        reasoning: Map<String, Any>? = null
    ): Flow<StreamResult> = flow {
        try {
            val apiService = apiClient.getModelApiService(modelConfig)
            val inputMessages = mutableListOf<Any>()
            for (msg in messages) {
                when {
                    // Function call output (tool result) → Responses API format
                    msg.role == "tool" && msg.toolCallId != null -> {
                        inputMessages.add(mapOf(
                            "type" to "function_call_output",
                            "call_id" to msg.toolCallId,
                            "output" to msg.content.toString()
                        ))
                    }
                    // Assistant message with tool_calls → Responses API function_call items
                    msg.role == "assistant" && msg.toolCalls != null -> {
                        for (tc in msg.toolCalls) {
                            val func = tc["function"] as? Map<*, *> ?: continue
                            inputMessages.add(mapOf(
                                "type" to "function_call",
                                "name" to (func["name"] ?: ""),
                                "arguments" to (func["arguments"] ?: ""),
                                "call_id" to (tc["id"] ?: "")
                            ))
                        }
                        // Also include text content if present
                        val content = msg.content
                        val text = when (content) {
                            is String -> content
                            else -> content.toString()
                        }
                        if (text.isNotBlank()) {
                            inputMessages.add(ResponsesInputMessage(role = "assistant", content = text))
                        }
                    }
                    // Regular messages
                    else -> {
                        val content = msg.content
                        if (content is List<*>) {
                            val convertedParts = content.mapNotNull { part ->
                                if (part is Map<*, *>) {
                                    when (part["type"]) {
                                        "image_url" -> {
                                            val imageUrl = (part["image_url"] as? Map<*, *>)?.get("url") as? String
                                            if (imageUrl != null) {
                                                mapOf(
                                                    "type" to "input_image",
                                                    "image_url" to imageUrl
                                                )
                                            } else null
                                        }
                                        "text" -> {
                                            mapOf(
                                                "type" to "input_text",
                                                "text" to (part["text"] as? String ?: "")
                                            )
                                        }
                                        else -> null
                                    }
                                } else null
                            }
                            inputMessages.add(ResponsesInputMessage(role = msg.role, content = convertedParts))
                        } else {
                            inputMessages.add(ResponsesInputMessage(role = msg.role, content = content.toString()))
                        }
                    }
                }
            }
            val request = ResponsesRequest(
                model = modelConfig.modelName,
                input = inputMessages,
                stream = true,
                tools = tools,
                enableThinking = enableThinking,
                reasoning = reasoning
            )
            val responseBody = apiService.streamResponses(request)
            responsesParser.parseStream(responseBody).collect { event ->
                when (event) {
                    is StreamEvent.Content -> emit(StreamResult(content = event.text))
                    is StreamEvent.Thinking -> emit(StreamResult(thinking = event.text))
                    is StreamEvent.Done -> emit(StreamResult(isDone = true))
                    is StreamEvent.Error -> emit(StreamResult(error = event.exception.message))
                    is StreamEvent.ToolProgress -> emit(StreamResult(toolProgress = event.message))
                    is StreamEvent.ToolCalls -> emit(StreamResult(toolCalls = event.toolCalls))
                    is StreamEvent.Finish -> {}
                }
            }
        } catch (e: Exception) {
            emit(StreamResult(error = e.message ?: "Unknown error"))
        }
    }

    fun buildMessages(
        systemPrompt: String,
        conversationMessages: List<Pair<String, String>>
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotEmpty()) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }
        for ((role, content) in conversationMessages) {
            messages.add(ChatMessage(role = role, content = content))
        }
        return messages
    }

    fun buildMessagesWithImages(
        systemPrompt: String,
        conversationMessages: List<Triple<String, String, List<String>>>
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotEmpty()) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }
        for ((role, text, images) in conversationMessages) {
            val contentParts = mutableListOf<Map<String, Any>>()
            if (images.isNotEmpty()) {
                for (base64 in images) {
                    val dataUri = if (base64.startsWith("data:")) base64
                        else "data:image/jpeg;base64,$base64"
                    contentParts.add(
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to dataUri)
                        )
                    )
                }
            }
            contentParts.add(mapOf("type" to "text", "text" to text))
            messages.add(ChatMessage(role = role, content = contentParts))
        }
        return messages
    }

    fun processImageUris(uris: List<Uri>): List<String> {
        return uris.mapNotNull { uri -> processImageUri(uri) }
    }

    private fun processImageUri(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null
            val maxSize = 1024
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxSize || height > maxSize) {
                maxSize.toFloat() / maxOf(width, height)
            } else {
                1.0f
            }
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            val resizedBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true).also {
                    originalBitmap.recycle()
                }
            } else {
                originalBitmap
            }
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            resizedBitmap.recycle()
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun getFileSizeMB(uri: Uri): Double {
        return try {
            var sizeBytes: Long = 0
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        sizeBytes = it.getLong(sizeIndex)
                    }
                }
            }
            if (sizeBytes == 0L) {
                val stream = context.contentResolver.openInputStream(uri)
                sizeBytes = stream?.available()?.toLong() ?: 0
                stream?.close()
            }
            sizeBytes / (1024.0 * 1024.0)
        } catch (e: Exception) {
            0.0
        }
    }

    fun isMainConfigured(): Boolean = encryptedPrefsManager.getApiKey("default") != null

    fun isAuxConfigured(): Boolean = encryptedPrefsManager.getAuxApiKey() != null

    fun isVisionConfigured(): Boolean = encryptedPrefsManager.getVisionApiKey() != null

    fun getMainModelName(): String = encryptedPrefsManager.getModelName()
    fun getAuxModelName(): String = encryptedPrefsManager.getAuxModelName()
    fun getVisionModelName(): String = encryptedPrefsManager.getVisionModelName()

    fun getModelById(modelId: String): ModelConfigItem? = encryptedPrefsManager.getModelById(modelId)

    fun getAvailableModels(): List<ModelConfigItem> = encryptedPrefsManager.getModelsList()
}
