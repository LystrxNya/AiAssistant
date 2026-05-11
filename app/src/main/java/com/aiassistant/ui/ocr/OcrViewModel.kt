package com.aiassistant.ui.ocr

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.dao.GeneratedMarkdownDao
import com.aiassistant.data.entity.GeneratedMarkdownEntity
import com.aiassistant.data.repository.ChatRepository
import com.aiassistant.manager.FileDownloader
import com.aiassistant.manager.ImageCompressor
import com.aiassistant.manager.PdfRenderManager
import com.aiassistant.manager.PdfRenderState
import com.aiassistant.manager.SandboxResult
import com.aiassistant.manager.UriSandboxManager
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

sealed class OcrStep {
    data object IDLE : OcrStep()
    data class PDF_RENDERING(val page: Int, val total: Int) : OcrStep()
    data object VISION_RECOGNIZING : OcrStep()
    data class AI_CLEANING(val processed: Int, val total: Int) : OcrStep()
    data object SHOWING_RESULT : OcrStep()
    data class ERROR(val message: String) : OcrStep()
}

sealed class OcrEvent {
    data class ShowToast(val message: String) : OcrEvent()
}

private const val SHORT_PDF_THRESHOLD = 50
private const val CHUNK_SIZE = 4000
private const val CHUNK_CONCURRENCY = 5

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val uriSandboxManager: UriSandboxManager,
    private val imageCompressor: ImageCompressor,
    private val pdfRenderManager: PdfRenderManager,
    private val chatRepository: ChatRepository,
    private val generatedMarkdownDao: GeneratedMarkdownDao,
    private val fileDownloader: FileDownloader
) : ViewModel() {

    private val _ocrStep = MutableStateFlow<OcrStep>(OcrStep.IDLE)
    val ocrStep: StateFlow<OcrStep> = _ocrStep.asStateFlow()

    private val _markdownResult = MutableStateFlow("")
    val markdownResult: StateFlow<String> = _markdownResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _events = MutableSharedFlow<OcrEvent>()
    val events: SharedFlow<OcrEvent> = _events.asSharedFlow()

    private var pendingImageUris: List<Uri> = emptyList()
    private var pendingPdfUri: Uri? = null
    private var currentSandboxFile: File? = null

    fun selectImages(uris: List<Uri>) {
        pendingPdfUri = null
        pendingImageUris = uris
        _ocrStep.value = OcrStep.IDLE
        _markdownResult.value = ""
        _errorMessage.value = null
    }

    fun selectPdf(uri: Uri) {
        pendingImageUris = emptyList()
        pendingPdfUri = uri
        _ocrStep.value = OcrStep.IDLE
        _markdownResult.value = ""
        _errorMessage.value = null
    }

    fun startProcessing() {
        if (pendingImageUris.isEmpty() && pendingPdfUri == null) {
            _errorMessage.value = "请先选择图片或PDF文件"
            return
        }

        viewModelScope.launch {
            try {
                _markdownResult.value = ""
                _errorMessage.value = null

                if (pendingImageUris.isNotEmpty()) {
                    processImageFlow(pendingImageUris)
                } else if (pendingPdfUri != null) {
                    processPdfFlow(pendingPdfUri!!)
                }
            } catch (e: Exception) {
                _ocrStep.value = OcrStep.ERROR(e.message ?: "处理失败")
            }
        }
    }

    private suspend fun processImageFlow(uris: List<Uri>) {
        val base64List = prepareImageBase64(uris)
        if (base64List.isEmpty()) return

        stageVisionRecognition(imageBase64List = base64List, isShortDoc = true)
    }

    private suspend fun processPdfFlow(uri: Uri) {
        val sandboxResult = uriSandboxManager.sandboxFile(uri, "application/pdf")
        val pdfFile = when (sandboxResult) {
            is SandboxResult.Success -> sandboxResult.file
            is SandboxResult.Error -> {
                _ocrStep.value = OcrStep.ERROR(sandboxResult.message)
                return
            }
        }
        currentSandboxFile = pdfFile

        val base64List = renderPdfPagesToBase64(Uri.fromFile(pdfFile))
        if (base64List.isEmpty()) return

        val isShortDoc = base64List.size <= SHORT_PDF_THRESHOLD

        stageVisionRecognition(imageBase64List = base64List, isShortDoc = isShortDoc)
    }

    private suspend fun prepareImageBase64(uris: List<Uri>): List<String> {
        val sandboxResults = uriSandboxManager.sandboxMultipleFiles(uris, "image/*")
        val successFiles = sandboxResults.filterIsInstance<SandboxResult.Success>().map { it.file }

        if (successFiles.isEmpty()) {
            _ocrStep.value = OcrStep.ERROR("图片文件无法读取，请重新选择")
            return emptyList()
        }

        val compressedResults = imageCompressor.compressMultipleImages(successFiles)
        val base64List = compressedResults.map { it.base64 }

        successFiles.forEach { uriSandboxManager.cleanupSpecificFile(it) }
        compressedResults.forEach { uriSandboxManager.cleanupSpecificFile(it.file) }

        return base64List
    }

    private suspend fun renderPdfPagesToBase64(pdfUri: Uri): List<String> {
        _ocrStep.value = OcrStep.PDF_RENDERING(0, 0)

        val base64List = mutableListOf<String>()
        var hasError = false

        pdfRenderManager.renderPdf(pdfUri).collect { state ->
            when (state) {
                is PdfRenderState.Loading -> {
                    _ocrStep.value = OcrStep.PDF_RENDERING(state.currentPage, state.totalPage)
                }
                is PdfRenderState.PageRendered -> {
                    val base64 = withContext(Dispatchers.Default) {
                        try {
                            val outputStream = ByteArrayOutputStream()
                            state.bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                            val bytes = outputStream.toByteArray()
                            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        } finally {
                            state.bitmap.recycle()
                        }
                    }
                    base64List.add(base64)
                }
                is PdfRenderState.Success -> {
                    return@collect
                }
                is PdfRenderState.Error -> {
                    _ocrStep.value = OcrStep.ERROR(state.message)
                    hasError = true
                    return@collect
                }
            }
        }

        if (!hasError && base64List.isEmpty()) {
            _ocrStep.value = OcrStep.ERROR("PDF页面渲染失败")
        }

        return base64List
    }

    private suspend fun stageVisionRecognition(imageBase64List: List<String>, isShortDoc: Boolean) {
        _ocrStep.value = OcrStep.VISION_RECOGNIZING

        if (!chatRepository.isVisionConfigured()) {
            _ocrStep.value = OcrStep.ERROR("请先在设置中配置【重度视觉模型】的 API Key 和地址")
            return
        }

        try {
            val recognizedTexts = coroutineScope {
                imageBase64List.mapIndexed { index, base64 ->
                    async(Dispatchers.IO) {
                        recognizeSingleImage(base64, index + 1)
                    }
                }.awaitAll()
            }

            val fullText = recognizedTexts.joinToString("\n\n---\n\n")

            if (fullText.isBlank()) {
                _ocrStep.value = OcrStep.ERROR("视觉模型未返回有效文字，请检查图片质量或模型配置")
                return
            }

            if (isShortDoc) {
                stageDirectOutput(fullText, imageBase64List.size)
            } else {
                stageChunkedCleaning(fullText, imageBase64List.size)
            }
        } catch (e: Exception) {
            _ocrStep.value = OcrStep.ERROR("视觉识别失败: ${e.message}")
        }
    }

    private suspend fun recognizeSingleImage(base64: String, pageNum: Int): String {
        val messages = chatRepository.buildMessagesWithImages(
            systemPrompt = "你是一个精准的OCR提取与排版助手。请仔细观察图片，将图片中的所有文字、表格、公式直接转换为排版完美的Markdown。严禁概括、删减、解释或合并内容。如果遇到表格，必须使用Markdown表格语法。直接输出Markdown代码，不要加任何前言后语。",
            conversationMessages = listOf(
                Triple("user", "请识别这张图片中的所有文字内容，保持原始排版格式。", listOf(base64))
            )
        )

        var result = ""
        chatRepository.streamChat(
            messages = messages,
            temperature = 0.1f,
            modelType = "vision"
        ).collect { streamResult ->
            if (streamResult.error != null) {
                throw Exception("第${pageNum}页识别失败: ${streamResult.error}")
            }
            if (streamResult.isDone) return@collect
            if (streamResult.content.isNotEmpty()) {
                result += streamResult.content
            }
        }
        return result
    }

    private suspend fun stageDirectOutput(rawText: String, imageCount: Int) {
        _markdownResult.value = rawText
        _ocrStep.value = OcrStep.SHOWING_RESULT
        saveToRoom(rawText, imageCount)
    }

    private suspend fun stageChunkedCleaning(rawText: String, imageCount: Int) {
        if (!chatRepository.isAuxConfigured()) {
            _markdownResult.value = rawText
            _ocrStep.value = OcrStep.SHOWING_RESULT
            saveToRoom(rawText, imageCount)
            return
        }

        _ocrStep.value = OcrStep.AI_CLEANING(0, 0)

        try {
            val chunks = splitIntoChunks(rawText, CHUNK_SIZE)
            val totalChunks = chunks.size
            val cleanedChunks = arrayOfNulls<String>(totalChunks)
            val semaphore = Semaphore(CHUNK_CONCURRENCY)
            var processedCount = 0

            coroutineScope {
                chunks.mapIndexed { index, chunk ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val cleaned = cleanSingleChunk(chunk, index + 1, totalChunks)
                            cleanedChunks[index] = cleaned
                            synchronized(this) {
                                processedCount++
                                _ocrStep.value = OcrStep.AI_CLEANING(processedCount, totalChunks)
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalMarkdown = cleanedChunks.filterNotNull().joinToString("\n\n")
            _markdownResult.value = finalMarkdown
            _ocrStep.value = OcrStep.SHOWING_RESULT
            saveToRoom(finalMarkdown, imageCount)
        } catch (e: Exception) {
            _markdownResult.value = rawText
            _ocrStep.value = OcrStep.SHOWING_RESULT
            saveToRoom(rawText, imageCount)
            _events.emit(OcrEvent.ShowToast("AI清洗部分失败，已保存原始识别结果: ${e.message}"))
        }
    }

    private suspend fun cleanSingleChunk(chunk: String, chunkIndex: Int, totalChunks: Int): String {
        val messages = chatRepository.buildMessages(
            systemPrompt = "你是一个专业的文字整理助手。请将以下OCR识别出的文字进行语义清洗和排版优化，输出结构清晰的Markdown格式文档。要求：保持所有原始信息不丢失，修正明显的OCR识别错误，优化段落和列表结构，添加适当的标题层级。只输出清洗后的Markdown内容，不要添加任何额外说明。",
            conversationMessages = listOf(
                "user" to "请将以下OCR识别结果片段(${chunkIndex}/${totalChunks})整理为格式规范的Markdown：\n\n$chunk"
            )
        )

        var result = ""
        chatRepository.streamChat(
            messages = messages,
            temperature = 0.3f,
            modelType = "aux"
        ).collect { streamResult ->
            if (streamResult.error != null) {
                throw Exception("第${chunkIndex}块清洗失败: ${streamResult.error}")
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

    private suspend fun saveToRoom(content: String, imageCount: Int) {
        val title = currentSandboxFile?.name ?: "OCR Result"
        val entity = GeneratedMarkdownEntity(
            title = title,
            content = content,
            sourceModule = "ocr",
            createdAt = System.currentTimeMillis()
        )
        generatedMarkdownDao.insert(entity)
    }

    fun downloadMarkdown() {
        val content = _markdownResult.value
        if (content.isBlank()) return

        val fileName = "ocr_result_${System.currentTimeMillis()}.md"
        viewModelScope.launch {
            val result = fileDownloader.saveMarkdownToDownloads(content, fileName)
            result.onSuccess {
                _events.emit(OcrEvent.ShowToast("已保存到下载目录: $it"))
            }.onFailure {
                _events.emit(OcrEvent.ShowToast("保存失败: ${it.message}"))
            }
        }
    }

    fun reset() {
        currentSandboxFile?.let { uriSandboxManager.cleanupSpecificFile(it) }
        currentSandboxFile = null
        pendingImageUris = emptyList()
        pendingPdfUri = null
        _ocrStep.value = OcrStep.IDLE
        _markdownResult.value = ""
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        currentSandboxFile?.let { uriSandboxManager.cleanupSpecificFile(it) }
        uriSandboxManager.cleanupTempFiles()
    }
}
