package com.aiassistant.data.network.parser

import com.aiassistant.data.network.model.StreamChunk
import com.aiassistant.data.network.model.ToolCallDelta
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import okio.BufferedSource
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SseStreamParser @Inject constructor(
    private val gson: Gson
) {

    fun parseStream(body: ResponseBody): Flow<StreamEvent> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                val source: BufferedSource = body.source()
                val buffer = StringBuilder()

                while (!source.exhausted()) {
                    val bytesAvailable = source.request(1)
                    if (!bytesAvailable) break

                    val segment = source.buffer.readUtf8()
                    buffer.append(segment)

                    while (true) {
                        val doubleNewlineIndex = buffer.indexOf("\n\n")
                        if (doubleNewlineIndex == -1) break

                        val chunk = buffer.substring(0, doubleNewlineIndex)
                        buffer.delete(0, doubleNewlineIndex + 2)

                        processSseChunk(chunk).forEach { event ->
                            send(event)
                            if (event is StreamEvent.Done || event is StreamEvent.Error) {
                                source.close()
                                body.close()
                                return@launch
                            }
                        }
                    }
                }

                if (buffer.isNotBlank()) {
                    processSseChunk(buffer.toString()).forEach { send(it) }
                }
                // Ensure flow always terminates when stream ends
                send(StreamEvent.Done)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    send(StreamEvent.Error(IOException("Stream read error", e)))
                }
            } finally {
                try { body.close() } catch (_: Exception) {}
            }
        }

        awaitClose { job.cancel() }
    }

    private val pendingToolCalls = mutableMapOf<Int, PendingToolCall>()

    private data class PendingToolCall(
        var id: String = "",
        var name: String = "",
        var arguments: StringBuilder = StringBuilder()
    )

    private fun processSseChunk(chunk: String): List<StreamEvent> {
        val lines = chunk.lines()
        val dataLines = lines.filter { it.startsWith("data:") }

        if (dataLines.isEmpty()) return emptyList()

        val data = dataLines.joinToString("\n") { line ->
            val stripped = line.removePrefix("data:")
            if (stripped.startsWith(" ")) stripped.substring(1) else stripped
        }

        if (data == "[DONE]") {
            return listOf(StreamEvent.Done)
        }

        if (data.isBlank()) return emptyList()

        return try {
            val streamChunk = gson.fromJson(data, StreamChunk::class.java)
            val delta = streamChunk.choices?.firstOrNull()?.delta
            val finishReason = streamChunk.choices?.firstOrNull()?.finishReason
            android.util.Log.d("SseParser", "delta: content=${delta?.content?.take(30)}, reasoning=${delta?.reasoningContent?.take(30)}, toolCalls=${delta?.toolCalls?.size}, finish=$finishReason")

            val events = mutableListOf<StreamEvent>()

            delta?.toolCalls?.forEach { tcDelta ->
                val idx = tcDelta.index
                val pending = pendingToolCalls.getOrPut(idx) { PendingToolCall() }
                tcDelta.id?.let { pending.id = it }
                tcDelta.function?.name?.let { pending.name = it }
                tcDelta.function?.arguments?.let { pending.arguments.append(it) }
            }

            if (delta != null) {
                if (!delta.reasoningContent.isNullOrBlank()) {
                    events.add(StreamEvent.Thinking(delta.reasoningContent))
                }
                if (!delta.content.isNullOrBlank()) {
                    events.add(StreamEvent.Content(delta.content))
                }
            }

            if (finishReason == "tool_calls" || (finishReason != null && pendingToolCalls.isNotEmpty())) {
                val completedCalls = pendingToolCalls.values
                    .filter { it.id.isNotEmpty() && it.name.isNotEmpty() }
                    .map { CompletedToolCall(id = it.id, functionName = it.name, arguments = it.arguments.toString()) }
                pendingToolCalls.clear()
                if (completedCalls.isNotEmpty()) {
                    events.add(StreamEvent.ToolCalls(completedCalls))
                }
                events.add(StreamEvent.Finish(finishReason ?: "tool_calls"))
            } else if (finishReason != null) {
                pendingToolCalls.clear()
                events.add(StreamEvent.Finish(finishReason))
            }

            events.ifEmpty { emptyList() }
        } catch (e: Exception) {
            listOf(StreamEvent.Error(IOException("JSON parse error: $data", e)))
        }
    }
}

sealed class StreamEvent {
    data class Content(val text: String) : StreamEvent()
    data class Thinking(val text: String) : StreamEvent()
    data class Finish(val reason: String) : StreamEvent()
    data class Error(val exception: IOException) : StreamEvent()
    data class ToolProgress(val message: String) : StreamEvent()
    data class ToolCalls(val toolCalls: List<CompletedToolCall>) : StreamEvent()
    data object Done : StreamEvent()
}

data class CompletedToolCall(
    val id: String,
    val functionName: String,
    val arguments: String
)

data class StreamResult(
    val content: String = "",
    val thinking: String = "",
    val isDone: Boolean = false,
    val error: String? = null,
    val toolProgress: String? = null,
    val toolCalls: List<CompletedToolCall>? = null
)
