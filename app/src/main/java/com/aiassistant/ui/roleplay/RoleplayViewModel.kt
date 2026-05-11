package com.aiassistant.ui.roleplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.dao.ConversationDao
import com.aiassistant.data.dao.MessageDao
import com.aiassistant.data.dao.PersonaDao
import com.aiassistant.data.entity.ConversationEntity
import com.aiassistant.data.entity.ConversationState
import com.aiassistant.data.entity.ConversationType
import com.aiassistant.data.entity.MessageEntity
import com.aiassistant.data.entity.MessageRole
import com.aiassistant.data.entity.PersonaEntity
import com.aiassistant.data.entity.PersonaType
import com.aiassistant.data.network.model.PromptTemplates
import com.aiassistant.data.repository.ChatRepository
import com.aiassistant.data.repository.MemoryRepository
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.entity.MemoryEntity
import com.aiassistant.data.network.model.ChatMessage
import com.aiassistant.data.preset.DeepSeekPresetManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RoleplayThinkingMode(val displayName: String, val description: String) {
    DEFAULT("默认", "模型自动选择思考方式"),
    INNER_OS("角色沉浸", "思考中带有角色内心独白"),
    ANALYSIS("纯分析", "思考中只有纯逻辑分析")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoleplayViewModel @Inject constructor(
    private val personaDao: PersonaDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val deepSeekPresetManager: DeepSeekPresetManager,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _currentPersonaId = MutableStateFlow(0L)
    val currentPersonaId: StateFlow<Long> = _currentPersonaId.asStateFlow()

    private val _currentConversationId = MutableStateFlow(0L)
    val currentConversationId: StateFlow<Long> = _currentConversationId.asStateFlow()

    val personas: StateFlow<List<PersonaEntity>> = personaDao.getPersonasByType(PersonaType.ROLEPLAY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = _currentPersonaId.flatMapLatest { id ->
        if (id > 0) conversationDao.getConversationsByPersona(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<MessageEntity>> = _currentConversationId.flatMapLatest { id ->
        if (id > 0) messageDao.getMessagesByConversation(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _thinkingContent = MutableStateFlow("")
    val thinkingContent: StateFlow<String> = _thinkingContent.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _thinkingMode = MutableStateFlow(RoleplayThinkingMode.INNER_OS)
    val thinkingMode: StateFlow<RoleplayThinkingMode> = _thinkingMode.asStateFlow()

    private val _showThinkingContent = MutableStateFlow(true)
    val showThinkingContent: StateFlow<Boolean> = _showThinkingContent.asStateFlow()

    private var currentStreamJob: Job? = null
    private var currentStreamingMessageId: Long = 0
    private var isFirstMessageInConversation = true

    private val _setupMessage = MutableStateFlow<String?>(null)
    val setupMessage: StateFlow<String?> = _setupMessage.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val memories: StateFlow<List<MemoryEntity>> = _memories.asStateFlow()

    init {
        ensureDeepSeekAvailable()
        viewModelScope.launch {
            val personas = personaDao.getPersonasByType(PersonaType.ROLEPLAY).stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
            )
            personas.collect { list ->
                if (list.isNotEmpty() && _currentPersonaId.value == 0L) {
                    val defaultPersona = personaDao.getDefaultByType(PersonaType.ROLEPLAY)
                    _currentPersonaId.value = defaultPersona?.id ?: list.first().id
                }
            }
        }

        viewModelScope.launch {
            _currentPersonaId.collect { personaId ->
                if (personaId > 0) {
                    val convs = conversationDao.getConversationsByPersona(personaId)
                    convs.collect { list ->
                        if (list.isNotEmpty() && _currentConversationId.value == 0L) {
                            _currentConversationId.value = list.first().id
                        } else if (list.isEmpty()) {
                            createNewConversation()
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            _currentConversationId.collect { convId ->
                if (convId > 0) {
                    val count = messageDao.getMessageCount(convId)
                    isFirstMessageInConversation = count == 0
                }
            }
        }
    }

    private fun ensureDeepSeekAvailable() {
        val models = encryptedPrefsManager.getModelsList()
        if (models.isEmpty()) {
            val apiKey = encryptedPrefsManager.getDeepSeekApiKey()
            if (!apiKey.isNullOrBlank()) {
                deepSeekPresetManager.initDeepSeekPresets(apiKey)
            } else {
                _setupMessage.value = "请先在设置中配置至少一个模型（百炼或 DeepSeek 官方）"
            }
        } else {
            // Models exist but no DeepSeek - try to add it if API key available
            val hasDeepSeek = models.any { it.provider == DeepSeekPresetManager.DEEPSEEK_PROVIDER }
            if (!hasDeepSeek) {
                val apiKey = encryptedPrefsManager.getDeepSeekApiKey()
                if (!apiKey.isNullOrBlank()) {
                    deepSeekPresetManager.initDeepSeekPresets(apiKey)
                }
            }
        }
    }

    fun selectPersona(personaId: Long) {
        _currentPersonaId.value = personaId
        _currentConversationId.value = 0L
    }

    fun selectConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
        viewModelScope.launch {
            val count = messageDao.getMessageCount(conversationId)
            isFirstMessageInConversation = count == 0
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val personaId = _currentPersonaId.value
            if (personaId == 0L) return@launch
            val conv = ConversationEntity(
                personaId = personaId,
                title = "新角色对话",
                type = ConversationType.ROLEPLAY
            )
            val id = conversationDao.insert(conv)
            _currentConversationId.value = id
            isFirstMessageInConversation = true
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun setThinkingMode(mode: RoleplayThinkingMode) {
        _thinkingMode.value = mode
    }

    fun setShowThinkingContent(show: Boolean) {
        _showThinkingContent.value = show
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val convId = _currentConversationId.value
        if (text.isBlank() || convId == 0L) return

        currentStreamJob?.cancel()

        viewModelScope.launch {
            val userMessage = MessageEntity(
                conversationId = convId,
                role = MessageRole.USER,
                content = text,
                state = ConversationState.SUCCESS
            )
            messageDao.insert(userMessage)

            _inputText.value = ""
            _conversationState.value = ConversationState.THINKING
            _streamingContent.value = ""
            _thinkingContent.value = ""
            _errorMessage.value = null

            conversationDao.updateTimestamp(convId)

            val assistantPlaceholder = MessageEntity(
                conversationId = convId,
                role = MessageRole.ASSISTANT,
                content = "",
                state = ConversationState.THINKING
            )
            currentStreamingMessageId = messageDao.insert(assistantPlaceholder)

            performStreamRequest(text, convId)
        }
    }

    private fun performStreamRequest(userText: String, conversationId: Long) {
        currentStreamJob = viewModelScope.launch {
            try {
                val persona = personaDao.getPersonaById(_currentPersonaId.value)
                val params = _thinkingMode.value

                val modelConfig = getModelForPersona(persona)
                if (modelConfig == null) {
                    handleError("请先在设置中配置 DeepSeek 官方模型的 API Key")
                    return@launch
                }

                val allMessages = messageDao.getMessagesByConversationOnce(conversationId)
                    .filter { it.id != currentStreamingMessageId && it.state == ConversationState.SUCCESS }

                // Overflow detection: extract memory from oldest overflow batch
                val maxContextMessages = MemoryRepository.CONTEXT_WINDOW_ROUNDS * 2
                if (allMessages.size > maxContextMessages) {
                    val overflowCount = allMessages.size - maxContextMessages
                    val batchSize = MemoryRepository.EXTRACTION_BATCH_ROUNDS * 2
                    if (overflowCount >= batchSize) {
                        val extractionMessages = allMessages.take(batchSize)
                        val personaId = _currentPersonaId.value
                        viewModelScope.launch {
                            try {
                                memoryRepository.extractMemory(personaId, conversationId, extractionMessages)
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Limit context to last 30 rounds
                val contextMessages = if (allMessages.size > maxContextMessages) {
                    allMessages.takeLast(maxContextMessages)
                } else {
                    allMessages
                }

                val systemPrompt = persona?.systemPrompt?.ifBlank { PromptTemplates.ROLEPLAY_DEFAULT_PROMPT }
                    ?: PromptTemplates.ROLEPLAY_DEFAULT_PROMPT

                val enrichedSystemPrompt = try {
                    val memories = memoryRepository.recallRelevantMemories(_currentPersonaId.value, userText)
                    if (memories.isNotEmpty()) {
                        systemPrompt + memoryRepository.buildMemoryPromptFragment(memories)
                    } else {
                        systemPrompt
                    }
                } catch (_: Exception) {
                    systemPrompt
                }

                val conversationPairs = contextMessages.map {
                    val role = if (it.role == MessageRole.USER) "user" else "assistant"
                    role to it.content
                }.toMutableList()

                // Build thinking mode marker - apply every turn
                val thinkingMarker = when (params) {
                    RoleplayThinkingMode.INNER_OS -> PromptTemplates.ROLEPLAY_INNER_OS_MARKER
                    RoleplayThinkingMode.ANALYSIS -> PromptTemplates.ROLEPLAY_NO_INNER_OS_MARKER
                    RoleplayThinkingMode.DEFAULT -> ""
                }
                isFirstMessageInConversation = false

                val finalSystemPrompt = enrichedSystemPrompt + thinkingMarker
                val chatMessages = chatRepository.buildMessages(finalSystemPrompt, conversationPairs)

                android.util.Log.d("RoleplayVM", "=== API Request ===")
                android.util.Log.d("RoleplayVM", "Total messages: ${chatMessages.size}")
                chatMessages.forEachIndexed { i, msg ->
                    val preview = msg.content.toString().take(100)
                    android.util.Log.d("RoleplayVM", "[$i] role=${msg.role} content=$preview")
                }

                val extraBody = mutableMapOf<String, Any>()
                extraBody["enable_thinking"] = true

                var completed = false
                chatRepository.streamChatWithModel(
                    modelConfig = modelConfig,
                    messages = chatMessages,
                    extraBody = extraBody
                ).collect { result ->
                    if (completed) return@collect
                    if (result.error != null) {
                        completed = true
                        if (_streamingContent.value.isNotBlank()) {
                            finalizeMessage(conversationId, userText)
                        } else {
                            handleError(result.error)
                        }
                        return@collect
                    }
                    if (result.isDone) {
                        completed = true
                        finalizeMessage(conversationId, userText)
                        return@collect
                    }
                    if (result.thinking.isNotEmpty()) {
                        _thinkingContent.value += result.thinking
                    }
                    if (result.content.isNotEmpty()) {
                        _conversationState.value = ConversationState.STREAMING
                        _streamingContent.value += result.content
                    }
                }
            } catch (e: Exception) {
                handleError(e.message ?: "Unknown error")
            }
        }
    }

    private fun getModelForPersona(persona: PersonaEntity?): com.aiassistant.data.ModelConfigItem? {
        val modelId = persona?.modelId
        if (!modelId.isNullOrBlank()) {
            chatRepository.getModelById(modelId)?.let { return it }
        }
        // Default to first DeepSeek official model
        val models = encryptedPrefsManager.getModelsList()
        return models.find { it.provider == "deepseek_official" }
            ?: models.find { it.modelName.contains("deepseek") }
            ?: models.firstOrNull()
    }

    private suspend fun finalizeMessage(conversationId: Long, userText: String) {
        val content = _streamingContent.value
        val thinking = _thinkingContent.value
        if (content.isNotBlank()) {
            messageDao.update(
                MessageEntity(
                    id = currentStreamingMessageId,
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = content,
                    thinkingContent = thinking,
                    state = ConversationState.SUCCESS
                )
            )
            _conversationState.value = ConversationState.SUCCESS
            _streamingContent.value = ""
            _thinkingContent.value = ""

            val currentConv = conversationDao.getConversationById(conversationId)
            if (currentConv != null && currentConv.title == "新角色对话") {
                val autoTitle = userText.take(20) + if (userText.length > 20) "..." else ""
                conversationDao.update(currentConv.copy(title = autoTitle))
            }
        }
    }

    private fun handleError(message: String) {
        _conversationState.value = ConversationState.ERROR
        _errorMessage.value = message
        if (currentStreamingMessageId > 0) {
            viewModelScope.launch {
                val existing = messageDao.getMessageById(currentStreamingMessageId)
                if (existing != null) {
                    messageDao.update(existing.copy(state = ConversationState.ERROR))
                }
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
        _conversationState.value = ConversationState.IDLE
    }

    fun createPersona(name: String, systemPrompt: String, description: String, icon: String, modelId: String) {
        viewModelScope.launch {
            val persona = PersonaEntity(
                name = name,
                systemPrompt = systemPrompt,
                description = description,
                icon = icon,
                modelId = modelId,
                type = PersonaType.ROLEPLAY
            )
            val id = personaDao.insert(persona)
            _currentPersonaId.value = id
        }
    }

    fun updatePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            personaDao.update(persona)
        }
    }

    fun deletePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            personaDao.delete(persona)
            if (_currentPersonaId.value == persona.id) {
                val remaining = personas.value.filter { it.id != persona.id }
                _currentPersonaId.value = remaining.firstOrNull()?.id ?: 0L
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            val conv = conversationDao.getConversationById(conversationId)
            if (conv != null) {
                conversationDao.delete(conv)
                if (_currentConversationId.value == conversationId) {
                    _currentConversationId.value = 0L
                }
            }
        }
    }

    fun getAvailableModels(): List<com.aiassistant.data.ModelConfigItem> {
        return encryptedPrefsManager.getModelsList()
    }

    fun dismissSetupMessage() {
        _setupMessage.value = null
    }

    fun loadMemories() {
        viewModelScope.launch {
            _memories.value = memoryRepository.getAllMemories(_currentPersonaId.value)
        }
    }

    fun editMessage(message: MessageEntity) {
        viewModelScope.launch {
            // Load message content into input
            _inputText.value = message.content

            // Delete all messages after this one (including this one)
            val allMessages = messageDao.getMessagesByConversationOnce(message.conversationId)
            val deleteFromIndex = allMessages.indexOfFirst { it.id == message.id }
            if (deleteFromIndex >= 0) {
                val messagesToDelete = allMessages.subList(deleteFromIndex, allMessages.size)
                for (msg in messagesToDelete) {
                    messageDao.delete(msg)
                }
            }

            // Reset state
            _conversationState.value = ConversationState.IDLE
            _streamingContent.value = ""
            _thinkingContent.value = ""
            isFirstMessageInConversation = false
        }
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(memoryId)
            loadMemories()
        }
    }
}
