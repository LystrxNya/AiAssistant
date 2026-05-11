package com.aiassistant.data.network.parser

import com.aiassistant.data.network.model.ResponsesStreamEvent
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponsesApiStreamParser @Inject constructor(
    private val gson: Gson
) {

    fun parseStream(body: ResponseBody): Flow<StreamEvent> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                val source = body.source()
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

                        processChunk(chunk).forEach { event ->
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
                    processChunk(buffer.toString()).forEach { send(it) }
                }
                // Emit any pending tool calls before closing
                emitPendingToolCalls().forEach { send(it) }
                // Ensure flow always terminates when stream ends
                send(StreamEvent.Done)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    send(StreamEvent.Error(IOException("Responses stream read error", e)))
                }
            } finally {
                try { body.close() } catch (_: Exception) {}
            }
        }

        awaitClose { job.cancel() }
    }

    // Track pending function calls from Responses API streaming events
    private data class PendingFunctionCall(
        var itemId: String = "",
        var name: String = "",
        var callId: String = "",
        var arguments: StringBuilder = StringBuilder()
    )

    private val pendingFunctionCalls = mutableMapOf<Int, PendingFunctionCall>()

    private fun emitPendingToolCalls(): List<StreamEvent> {
        if (pendingFunctionCalls.isEmpty()) return emptyList()
        val completedCalls = pendingFunctionCalls.values
            .filter { it.name.isNotEmpty() }
            .map { CompletedToolCall(id = it.callId.ifEmpty { it.itemId }, functionName = it.name, arguments = it.arguments.toString()) }
        pendingFunctionCalls.clear()
        return if (completedCalls.isNotEmpty()) listOf(StreamEvent.ToolCalls(completedCalls)) else emptyList()
    }

    private fun processChunk(chunk: String): List<StreamEvent> {
        val lines = chunk.lines()
        var eventType: String? = null
        var dataLine: String? = null

        for (line in lines) {
            when {
                line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                line.startsWith("data:") -> dataLine = line.removePrefix("data:").trim()
            }
        }

        if (dataLine.isNullOrBlank()) return emptyList()
        if (dataLine == "[DONE]") return listOf(StreamEvent.Done)

        return try {
            val event = gson.fromJson(dataLine, ResponsesStreamEvent::class.java)
            android.util.Log.d("ResponsesParser", "Event: ${event.type}, delta=${event.delta?.take(50)}, name=${event.name}, callId=${event.callId}, args=${event.arguments?.take(50)}")
            when (event.type) {
                "response.reasoning_summary_text.delta" -> {
                    val text = event.delta
                    if (!text.isNullOrBlank()) listOf(StreamEvent.Thinking(text)) else emptyList()
                }
                "response.output_text.delta" -> {
                    val text = event.delta
                    if (!text.isNullOrBlank()) listOf(StreamEvent.Content(text)) else emptyList()
                }
                "response.completed" -> {
                    // Emit pending tool calls, then Done
                    val events = mutableListOf<StreamEvent>()
                    events.addAll(emitPendingToolCalls())
                    events.add(StreamEvent.Done)
                    events
                }
                "response.failed" -> {
                    listOf(StreamEvent.Error(IOException("Responses API error")))
                }
                // Function call events (Responses API)
                "response.function_call.created" -> {
                    val index = event.outputIndex ?: 0
                    val pending = PendingFunctionCall(
                        itemId = event.itemId ?: "",
                        name = event.name ?: "",
                        callId = event.callId ?: ""
                    )
                    pendingFunctionCalls[index] = pending
                    emptyList()
                }
                "response.function_call_arguments.delta" -> {
                    val index = event.outputIndex ?: 0
                    val pending = pendingFunctionCalls.getOrPut(index) { PendingFunctionCall() }
                    event.itemId?.let { pending.itemId = it }
                    event.callId?.let { pending.callId = it }
                    event.delta?.let { pending.arguments.append(it) }
                    emptyList()
                }
                "response.function_call_arguments.done" -> {
                    val index = event.outputIndex ?: 0
                    val pending = pendingFunctionCalls.getOrPut(index) { PendingFunctionCall() }
                    event.arguments?.let { pending.arguments = StringBuilder(it) }
                    event.callId?.let { pending.callId = it }
                    event.itemId?.let { pending.itemId = it }
                    emptyList()
                }
                "response.reasoning_summary_text.done",
                "response.output_text.done",
                "response.created",
                "response.in_progress",
                "response.output_item.added" -> {
                    // Capture itemId from output_item.added for function_call items
                    val item = event.item
                    if (item?.type == "function_call") {
                        val index = event.outputIndex ?: 0
                        val pending = pendingFunctionCalls.getOrPut(index) { PendingFunctionCall() }
                        item.id?.let { pending.itemId = it }
                        item.name?.let { pending.name = it }
                    }
                    emptyList()
                }
                "response.output_item.done" -> {
                    // Extract call_id from completed function_call output item
                    val item = event.item
                    if (item?.type == "function_call") {
                        val index = event.outputIndex ?: 0
                        val pending = pendingFunctionCalls[index]
                        if (pending != null) {
                            item.callId?.let { pending.callId = it }
                            item.id?.let { pending.itemId = it }
                        }
                    }
                    emptyList()
                }
                "response.content_part.added",
                "response.content_part.done" -> emptyList()
                "response.web_search_call.in_progress",
                "response.web_search_call.searching" -> {
                    listOf(StreamEvent.ToolProgress("正在搜索网络..."))
                }
                "response.web_search_call.completed" -> {
                    listOf(StreamEvent.ToolProgress("搜索完成"))
                }
                "response.code_interpreter_call.in_progress",
                "response.code_interpreter_call.interpreting" -> {
                    listOf(StreamEvent.ToolProgress("正在执行代码..."))
                }
                "response.code_interpreter_call.completed" -> {
                    listOf(StreamEvent.ToolProgress("代码执行完成"))
                }
                else -> {
                    val text = event.delta
                    if (!text.isNullOrBlank()) listOf(StreamEvent.Content(text)) else emptyList()
                }
            }
        } catch (e: Exception) {
            listOf(StreamEvent.Error(IOException("Responses JSON parse error: $dataLine", e)))
        }
    }
}
