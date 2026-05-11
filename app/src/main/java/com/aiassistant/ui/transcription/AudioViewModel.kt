package com.aiassistant.ui.transcription

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.dao.GeneratedMarkdownDao
import com.aiassistant.data.entity.GeneratedMarkdownEntity
import com.aiassistant.data.network.model.PromptTemplates
import com.aiassistant.data.repository.AudioProcessResult
import com.aiassistant.data.repository.ChatRepository
import com.aiassistant.data.repository.FileUploadRepository
import com.aiassistant.manager.FileDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

sealed class AudioStep {
    data object IDLE : AudioStep()
    data class UPLOADING(val percent: Int) : AudioStep()
    data object AI_TRANSCRIBING : AudioStep()
    data class AI_CLEANING(val processed: Int, val total: Int) : AudioStep()
    data object SUCCESS : AudioStep()
    data class ERROR(val message: String) : AudioStep()
}

sealed class AudioEvent {
    data class ShowToast(val message: String) : AudioEvent()
}

private const val SHORT_AUDIO_THRESHOLD = 8000
private const val CHUNK_SIZE = 4000
private const val CHUNK_CONCURRENCY = 5
private const val TAIL_SIZE = 500

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val fileUploadRepository: FileUploadRepository,
    private val chatRepository: ChatRepository,
    private val generatedMarkdownDao: GeneratedMarkdownDao,
    private val fileDownloader: FileDownloader
) : ViewModel() {

    private val _audioStep = MutableStateFlow<AudioStep>(AudioStep.IDLE)
    val audioStep: StateFlow<AudioStep> = _audioStep.asStateFlow()

    private val _markdownResult = MutableStateFlow("")
    val markdownResult: StateFlow<String> = _markdownResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _events = MutableSharedFlow<AudioEvent>()
    val events: SharedFlow<AudioEvent> = _events.asSharedFlow()

    private var pendingAudioUri: Uri? = null

    fun selectAudio(uri: Uri) {
        pendingAudioUri = uri
        _audioStep.value = AudioStep.IDLE
        _markdownResult.value = ""
        _errorMessage.value = null
    }

    fun startProcessing() {
        val uri = pendingAudioUri
        if (uri == null) {
            _errorMessage.value = "请先选择录音文件"
            return
        }

        viewModelScope.launch {
            try {
                _markdownResult.value = ""
                _errorMessage.value = null

                var rawTranscript = ""

                fileUploadRepository.processAudioUpload(uri).collect { result ->
                    when (result) {
                        is AudioProcessResult.Uploading -> {
                            _audioStep.value = AudioStep.UPLOADING(result.percent)
                        }
                        is AudioProcessResult.Transcribing -> {
                            _audioStep.value = AudioStep.AI_TRANSCRIBING
                            rawTranscript = result.text
                        }
                        is AudioProcessResult.Cleaning -> {
                            _audioStep.value = AudioStep.AI_CLEANING(0, 0)
                        }
                        is AudioProcessResult.Success -> {
                            rawTranscript = result.text
                        }
                        is AudioProcessResult.Error -> {
                            throw Exception(result.message)
                        }
                    }
                }

                if (rawTranscript.isBlank()) {
                    throw Exception("转写结果为空，请检查音频质量或模型配置")
                }

                stageCleaning(rawTranscript)
            } catch (e: Exception) {
                _audioStep.value = AudioStep.ERROR(e.message ?: "处理失败")
            }
        }
    }

    private suspend fun stageCleaning(rawTranscript: String) {
        if (!chatRepository.isAuxConfigured()) {
            _markdownResult.value = rawTranscript
            _audioStep.value = AudioStep.SUCCESS
            saveToRoom(rawTranscript)
            return
        }

        if (rawTranscript.length < SHORT_AUDIO_THRESHOLD) {
            cleanShortAudio(rawTranscript)
        } else {
            cleanLongAudio(rawTranscript)
        }
    }

    private suspend fun cleanShortAudio(rawTranscript: String) {
        _audioStep.value = AudioStep.AI_CLEANING(1, 1)

        try {
            val messages = chatRepository.buildMessages(
                systemPrompt = PromptTemplates.AUDIO_SHORT_CLEAN,
                conversationMessages = listOf("user" to rawTranscript)
            )

            var result = ""
            chatRepository.streamChat(
                messages = messages,
                temperature = 0.3f,
                modelType = "aux"
            ).collect { streamResult ->
                if (streamResult.error != null) {
                    throw Exception(streamResult.error)
                }
                if (streamResult.isDone) return@collect
                if (streamResult.content.isNotEmpty()) {
                    result += streamResult.content
                }
            }

            val finalMarkdown = result.ifBlank { rawTranscript }
            _markdownResult.value = finalMarkdown
            _audioStep.value = AudioStep.SUCCESS
            saveToRoom(finalMarkdown)
        } catch (e: Exception) {
            _markdownResult.value = rawTranscript
            _audioStep.value = AudioStep.SUCCESS
            saveToRoom(rawTranscript)
            _events.emit(AudioEvent.ShowToast("AI清洗失败，已保存原始转写结果: ${e.message}"))
        }
    }

    private suspend fun cleanLongAudio(rawTranscript: String) {
        _audioStep.value = AudioStep.AI_CLEANING(0, 0)

        try {
            val chunks = splitIntoChunks(rawTranscript, CHUNK_SIZE)
            val totalChunks = chunks.size
            val cleanedChunks = arrayOfNulls<String>(totalChunks)
            val semaphore = Semaphore(CHUNK_CONCURRENCY)
            var processedCount = 0

            coroutineScope {
                chunks.mapIndexed { index, chunk ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val cleaned = cleanSingleChunk(chunk, index, chunks)
                            cleanedChunks[index] = cleaned
                            synchronized(this) {
                                processedCount++
                                _audioStep.value = AudioStep.AI_CLEANING(processedCount, totalChunks)
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalMarkdown = cleanedChunks.filterNotNull().joinToString("\n\n")
            _markdownResult.value = finalMarkdown
            _audioStep.value = AudioStep.SUCCESS
            saveToRoom(finalMarkdown)
        } catch (e: Exception) {
            _markdownResult.value = rawTranscript
            _audioStep.value = AudioStep.SUCCESS
            saveToRoom(rawTranscript)
            _events.emit(AudioEvent.ShowToast("AI清洗部分失败，已保存原始转写结果: ${e.message}"))
        }
    }

    private suspend fun cleanSingleChunk(
        chunk: String,
        index: Int,
        allChunks: List<String>
    ): String {
        val systemPrompt = if (index == 0) {
            PromptTemplates.AUDIO_LONG_CHUNK_FIRST
        } else {
            val prevChunk = allChunks[index - 1]
            val tail = if (prevChunk.length > TAIL_SIZE) {
                prevChunk.takeLast(TAIL_SIZE)
            } else {
                prevChunk
            }
            PromptTemplates.AUDIO_LONG_CHUNK_CONTINUE.replace("{prev_chunk_tail}", tail)
        }

        val messages = chatRepository.buildMessages(
            systemPrompt = systemPrompt,
            conversationMessages = listOf("user" to chunk)
        )

        var result = ""
        chatRepository.streamChat(
            messages = messages,
            temperature = 0.3f,
            modelType = "aux"
        ).collect { streamResult ->
            if (streamResult.error != null) {
                throw Exception(streamResult.error)
            }
            if (streamResult.isDone) return@collect
            if (streamResult.content.isNotEmpty()) {
                result += streamResult.content
            }
        }
        return result.ifBlank { chunk }
    }

    private fun splitIntoChunks(text: String, chunkSize: Int): List<String> {
        if (text.length <= chunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n\n")
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentChunk.length + paragraph.length + 2 > chunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n\n")
            }
            currentChunk.append(paragraph)
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.ifEmpty { listOf(text) }
    }

    private suspend fun saveToRoom(content: String) {
        val entity = GeneratedMarkdownEntity(
            title = "Audio Transcript",
            content = content,
            sourceModule = "transcription",
            createdAt = System.currentTimeMillis()
        )
        generatedMarkdownDao.insert(entity)
    }

    fun downloadMarkdown() {
        val content = _markdownResult.value
        if (content.isBlank()) return

        val fileName = "audio_transcript_${System.currentTimeMillis()}.md"
        viewModelScope.launch {
            val result = fileDownloader.saveMarkdownToDownloads(content, fileName)
            result.onSuccess {
                _events.emit(AudioEvent.ShowToast("已保存到下载目录: $it"))
            }.onFailure {
                _events.emit(AudioEvent.ShowToast("保存失败: ${it.message}"))
            }
        }
    }

    fun reset() {
        pendingAudioUri = null
        _audioStep.value = AudioStep.IDLE
        _markdownResult.value = ""
        _errorMessage.value = null
    }
}
