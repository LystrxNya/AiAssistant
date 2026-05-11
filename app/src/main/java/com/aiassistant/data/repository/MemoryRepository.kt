package com.aiassistant.data.repository

import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.dao.MemoryDao
import com.aiassistant.data.entity.MemoryEntity
import com.aiassistant.data.entity.MessageEntity
import com.aiassistant.data.entity.MessageRole
import com.aiassistant.data.network.ApiClient
import com.aiassistant.data.network.model.ChatMessage
import com.aiassistant.data.network.model.ChatRequest
import com.aiassistant.data.network.model.PromptTemplates
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val apiClient: ApiClient,
    private val encryptedPrefsManager: EncryptedPrefsManager
) {
    companion object {
        const val MIN_MESSAGES_FOR_EXTRACTION = 10
        const val MAX_MEMORIES_RECALLED = 5
        const val DECAY_RATE_PER_DAY = 0.05f
        const val CLEANUP_THRESHOLD = 0.1f
        const val MAX_MEMORIES_PER_PERSONA = 50
        const val CONTEXT_WINDOW_ROUNDS = 30
        const val EXTRACTION_BATCH_ROUNDS = 10
    }

    private data class MemoryExtractionResult(
        val summary: String = "",
        @SerializedName("persona_summary") val personaSummary: String = "",
        val topics: List<String> = emptyList(),
        @SerializedName("key_facts") val keyFacts: List<String> = emptyList(),
        val importance: Float = 0.5f
    )

    suspend fun shouldExtractMemory(conversationId: Long, personaId: Long): Boolean {
        val count = memoryDao.getMemoryCount(personaId)
        if (count >= MAX_MEMORIES_PER_PERSONA) return false
        return true
    }

    suspend fun extractMemory(
        personaId: Long,
        conversationId: Long,
        messages: List<MessageEntity>
    ): MemoryEntity? {
        if (messages.size < 2) return null
        val modelConfig = getMemoryModel() ?: return null

        val conversationPairs = messages.map {
            val role = if (it.role == MessageRole.USER) "user" else "assistant"
            role to it.content
        }

        val chatMessages = mutableListOf<ChatMessage>()
        chatMessages.add(ChatMessage(role = "system", content = PromptTemplates.MEMORY_EXTRACTION_PROMPT))
        for ((role, content) in conversationPairs) {
            chatMessages.add(ChatMessage(role = role, content = content))
        }

        val request = ChatRequest(
            model = modelConfig.modelName,
            messages = chatMessages,
            stream = false,
            temperature = 0.3f
        )

        return try {
            val apiService = apiClient.getModelApiService(modelConfig)
            val response = apiService.chatCompletions(request)
            val content = response.choices?.firstOrNull()?.message?.content?.toString() ?: return null

            val jsonStr = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val parsed = Gson().fromJson(jsonStr, MemoryExtractionResult::class.java) ?: return null

            if (parsed.summary.isBlank()) return null

            val memory = MemoryEntity(
                personaId = personaId,
                conversationId = conversationId,
                summary = parsed.summary,
                personaSummary = parsed.personaSummary.ifBlank { parsed.summary },
                topics = parsed.topics.joinToString(","),
                keyFacts = parsed.keyFacts.joinToString(","),
                importance = parsed.importance.coerceIn(0f, 1f)
            )
            val id = memoryDao.insert(memory)
            enforceMaxMemories(personaId)
            memory.copy(id = id)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun recallRelevantMemories(personaId: Long, userText: String): List<MemoryEntity> {
        val keywords = extractKeywords(userText)
        if (keywords.isEmpty()) return emptyList()

        val seen = mutableSetOf<Long>()
        val candidates = mutableListOf<MemoryEntity>()

        for (keyword in keywords) {
            if (keyword.length < 2) continue
            val results = memoryDao.searchMemories(personaId, keyword)
            for (memory in results) {
                if (seen.add(memory.id)) {
                    candidates.add(memory)
                }
            }
        }

        if (candidates.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val scored = candidates.map { memory ->
            val daysSinceAccess = (now - memory.lastAccessedAt) / (1000.0 * 60 * 60 * 24)
            val recencyScore = 1.0f / (1.0f + (daysSinceAccess / 7.0f).toFloat())
            val score = memory.importance * 0.6f + recencyScore * 0.4f
            memory to score
        }

        val topMemories = scored.sortedByDescending { it.second }
            .take(MAX_MEMORIES_RECALLED)
            .map { it.first }

        for (memory in topMemories) {
            memoryDao.updateLastAccessed(memory.id, now)
        }

        return topMemories
    }

    fun buildMemoryPromptFragment(memories: List<MemoryEntity>): String {
        if (memories.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append(PromptTemplates.MEMORY_INJECTION_HEADER)
        memories.forEachIndexed { index, memory ->
            sb.appendLine("${index + 1}. [重要度:${String.format("%.1f", memory.importance)}] ${memory.personaSummary}")
            if (memory.topics.isNotBlank()) {
                sb.appendLine("   主题: ${memory.topics}")
            }
        }
        sb.append(PromptTemplates.MEMORY_INJECTION_FOOTER)
        return sb.toString()
    }

    suspend fun decayMemoriesForPersona(personaId: Long) {
        val memories = memoryDao.getMemoriesByPersona(personaId)
        for (memory in memories) {
            val newImportance = (memory.importance - DECAY_RATE_PER_DAY).coerceAtLeast(0f)
            memoryDao.updateImportance(memory.id, newImportance)
        }
        memoryDao.deleteLowImportance(personaId, CLEANUP_THRESHOLD)
        enforceMaxMemories(personaId)
    }

    suspend fun getAllMemories(personaId: Long): List<MemoryEntity> {
        return memoryDao.getMemoriesByPersona(personaId)
    }

    fun observeMemories(personaId: Long): Flow<List<MemoryEntity>> {
        return memoryDao.observeMemoriesByPersona(personaId)
    }

    suspend fun deleteMemory(memoryId: Long) {
        memoryDao.deleteById(memoryId)
    }

    private suspend fun enforceMaxMemories(personaId: Long) {
        val count = memoryDao.getMemoryCount(personaId)
        if (count > MAX_MEMORIES_PER_PERSONA) {
            memoryDao.deleteLowestImportance(personaId, count - MAX_MEMORIES_PER_PERSONA)
        }
    }

    private fun getMemoryModel(): com.aiassistant.data.ModelConfigItem? {
        // Prefer aux model for memory extraction
        val auxId = encryptedPrefsManager.getAuxModelId()
        if (auxId.isNotBlank()) {
            encryptedPrefsManager.getModelById(auxId)?.let { return it }
        }
        // Fallback to any available model
        val models = encryptedPrefsManager.getModelsList()
        return models.find { it.provider == "deepseek_official" }
            ?: models.find { it.modelName.contains("deepseek") }
            ?: models.firstOrNull()
    }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            "的", "了", "是", "在", "我", "你", "他", "她", "它",
            "这", "那", "有", "和", "与", "就", "都", "而", "及", "或",
            "不", "也", "会", "能", "要", "到", "说", "对", "为", "把",
            "被", "让", "给", "从", "向", "着", "过", "去", "来", "个",
            "a", "an", "the", "is", "are", "was", "were", "in", "on", "at",
            "to", "for", "of", "with", "and", "or", "but", "not", "it",
            "do", "does", "did", "has", "have", "had", "be", "been", "being"
        )
        return text.split(Regex("[\\s,，。.!！?？;；:：\\n\\r\\t()（）\\[\\]【】\"']"))
            .map { it.trim() }
            .filter { it.length >= 2 && it.lowercase() !in stopWords }
            .distinct()
            .take(10)
    }
}
