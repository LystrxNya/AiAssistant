package com.aiassistant.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.dao.ConversationDao
import com.aiassistant.data.dao.MessageDao
import com.aiassistant.data.dao.PersonaDao
import com.aiassistant.data.entity.ConversationEntity
import com.aiassistant.data.entity.ConversationState
import com.aiassistant.data.entity.MessageEntity
import com.aiassistant.data.entity.MessageRole
import com.aiassistant.data.entity.PersonaEntity
import com.aiassistant.data.entity.PersonaType
import com.aiassistant.data.repository.ChatRepository
import com.aiassistant.data.repository.SearchRepository
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.ModelConfigItem
import com.aiassistant.data.preset.BailianPresetManager
import com.aiassistant.data.network.model.ToolDefinition
import com.aiassistant.data.network.model.FunctionDef
import com.aiassistant.data.network.model.ChatMessage
import com.aiassistant.data.network.parser.CompletedToolCall
import com.aiassistant.data.tool.TodoToolExecutor
import com.aiassistant.ui.chat.components.DeepSeekThinkingLevel
import com.aiassistant.manager.CameraXManager
import com.aiassistant.manager.CropManager
import com.aiassistant.manager.SandboxResult
import com.aiassistant.manager.UriSandboxManager
import com.aiassistant.manager.ImageCompressor
import com.aiassistant.manager.CompressedImageResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectedImage(
    val uri: Uri,
    val thumbnailFile: java.io.File? = null,
    val base64: String = ""
)

data class FineTuneParams(
    val temperature: Float = 0.7f,
    val temperatureEnabled: Boolean = false,
    val topP: Float = 0.9f,
    val topPEnabled: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val thinkingEnabled: Boolean = true,
    val thinkingMode: String = "standard",
    val thinkingExplanation: String = "",
    val showThinkingContent: Boolean = true,
    val showStreamingEnabled: Boolean = true,
    val mdRenderingEnabled: Boolean = true,
    val bailianWebSearch: Boolean = false,
    val bailianWebExtractor: Boolean = false,
    val bailianCodeInterpreter: Boolean = false,
    val bailianWebSearchImage: Boolean = false,
    val bailianImageSearch: Boolean = false,
    val contextTurns: Int = 20,
    val summarizeDiscardedContext: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val personaDao: PersonaDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository,
    private val searchRepository: SearchRepository,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val uriSandboxManager: UriSandboxManager,
    private val imageCompressor: ImageCompressor,
    private val cameraXManager: CameraXManager,
    private val cropManager: CropManager,
    private val bailianPresetManager: BailianPresetManager,
    private val todoToolExecutor: TodoToolExecutor
) : ViewModel() {

    val personas: StateFlow<List<PersonaEntity>> = personaDao.getPersonasByType(PersonaType.CHAT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentPersonaId = MutableStateFlow(0L)
    val currentPersonaId: StateFlow<Long> = _currentPersonaId.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow(0L)
    val currentConversationId: StateFlow<Long> = _currentConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedImages = MutableStateFlow<List<SelectedImage>>(emptyList())
    val selectedImages: StateFlow<List<SelectedImage>> = _selectedImages.asStateFlow()

    private val _fineTuneParams = MutableStateFlow(FineTuneParams())
    val fineTuneParams: StateFlow<FineTuneParams> = _fineTuneParams.asStateFlow()

    private val _personaDrawerOpen = MutableStateFlow(false)
    val personaDrawerOpen: StateFlow<Boolean> = _personaDrawerOpen.asStateFlow()

    private val _fineTuneVisible = MutableStateFlow(false)
    val fineTuneVisible: StateFlow<Boolean> = _fineTuneVisible.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _thinkingContent = MutableStateFlow("")
    val thinkingContent: StateFlow<String> = _thinkingContent.asStateFlow()

    private val _toolProgress = MutableStateFlow("")
    val toolProgress: StateFlow<String> = _toolProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentStreamingMessageId: Long = 0L
    private var currentStreamJob: Job? = null
    private var autoSelectNextConversation = true

    // Cache live content by message ID to prevent re-rendering flicker during scroll
    private val messageLiveContentCache = mutableMapOf<Long, String>()

    fun getMessageLiveContent(messageId: Long): String {
        return messageLiveContentCache[messageId] ?: ""
    }

    init {
        viewModelScope.launch {
            personaDao.getPersonasByType(PersonaType.CHAT)
                .first { it.isNotEmpty() }
                .let { list ->
                    val default = list.firstOrNull { it.isDefault } ?: list.first()
                    _currentPersonaId.value = default.id
                }
        }

        viewModelScope.launch {
            _currentPersonaId
                .flatMapLatest { personaId ->
                    if (personaId > 0) conversationDao.getConversationsByPersona(personaId)
                    else flowOf(emptyList())
                }
                .collect { convList ->
                    _conversations.value = convList
                    if (autoSelectNextConversation) {
                        val pid = _currentPersonaId.value
                        if (pid > 0) {
                            if (convList.isNotEmpty() && _currentConversationId.value == 0L) {
                                selectConversation(convList.first().id)
                            } else if (convList.isEmpty()) {
                                createNewConversation(pid)
                            }
                        }
                    }
                }
        }

        viewModelScope.launch {
            _currentConversationId.flatMapLatest { convId ->
                if (convId > 0) messageDao.getMessagesByConversation(convId)
                else flowOf(emptyList())
            }.collect { msgList ->
                _messages.value = msgList
            }
        }
    }

    fun switchPersona(personaId: Long) {
        cancelCurrentStream()
        _currentConversationId.value = 0L
        _currentPersonaId.value = personaId
        _conversationState.value = ConversationState.IDLE
        _inputText.value = ""
        _streamingContent.value = ""
        _thinkingContent.value = ""
        _errorMessage.value = null
        messageLiveContentCache.clear()
    }

    fun selectConversation(conversationId: Long) {
        cancelCurrentStream()
        _currentConversationId.value = conversationId
        _conversationState.value = ConversationState.IDLE
        _streamingContent.value = ""
        _thinkingContent.value = ""
        _errorMessage.value = null
        messageLiveContentCache.clear()
    }

    fun createNewConversation(personaId: Long = _currentPersonaId.value) {
        if (personaId <= 0) return
        viewModelScope.launch {
            try {
                autoSelectNextConversation = false
                val convId = conversationDao.insert(
                    ConversationEntity(
                        personaId = personaId,
                        title = "新对话"
                    )
                )
                _currentConversationId.value = convId
                _conversationState.value = ConversationState.IDLE
            } catch (_: Exception) {
            } finally {
                autoSelectNextConversation = true
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            conversationDao.delete(ConversationEntity(id = conversationId, personaId = _currentPersonaId.value))
            if (_currentConversationId.value == conversationId) {
                val remaining = _conversations.value.filter { it.id != conversationId }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    createNewConversation()
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun addImageUris(uris: List<Uri>) {
        viewModelScope.launch {
            val currentImages = _selectedImages.value.toMutableList()
            val availableSlots = 9 - currentImages.size
            if (availableSlots <= 0) return@launch

            val urisToProcess = uris.take(availableSlots)
            for (uri in urisToProcess) {
                if (currentImages.any { it.uri == uri }) continue

                val sandboxResult = uriSandboxManager.sandboxFile(uri, "image/*")
                val thumbnailFile = when (sandboxResult) {
                    is SandboxResult.Success -> sandboxResult.file
                    is SandboxResult.Error -> null
                }

                if (thumbnailFile != null) {
                    val compressed = imageCompressor.compressImage(thumbnailFile)
                    currentImages.add(
                        SelectedImage(
                            uri = uri,
                            thumbnailFile = compressed.file,
                            base64 = compressed.base64
                        )
                    )
                    uriSandboxManager.cleanupSpecificFile(thumbnailFile)
                }
            }

            _selectedImages.value = currentImages
        }
    }

    fun removeImage(uri: Uri) {
        val current = _selectedImages.value.toMutableList()
        val imageToRemove = current.find { it.uri == uri }
        imageToRemove?.thumbnailFile?.let { uriSandboxManager.cleanupSpecificFile(it) }
        current.removeAll { it.uri == uri }
        _selectedImages.value = current
    }

    fun clearImages() {
        _selectedImages.value.forEach { it.thumbnailFile?.let { f -> uriSandboxManager.cleanupSpecificFile(f) } }
        _selectedImages.value = emptyList()
    }

    fun updateFineTuneParams(params: FineTuneParams) {
        _fineTuneParams.value = params
    }

    fun togglePersonaDrawer() {
        _personaDrawerOpen.value = !_personaDrawerOpen.value
    }

    fun closePersonaDrawer() {
        _personaDrawerOpen.value = false
    }

    fun toggleFineTune() {
        _fineTuneVisible.value = !_fineTuneVisible.value
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun retryMessage(assistantMessageId: Long) {
        val allMessages = _messages.value
        val assistantMsg = allMessages.firstOrNull { it.id == assistantMessageId }
            ?: return
        if (assistantMsg.role != MessageRole.ASSISTANT) return

        val assistantIndex = allMessages.indexOf(assistantMsg)
        val userMsg = allMessages.subList(0, assistantIndex)
            .lastOrNull { it.role == MessageRole.USER }
            ?: return

        viewModelScope.launch {
            messageDao.delete(assistantMsg)
            _conversationState.value = ConversationState.THINKING
            _streamingContent.value = ""
            _thinkingContent.value = ""
            _errorMessage.value = null

            val assistantPlaceholder = MessageEntity(
                conversationId = _currentConversationId.value,
                role = MessageRole.ASSISTANT,
                content = "",
                state = ConversationState.THINKING
            )
            currentStreamingMessageId = messageDao.insert(assistantPlaceholder)
            performStreamRequest(userMsg.content, userMsg.imagePaths)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            val msg = messageDao.getMessageById(messageId) ?: return@launch
            val allMessages = messageDao.getMessagesByConversationOnce(msg.conversationId)
            val idx = allMessages.indexOfFirst { it.id == messageId }
            if (idx > 0) {
                val prev = allMessages[idx - 1]
                if (prev.role == MessageRole.USER && msg.role == MessageRole.ASSISTANT) {
                    messageDao.delete(prev)
                }
            }
            messageDao.delete(msg)
        }
    }

    fun editMessage(message: MessageEntity) {
        viewModelScope.launch {
            _inputText.value = message.content
            val allMessages = messageDao.getMessagesByConversationOnce(message.conversationId)
            val deleteFromIndex = allMessages.indexOfFirst { it.id == message.id }
            if (deleteFromIndex >= 0) {
                for (i in deleteFromIndex until allMessages.size) {
                    messageDao.delete(allMessages[i])
                }
            }
            _conversationState.value = ConversationState.IDLE
            _streamingContent.value = ""
            _thinkingContent.value = ""
            _errorMessage.value = null
        }
    }

    fun stopStreaming() {
        cancelCurrentStream()
        viewModelScope.launch {
            if (currentStreamingMessageId > 0) {
                val existing = messageDao.getMessageById(currentStreamingMessageId)
                if (existing != null && existing.content.isNotBlank()) {
                    messageDao.update(
                        existing.copy(state = ConversationState.SUCCESS)
                    )
                } else if (existing != null) {
                    messageDao.delete(existing)
                    messageLiveContentCache.remove(currentStreamingMessageId)
                }
            }
            _conversationState.value = ConversationState.SUCCESS
            _streamingContent.value = ""
            _thinkingContent.value = ""
        }
    }

    private fun cancelCurrentStream() {
        currentStreamJob?.cancel()
        currentStreamJob = null
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val images = _selectedImages.value
        if (text.isEmpty() && images.isEmpty()) return
        if (_currentConversationId.value == 0L) return
        if (_conversationState.value != ConversationState.IDLE &&
            _conversationState.value != ConversationState.SUCCESS &&
            _conversationState.value != ConversationState.ERROR
        ) return

        viewModelScope.launch {
            val base64Images = images.map { it.base64 }
            val imagePaths = images.map { it.base64 }

            val userMessage = MessageEntity(
                conversationId = _currentConversationId.value,
                role = MessageRole.USER,
                content = text,
                imagePaths = imagePaths,
                state = ConversationState.SUCCESS
            )
            messageDao.insert(userMessage)

            _inputText.value = ""
            clearImages()
            _conversationState.value = ConversationState.THINKING
            _streamingContent.value = ""
            _thinkingContent.value = ""
            _errorMessage.value = null

            conversationDao.updateTimestamp(_currentConversationId.value)

            val assistantPlaceholder = MessageEntity(
                conversationId = _currentConversationId.value,
                role = MessageRole.ASSISTANT,
                content = "",
                state = ConversationState.THINKING
            )
            currentStreamingMessageId = messageDao.insert(assistantPlaceholder)

            var searchSummary: String? = null
            val params = _fineTuneParams.value
            if (params.webSearchEnabled && text.isNotBlank()) {
                _streamingContent.value = "🔍 正在联网搜索..."
                val searchResult = searchRepository.search(text)
                searchResult.onSuccess { summary ->
                    searchSummary = summary
                }.onFailure {
                    _streamingContent.value = ""
                }
            }

            performStreamRequest(text, base64Images, searchSummary)
            cameraXManager.deleteAllCameraCache()
        }
    }

    fun prepareCameraUri(): Uri = cameraXManager.createTempImageUri()

    fun createCropOptions(sourceUri: Uri): com.yalantis.ucrop.UCrop? {
        return try {
            cropManager.createCropOptions(sourceUri)
        } catch (_: Exception) {
            null
        }
    }

    fun getCropResult(data: android.content.Intent?): Uri? = cropManager.getCropResult(data)

    private suspend fun getCurrentPersonaTools(): List<ToolDefinition>? {
        val persona = personaDao.getPersonaById(_currentPersonaId.value) ?: return null
        if (persona.name == "时间管理大师") {
            return todoToolExecutor.getToolDefinitions()
        }
        return null
    }

    /**
     * Parse a text-based tool call from the model's response content.
     * Supports JSON in code blocks (```json ... ```) or raw JSON.
     * Returns (functionName, argumentsJson) or null if no tool call found.
     */
    private fun parseTextToolCall(content: String): Pair<String, String>? {
        // Find JSON containing "tool_call" key - handle nested braces properly
        val toolCallIndex = content.indexOf("\"tool_call\"")
        if (toolCallIndex < 0) return null

        // Scan backwards to find the opening '{' of the JSON object
        var start = toolCallIndex
        while (start >= 0 && content[start] != '{') start--
        if (start < 0) return null

        // Use brace counting to find the complete JSON object
        val jsonString = extractJsonObject(content, start) ?: return null

        return try {
            val parsed = com.google.gson.JsonParser.parseString(jsonString).asJsonObject
            val toolCall = parsed.get("tool_call")?.asString ?: return null
            val arguments = parsed.getAsJsonObject("arguments")?.toString() ?: "{}"
            toolCall to arguments
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJsonObject(text: String, startIndex: Int): String? {
        if (startIndex < 0 || startIndex >= text.length || text[startIndex] != '{') return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in startIndex until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == '{') depth++
            else if (c == '}') { depth--; if (depth == 0) return text.substring(startIndex, i + 1) }
        }
        return null
    }

    private fun buildChatFlow(
        msgs: List<ChatMessage>,
        persona: PersonaEntity?,
        params: FineTuneParams,
        temperature: Float?,
        topP: Float?,
        personaTools: List<ToolDefinition>?,
        toolChoice: String? = null
    ): kotlinx.coroutines.flow.Flow<com.aiassistant.data.network.parser.StreamResult>? {
        val personaModelId = persona?.modelId
        val modelConfig = if (personaModelId?.isNotBlank() == true) {
            chatRepository.getModelById(personaModelId)
        } else {
            val mainModelId = encryptedPrefsManager.getMainModelId()
            if (mainModelId.isNotBlank()) {
                encryptedPrefsManager.getModelById(mainModelId)
            } else {
                null
            }
        } ?: encryptedPrefsManager.getModelsList().firstOrNull() ?: return null

        val isResponsesApi = modelConfig.apiType == BailianPresetManager.API_TYPE_RESPONSES
        android.util.Log.d("ChatVM", "buildChatFlow: model=${modelConfig.modelName}, apiType=${modelConfig.apiType}, isResponsesApi=$isResponsesApi, personaTools=${personaTools?.size}, thinkingEnabled=${params.thinkingEnabled}")

        // When persona has custom function calling tools
        if (personaTools != null) {
            if (isResponsesApi) {
                // Responses API: supports streaming + thinking + function calling together
                val responsesTools = buildResponsesToolsFromDefinitions(personaTools)
                val builtInTools = buildBailianResponsesTools(params)
                val allTools = responsesTools + builtInTools
                val reasoningObj = buildResponsesReasoning(params, modelConfig)
                return chatRepository.streamResponsesWithModel(
                    modelConfig = modelConfig,
                    messages = msgs,
                    tools = allTools.ifEmpty { null },
                    enableThinking = if (reasoningObj == null && params.thinkingEnabled) true else null,
                    reasoning = reasoningObj
                )
            } else {
                // Chat Completions API: disable thinking for function calling
                val extraBody = buildBailianExtraBody(params, modelConfig).toMutableMap()
                extraBody.remove("enable_thinking")
                extraBody.remove("reasoning_effort")
                return chatRepository.streamChatWithModel(
                    modelConfig = modelConfig,
                    messages = msgs,
                    temperature = temperature,
                    topP = topP,
                    tools = personaTools,
                    toolChoice = toolChoice,
                    extraBody = extraBody.ifEmpty { null }
                )
            }
        }

        return if (isResponsesApi) {
            val responsesTools = buildBailianResponsesTools(params)
            val reasoningObj = buildResponsesReasoning(params, modelConfig)
            chatRepository.streamResponsesWithModel(
                modelConfig = modelConfig,
                messages = msgs,
                tools = responsesTools.ifEmpty { null },
                enableThinking = if (reasoningObj == null && params.thinkingEnabled) true else null,
                reasoning = reasoningObj
            )
        } else {
            val chatTools = buildBailianChatTools(params, modelConfig)
            val extraBody = buildBailianExtraBody(params, modelConfig)
            chatRepository.streamChatWithModel(
                modelConfig = modelConfig,
                messages = msgs,
                temperature = temperature,
                topP = topP,
                tools = chatTools.ifEmpty { null },
                extraBody = extraBody.ifEmpty { null }
            )
        }
    }

    private fun performStreamRequest(userText: String, base64Images: List<String>, searchSummary: String? = null) {
        currentStreamJob = viewModelScope.launch {
            try {
                if (encryptedPrefsManager.getModelsList().isEmpty()) {
                    handleError("请先在设置中配置至少一个模型")
                    return@launch
                }

                val persona = personaDao.getPersonaById(_currentPersonaId.value)
                val params = _fineTuneParams.value

                val allHistoryMessages = _messages.value
                    .filter { it.id != currentStreamingMessageId && it.state == ConversationState.SUCCESS }
                val contextTurns = params.contextTurns
                val historyMessages = allHistoryMessages.takeLast(contextTurns * 2)

                var contextSummary: String? = null
                if (params.summarizeDiscardedContext && allHistoryMessages.size > contextTurns * 2) {
                    val discardedMessages = allHistoryMessages.dropLast(contextTurns * 2)
                    contextSummary = summarizeDiscardedContext(discardedMessages)
                }

                var basePrompt = persona?.systemPrompt ?: ""
                if (!contextSummary.isNullOrBlank()) {
                    val contextNote = "以下是之前对话的摘要（早期上下文已被压缩）：\n\n$contextSummary"
                    basePrompt = if (basePrompt.isNotBlank()) {
                        "$basePrompt\n\n$contextNote"
                    } else {
                        contextNote
                    }
                }
                if (!searchSummary.isNullOrBlank()) {
                    val searchContext = "以下是联网搜索到的最新相关信息，请结合这些信息回答用户问题：\n\n$searchSummary"
                    basePrompt = if (basePrompt.isNotBlank()) {
                        "$basePrompt\n\n$searchContext"
                    } else {
                        searchContext
                    }
                }
                val d = "$"
                basePrompt += "\n\n格式要求：使用Markdown排版。标题#后加空格。列表每项独占一行。代码块```必须独占一行。数学公式行内用${d}x${d}，块级用${d}${d}独占一行${d}${d}。不要向用户透露或讨论格式规则。"

                // Inject current datetime so the model can correctly compute relative dates
                val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss (EEEE)", java.util.Locale.CHINA)
                    .format(java.util.Date())
                basePrompt += "\n\n当前时间：$now"

                val initialMessages = if (base64Images.isEmpty()) {
                    val conversationPairs = historyMessages.map {
                        val role = if (it.role == MessageRole.USER) "user" else "assistant"
                        role to it.content
                    }.toMutableList()
                    chatRepository.buildMessages(basePrompt, conversationPairs)
                } else {
                    val triples = historyMessages.map {
                        val role = if (it.role == MessageRole.USER) "user" else "assistant"
                        Triple(role, it.content, it.imagePaths)
                    }.toMutableList()
                    triples.add(Triple("user", userText, base64Images))
                    chatRepository.buildMessagesWithImages(basePrompt, triples)
                }

                val temperature = if (params.temperatureEnabled) params.temperature else null
                val topP = if (params.topPEnabled) params.topP else null

                val personaTools = getCurrentPersonaTools()

                // Tool calling loop
                var currentMessages = initialMessages.toMutableList()
                var toolLoopActive = true
                val maxToolRounds = 10
                var toolRound = 0

                while (toolLoopActive && toolRound < maxToolRounds) {
                    toolLoopActive = false
                    toolRound++
                    android.util.Log.d("ChatVM", "=== Tool loop round $toolRound, currentMessages.size=${currentMessages.size} ===")

                    var fullContent = ""
                    var fullThinking = ""

                    val chatFlow = buildChatFlow(currentMessages, persona, params, temperature, topP, personaTools, toolChoice = if (toolRound == 1) "auto" else null)
                    android.util.Log.d("ChatVM", "buildChatFlow returned: ${if (chatFlow != null) "non-null flow" else "NULL"}")
                    if (chatFlow == null) {
                        _errorMessage.value = "请先在设置中配置至少一个模型"
                        _conversationState.value = ConversationState.ERROR
                        return@launch
                    }

                    var collectedToolCalls: List<CompletedToolCall>? = null
                    var completed = false
                    var hasError = false

                    val doneSignal = CompletableDeferred<Boolean>()
                    val collectJob = launch {
                        try {
                            chatFlow.collect { result ->
                                if (result.error != null) {
                                    hasError = true
                                    android.util.Log.e("ChatVM", "Stream error: ${result.error}")
                                    handleError(result.error)
                                    doneSignal.complete(false)
                                    return@collect
                                }

                                if (result.isDone) {
                                    completed = true
                                    android.util.Log.d("ChatVM", "Stream done. hasError=$hasError, toolCalls=${collectedToolCalls?.size}, contentLen=${fullContent.length}, thinkingLen=${fullThinking.length}")
                                    if (hasError) {
                                        doneSignal.complete(false)
                                        return@collect
                                    }
                                    if (collectedToolCalls == null) {
                                        messageDao.update(
                                            MessageEntity(
                                                id = currentStreamingMessageId,
                                                conversationId = _currentConversationId.value,
                                                role = MessageRole.ASSISTANT,
                                                content = fullContent,
                                                thinkingContent = fullThinking,
                                                state = ConversationState.SUCCESS
                                            )
                                        )
                                        _conversationState.value = ConversationState.SUCCESS
                                        _streamingContent.value = ""
                                        _thinkingContent.value = ""
                                        _toolProgress.value = ""
                                        updateConversationTitle(userText)
                                    }
                                    doneSignal.complete(true)
                                    return@collect
                                }

                                if (result.toolCalls != null) {
                                    collectedToolCalls = result.toolCalls
                                    android.util.Log.d("ChatVM", "Tool calls collected: ${result.toolCalls.size} calls")
                                    for (tc in result.toolCalls) {
                                        android.util.Log.d("ChatVM", "  Tool: ${tc.functionName}(${tc.arguments})")
                                    }
                                }

                                if (result.thinking.isNotEmpty()) {
                                    fullThinking += result.thinking
                                    _thinkingContent.value = fullThinking
                                }

                                if (result.content.isNotEmpty()) {
                                    fullContent += result.content
                                    _streamingContent.value = fullContent
                                    messageLiveContentCache[currentStreamingMessageId] = fullContent
                                    if (_conversationState.value == ConversationState.THINKING) {
                                        _conversationState.value = ConversationState.STREAMING
                                    }
                                }

                                if (result.toolProgress != null) {
                                    _toolProgress.value = result.toolProgress
                                }
                            }
                            // Flow completed naturally without isDone
                            doneSignal.complete(true)
                        } catch (e: Exception) {
                            android.util.Log.e("ChatVM", "Collect error", e)
                            doneSignal.complete(false)
                        }
                    }
                    doneSignal.await()
                    collectJob.cancel()
                    android.util.Log.d("ChatVM", "chatFlow collect cancelled. hasError=$hasError, completed=$completed, collectedToolCalls=${collectedToolCalls?.size}")

                    if (hasError) break

                    if (collectedToolCalls != null && collectedToolCalls!!.isNotEmpty()) {
                        android.util.Log.d("ChatVM", "Entering tool execution block, toolRound=$toolRound")
                        // Save assistant message with tool call info
                        val assistantToolMsg = fullContent.ifBlank { "[调用工具中...]" }
                        messageDao.update(
                            MessageEntity(
                                id = currentStreamingMessageId,
                                conversationId = _currentConversationId.value,
                                role = MessageRole.ASSISTANT,
                                content = assistantToolMsg,
                                thinkingContent = fullThinking,
                                state = ConversationState.SUCCESS
                            )
                        )
                        android.util.Log.d("ChatVM", "Assistant message saved")

                        // Build assistant message with tool_calls for API
                        val toolCallsList = collectedToolCalls!!.map { tc ->
                            mapOf(
                                "id" to tc.id,
                                "type" to "function",
                                "function" to mapOf(
                                    "name" to tc.functionName,
                                    "arguments" to tc.arguments
                                )
                            )
                        }
                        currentMessages.add(ChatMessage(
                            role = "assistant",
                            content = fullContent.ifBlank { "" },
                            toolCalls = toolCallsList,
                            reasoningContent = fullThinking.ifBlank { null }
                        ))
                        android.util.Log.d("ChatVM", "Assistant tool_calls message added to context, currentMessages.size=${currentMessages.size}")

                        // Execute each tool and add results
                        for (toolCall in collectedToolCalls!!) {
                            android.util.Log.d("ChatVM", "Executing tool: ${toolCall.functionName}, args=${toolCall.arguments.take(100)}")
                            _toolProgress.value = "正在执行: ${toolCall.functionName}..."
                            val resultJson = try {
                                val result = todoToolExecutor.execute(toolCall.functionName, toolCall.arguments)
                                android.util.Log.d("ChatVM", "Tool result: ${result.take(200)}")
                                result
                            } catch (e: Exception) {
                                android.util.Log.e("ChatVM", "Tool execution error", e)
                                """{"error": "工具执行异常: ${e.message?.replace("\"", "'")}"}"""
                            }
                            currentMessages.add(ChatMessage(
                                role = "tool",
                                content = resultJson,
                                toolCallId = toolCall.id
                            ))
                        }
                        android.util.Log.d("ChatVM", "All tools executed, preparing for next round. currentMessages.size=${currentMessages.size}")

                        // Prepare for next round
                        val newPlaceholder = MessageEntity(
                            conversationId = _currentConversationId.value,
                            role = MessageRole.ASSISTANT,
                            content = "",
                            state = ConversationState.THINKING
                        )
                        currentStreamingMessageId = messageDao.insert(newPlaceholder)
                        _conversationState.value = ConversationState.STREAMING
                        _streamingContent.value = ""
                        _thinkingContent.value = ""
                        android.util.Log.d("ChatVM", "Next round prepared, newStreamingId=$currentStreamingMessageId, calling continue")

                        toolLoopActive = true
                        continue
                    }

                    // No native tool calls - check for text-based tool call (fallback)
                    // Also check thinking content: with thinking enabled, model may put tool call there
                    if (personaTools != null) {
                        val textToolCall = parseTextToolCall(fullContent)
                            ?: if (fullThinking.isNotBlank()) parseTextToolCall(fullThinking) else null
                        if (textToolCall != null) {
                            // Save the assistant message with the tool call text
                            messageDao.update(
                                MessageEntity(
                                    id = currentStreamingMessageId,
                                    conversationId = _currentConversationId.value,
                                    role = MessageRole.ASSISTANT,
                                    content = fullContent,
                                    thinkingContent = fullThinking,
                                    state = ConversationState.SUCCESS
                                )
                            )

                            // Add assistant message to conversation for API
                            currentMessages.add(ChatMessage(
                                role = "assistant",
                                content = fullContent
                            ))

                            // Execute the tool
                            _toolProgress.value = "正在执行: ${textToolCall.first}..."
                            val resultJson = try {
                                todoToolExecutor.execute(textToolCall.first, textToolCall.second)
                            } catch (e: Exception) {
                                """{"error": "工具执行异常: ${e.message?.replace("\"", "'")}"}"""
                            }

                            // Add tool result as user message (since this is text-based, not native tool_calls)
                            currentMessages.add(ChatMessage(
                                role = "user",
                                content = "工具执行结果（${textToolCall.first}）：\n$resultJson\n\n请根据工具执行结果回复用户。如果工具执行成功，简洁确认即可。如果失败，告知用户原因。"
                            ))

                            // Prepare for next round
                            val newPlaceholder = MessageEntity(
                                conversationId = _currentConversationId.value,
                                role = MessageRole.ASSISTANT,
                                content = "",
                                state = ConversationState.THINKING
                            )
                            currentStreamingMessageId = messageDao.insert(newPlaceholder)
                            _conversationState.value = ConversationState.STREAMING
                            _streamingContent.value = ""
                            _thinkingContent.value = ""

                            toolLoopActive = true
                            continue
                        }
                    }

                    // No tool calls at all - finalize
                    if (!completed && _conversationState.value == ConversationState.STREAMING) {
                        messageDao.update(
                            MessageEntity(
                                id = currentStreamingMessageId,
                                conversationId = _currentConversationId.value,
                                role = MessageRole.ASSISTANT,
                                content = fullContent,
                                thinkingContent = fullThinking,
                                state = ConversationState.SUCCESS
                            )
                        )
                        _conversationState.value = ConversationState.SUCCESS
                        _streamingContent.value = ""
                        _thinkingContent.value = ""
                        _toolProgress.value = ""
                        updateConversationTitle(userText)
                    }
                }

                if (toolRound >= maxToolRounds) {
                    _toolProgress.value = ""
                }

                // Clean up cache for messages that now have saved content
                messageLiveContentCache.keys.removeAll { id ->
                    val msg = _messages.value.find { it.id == id }
                    msg != null && msg.content.isNotBlank()
                }
            } catch (e: Exception) {
                handleError(e.message ?: "发送消息时发生未知错误")
            }
        }
    }

    private fun buildBailianResponsesTools(params: FineTuneParams): List<Map<String, Any>> {
        val tools = mutableListOf<Map<String, Any>>()
        if (params.bailianWebSearch) tools.add(mapOf("type" to "web_search"))
        if (params.bailianWebExtractor) tools.add(mapOf("type" to "web_extractor"))
        if (params.bailianCodeInterpreter) tools.add(mapOf("type" to "code_interpreter"))
        if (params.bailianWebSearchImage) tools.add(mapOf("type" to "web_search_image"))
        if (params.bailianImageSearch) tools.add(mapOf("type" to "image_search"))
        return tools
    }

    /**
     * Convert ToolDefinition list (OpenAI format) to Responses API format.
     * OpenAI: {"type":"function","function":{"name":...,"parameters":...}}
     * Responses API: {"type":"function","name":...,"parameters":...}
     */
    private fun buildResponsesToolsFromDefinitions(tools: List<ToolDefinition>): List<Map<String, Any>> {
        return tools.map { toolDef ->
            val m = mutableMapOf<String, Any>(
                "type" to "function",
                "name" to toolDef.function.name
            )
            toolDef.function.description.let { m["description"] = it }
            m["parameters"] = toolDef.function.parameters
            m
        }
    }

    private fun buildBailianChatTools(params: FineTuneParams, modelConfig: ModelConfigItem): List<ToolDefinition> {
        // Bailian built-in tools (web_search, code_interpreter, etc.) are enabled via extra_body,
        // not as standard function tool definitions. Returning empty to avoid redundancy.
        return emptyList()
    }

    private fun buildResponsesReasoning(params: FineTuneParams, modelConfig: ModelConfigItem): Map<String, Any>? {
        val capability = bailianPresetManager.getThinkingCapability(modelConfig)
        if (capability == BailianPresetManager.THINKING_REASONING_EFFORT) {
            val level = DeepSeekThinkingLevel.entries.find { it.modeName == params.thinkingMode }
                ?: DeepSeekThinkingLevel.MAX
            if (level == DeepSeekThinkingLevel.NONE) return null
            return mapOf("effort" to level.reasoningEffort)
        }
        return null
    }

    private fun buildBailianExtraBody(params: FineTuneParams, modelConfig: ModelConfigItem): Map<String, Any> {
        val extra = mutableMapOf<String, Any>()
        val availableTools = bailianPresetManager.getAvailableTools(modelConfig)
        val capability = bailianPresetManager.getThinkingCapability(modelConfig)

        if (params.bailianWebSearch && "web_search" in availableTools) {
            extra["enable_search"] = true
        }
        if (params.bailianCodeInterpreter && "code_interpreter" in availableTools) {
            extra["enable_code_interpreter"] = true
            extra["enable_thinking"] = true
        }
        if (params.bailianWebExtractor && "web_extractor" in availableTools) {
            extra["enable_search"] = true
            extra["enable_thinking"] = true
            extra["search_options"] = mapOf("search_strategy" to "agent_max")
        }

        if (capability == BailianPresetManager.THINKING_REASONING_EFFORT) {
            val level = DeepSeekThinkingLevel.entries.find { it.modeName == params.thinkingMode }
                ?: DeepSeekThinkingLevel.MAX
            if (level == DeepSeekThinkingLevel.NONE) {
                extra["enable_thinking"] = false
            } else {
                extra["reasoning_effort"] = level.reasoningEffort
            }
        } else if (capability == BailianPresetManager.THINKING_BINARY) {
            if (params.thinkingEnabled) {
                extra["enable_thinking"] = true
            }
        }

        return extra
    }

    private suspend fun summarizeDiscardedContext(messages: List<MessageEntity>): String? {
        return try {
            val conversationText = messages.joinToString("\n") { msg ->
                val role = if (msg.role == MessageRole.USER) "用户" else "AI"
                "$role: ${msg.content.take(500)}"
            }
            if (conversationText.isBlank()) return null

            val summaryPrompt = "请用简洁的中文总结以下对话的关键信息，保留重要的上下文、决定和结论：\n\n$conversationText"
            val summaryMessages = listOf(
                com.aiassistant.data.network.model.ChatMessage(role = "user", content = summaryPrompt)
            )

            var summary = ""
            chatRepository.streamChat(messages = summaryMessages, modelType = "aux").collect { result ->
                if (result.content.isNotEmpty()) {
                    summary += result.content
                }
                if (result.error != null) {
                    return@collect
                }
            }
            summary.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun handleError(error: String) {
        messageDao.update(
            MessageEntity(
                id = currentStreamingMessageId,
                conversationId = _currentConversationId.value,
                role = MessageRole.ASSISTANT,
                content = "错误：$error",
                state = ConversationState.ERROR
            )
        )
        _conversationState.value = ConversationState.ERROR
        _streamingContent.value = ""
        _thinkingContent.value = ""
        _toolProgress.value = ""
        _errorMessage.value = error
    }

    private suspend fun updateConversationTitle(userText: String) {
        val currentConv = conversationDao.getConversationById(_currentConversationId.value)
        if (currentConv != null && currentConv.title == "新对话") {
            val autoTitle = userText.take(20) + if (userText.length > 20) "..." else ""
            conversationDao.update(currentConv.copy(title = autoTitle))
        }
    }

    private val _editingPersona = MutableStateFlow<PersonaEntity?>(null)
    val editingPersona: StateFlow<PersonaEntity?> = _editingPersona.asStateFlow()

    fun getAvailableModels(): List<ModelConfigItem> {
        return encryptedPrefsManager.getModelsList()
    }

    fun getCurrentModelConfig(): ModelConfigItem? {
        val persona = personas.value.find { it.id == _currentPersonaId.value }
        val personaModelId = persona?.modelId
        return if (personaModelId?.isNotBlank() == true) {
            chatRepository.getModelById(personaModelId)
        } else {
            val mainModelId = encryptedPrefsManager.getMainModelId()
            if (mainModelId.isNotBlank()) {
                encryptedPrefsManager.getModelById(mainModelId)
            } else {
                null
            }
        } ?: encryptedPrefsManager.getModelsList().firstOrNull()
    }

    fun isCurrentModelBailian(): Boolean {
        val model = getCurrentModelConfig() ?: return false
        return bailianPresetManager.isBailianModel(model.id)
    }

    fun isCurrentModelResponsesApi(): Boolean {
        val model = getCurrentModelConfig() ?: return false
        return bailianPresetManager.isResponsesApiModel(model)
    }

    fun getAvailableBailianTools(): List<String> {
        val model = getCurrentModelConfig() ?: return emptyList()
        return bailianPresetManager.getAvailableTools(model)
    }

    fun isBailianConfigured(): Boolean {
        return encryptedPrefsManager.getBailianApiKey() != null
    }

    fun startEditPersona(persona: PersonaEntity) {
        _editingPersona.value = persona
    }

    fun startNewPersona() {
        _editingPersona.value = PersonaEntity(
            name = "",
            systemPrompt = "",
            description = "",
            icon = "🤖",
            modelId = ""
        )
    }

    fun updateEditingPersona(persona: PersonaEntity) {
        _editingPersona.value = persona
    }

    fun saveEditingPersona() {
        val persona = _editingPersona.value ?: return
        viewModelScope.launch {
            if (persona.id == 0L) {
                personaDao.insert(persona)
            } else {
                personaDao.update(persona)
            }
            _editingPersona.value = null
        }
    }

    fun cancelEditPersona() {
        _editingPersona.value = null
    }

    fun deletePersona(personaId: Long) {
        viewModelScope.launch {
            personaDao.delete(PersonaEntity(id = personaId, name = "", systemPrompt = ""))
            if (_currentPersonaId.value == personaId) {
                val remaining = personas.value.filter { it.id != personaId }
                if (remaining.isNotEmpty()) {
                    switchPersona(remaining.first().id)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelCurrentStream()
        uriSandboxManager.cleanupTempFiles()
    }

    fun getThinkingCapability(): String {
        val modelConfig = getCurrentModelConfig() ?: return "binary"
        return bailianPresetManager.getThinkingCapability(modelConfig)
    }
}
