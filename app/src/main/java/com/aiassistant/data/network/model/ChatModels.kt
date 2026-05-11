package com.aiassistant.data.network.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

class ChatRequestSerializer : JsonSerializer<ChatRequest> {
    override fun serialize(src: ChatRequest, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("model", src.model)
        obj.add("messages", context.serialize(src.messages))
        obj.addProperty("stream", src.stream)
        src.temperature?.let { obj.addProperty("temperature", it) }
        src.topP?.let { obj.addProperty("top_p", it) }
        src.maxTokens?.let { obj.addProperty("max_tokens", it) }
        src.tools?.let { obj.add("tools", context.serialize(it)) }
        src.toolChoice?.let { obj.addProperty("tool_choice", it) }
        src.extraBody?.forEach { (key, value) ->
            obj.add(key, context.serialize(value))
        }
        return obj
    }
}

class ChatMessageSerializer : JsonSerializer<ChatMessage> {
    override fun serialize(src: ChatMessage, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("role", src.role)
        when (val content = src.content) {
            is String -> {
                if (content.isEmpty() && src.toolCalls != null) {
                    obj.add("content", null)
                } else {
                    obj.addProperty("content", content)
                }
            }
            else -> obj.add("content", context.serialize(content))
        }
        src.toolCallId?.let { obj.addProperty("tool_call_id", it) }
        src.name?.let { obj.addProperty("name", it) }
        src.toolCalls?.let { obj.add("tool_calls", context.serialize(it)) }
        src.reasoningContent?.let { obj.addProperty("reasoning_content", it) }
        return obj
    }
}

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerializedName("top_p")
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val tools: List<ToolDefinition>? = null,
    @SerializedName("tool_choice")
    val toolChoice: String? = null,
    val extraBody: Map<String, Any>? = null
)

/**
 * Represents a chat message sent to or received from the API.
 *
 * @property role The message role (e.g., "system", "user", "assistant").
 * @property content The message content. Accepted types:
 *   - [String] for plain text messages
 *   - [List] of content part objects (e.g., [TextContent], [ImageContent]) for multimodal messages
 *   - `null` for assistant messages where content is not yet available (streaming delta)
 */
data class ChatMessage(
    val role: String,
    val content: Any,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<Map<String, Any>>? = null,
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null
)

data class TextContent(
    val type: String,
    val text: String
)

data class ImageContent(
    val type: String,
    @SerializedName("image_url")
    val imageUrl: ImageUrl
)

data class ImageUrl(
    val url: String
)

data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDef
)

data class FunctionDef(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class ResponsesRequest(
    val model: String,
    val input: List<Any>,
    val stream: Boolean = true,
    val tools: List<Map<String, Any>>? = null,
    @SerializedName("enable_thinking")
    val enableThinking: Boolean? = null,
    val reasoning: Map<String, Any>? = null
)

data class ResponsesInputMessage(
    val role: String,
    val content: Any
)

data class ResponsesStreamEvent(
    val type: String,
    val delta: String? = null,
    val response: ResponsesOutput? = null,
    @SerializedName("sequence_number")
    val sequenceNumber: Int? = null,
    // Fields for function_call events
    @SerializedName("item_id")
    val itemId: String? = null,
    val name: String? = null,
    @SerializedName("call_id")
    val callId: String? = null,
    val arguments: String? = null,
    @SerializedName("output_index")
    val outputIndex: Int? = null,
    // Field for output_item events (contains the complete item)
    val item: ResponsesOutputItem? = null
)

data class ResponsesOutput(
    val id: String?,
    val output: List<ResponsesOutputItem>?,
    val usage: ResponsesUsage?,
    val status: String?
)

data class ResponsesOutputItem(
    val type: String?,
    val id: String?,
    val role: String?,
    val content: List<ResponsesContentPart>?,
    // Fields for function_call output items
    val name: String?,
    val arguments: String?,
    @SerializedName("call_id")
    val callId: String?
)

data class ResponsesContentPart(
    val type: String?,
    val text: String?
)

data class ResponsesUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int?,
    @SerializedName("output_tokens")
    val outputTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)

data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int?,
    @SerializedName("completion_tokens")
    val completionTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)

data class StreamChunk(
    val id: String?,
    val choices: List<StreamChoice>?
)

data class StreamChoice(
    val index: Int,
    val delta: StreamDelta?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class StreamDelta(
    val role: String?,
    val content: String?,
    @SerializedName("reasoning_content")
    val reasoningContent: String?,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCallDelta>?
)

data class ToolCallDelta(
    val index: Int,
    val id: String?,
    val type: String?,
    val function: FunctionCallDelta?
)

data class FunctionCallDelta(
    val name: String?,
    val arguments: String?
)

data class FileUploadResponse(
    val id: String?,
    @SerializedName("object")
    val objectType: String?,
    @SerializedName("bytes")
    val sizeBytes: Long?,
    @SerializedName("created_at")
    val createdAt: Long?,
    val filename: String?,
    val purpose: String?
)
