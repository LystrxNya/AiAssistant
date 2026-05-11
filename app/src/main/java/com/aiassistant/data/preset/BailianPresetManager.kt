package com.aiassistant.data.preset

import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.ModelConfigItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BailianPresetManager @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    companion object {
        const val API_TYPE_RESPONSES = "responses"
        const val API_TYPE_CHAT = "chat"
        const val API_TYPE_STT = "stt"
        const val BAILIAN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode"
        const val BAILIAN_PROVIDER = "bailian"

        const val THINKING_NONE = "none"
        const val THINKING_BINARY = "binary"
        const val THINKING_REASONING_EFFORT = "reasoning_effort"
    }

    data class BailianModelPreset(
        val id: String,
        val displayName: String,
        val modelName: String,
        val category: String,
        val apiType: String,
        val description: String,
        val toolNames: List<String> = emptyList(),
        val thinkingCapability: String = THINKING_NONE
    )

    val allPresets: List<BailianModelPreset> = listOf(
        BailianModelPreset(
            id = "bailian_qwen3.6_plus",
            displayName = "千问3.6-Plus (Responses API)",
            modelName = "qwen3.6-plus",
            category = "qwen",
            apiType = API_TYPE_RESPONSES,
            description = "支持全部官方工具：联网搜索/网页抓取/代码解释器/文搜图/图搜图",
            toolNames = listOf("web_search", "web_extractor", "code_interpreter", "web_search_image", "image_search"),
            thinkingCapability = THINKING_BINARY
        ),
        BailianModelPreset(
            id = "bailian_qwen3.5_plus",
            displayName = "千问3.5-Plus (Responses API)",
            modelName = "qwen3.5-plus",
            category = "qwen",
            apiType = API_TYPE_RESPONSES,
            description = "支持全部官方工具：联网搜索/网页抓取/代码解释器/文搜图/图搜图",
            toolNames = listOf("web_search", "web_extractor", "code_interpreter", "web_search_image", "image_search"),
            thinkingCapability = THINKING_BINARY
        ),
        BailianModelPreset(
            id = "bailian_deepseek_v4_pro",
            displayName = "DeepSeek V4 Pro (百炼托管)",
            modelName = "deepseek-v4-pro",
            category = "deepseek",
            apiType = API_TYPE_CHAT,
            description = "通过百炼调用，支持联网搜索，支持3档思考深度",
            toolNames = listOf("web_search"),
            thinkingCapability = THINKING_REASONING_EFFORT
        ),
        BailianModelPreset(
            id = "bailian_deepseek_v4_flash",
            displayName = "DeepSeek V4 Flash (百炼托管)",
            modelName = "deepseek-v4-flash",
            category = "deepseek",
            apiType = API_TYPE_CHAT,
            description = "通过百炼调用，支持联网搜索，支持3档思考深度",
            toolNames = listOf("web_search"),
            thinkingCapability = THINKING_REASONING_EFFORT
        ),
        BailianModelPreset(
            id = "bailian_glm_5_1",
            displayName = "GLM-5.1 (百炼托管)",
            modelName = "glm-5.1",
            category = "glm",
            apiType = API_TYPE_CHAT,
            description = "智谱GLM-5.1，通过百炼调用",
            toolNames = emptyList(),
            thinkingCapability = THINKING_BINARY
        ),
        BailianModelPreset(
            id = "bailian_glm_5",
            displayName = "GLM-5 (百炼托管)",
            modelName = "glm-5",
            category = "glm",
            apiType = API_TYPE_CHAT,
            description = "智谱GLM-5，通过百炼调用",
            toolNames = emptyList(),
            thinkingCapability = THINKING_BINARY
        ),
        BailianModelPreset(
            id = "bailian_kimi_k2_6",
            displayName = "Kimi K2.6 (百炼托管)",
            modelName = "kimi-k2.6",
            category = "kimi",
            apiType = API_TYPE_CHAT,
            description = "月之暗面Kimi K2.6，通过百炼调用",
            toolNames = emptyList(),
            thinkingCapability = THINKING_BINARY
        ),
        BailianModelPreset(
            id = "bailian_kimi_k2_5",
            displayName = "Kimi K2.5 (百炼托管)",
            modelName = "kimi-k2.5",
            category = "kimi",
            apiType = API_TYPE_CHAT,
            description = "月之暗面Kimi K2.5，通过百炼调用",
            toolNames = emptyList(),
            thinkingCapability = THINKING_BINARY
        ),
        BailianModelPreset(
            id = "bailian_minimax_m2_7",
            displayName = "MiniMax M2.7 (百炼托管)",
            modelName = "MiniMax-M2.7",
            category = "minimax",
            apiType = API_TYPE_CHAT,
            description = "稀宇科技MiniMax M2.7，通过百炼调用",
            toolNames = emptyList(),
            thinkingCapability = THINKING_BINARY
        ),
        BailianModelPreset(
            id = "bailian_minimax_m2_5",
            displayName = "MiniMax M2.5 (百炼托管)",
            modelName = "MiniMax-M2.5",
            category = "minimax",
            apiType = API_TYPE_CHAT,
            description = "稀宇科技MiniMax M2.5，通过百炼调用",
            toolNames = emptyList(),
            thinkingCapability = THINKING_BINARY
        ),
        BailianModelPreset(
            id = "bailian_qwen3_asr_flash",
            displayName = "千问3-ASR-Flash (语音转写)",
            modelName = "qwen3-asr-flash",
            category = "qwen",
            apiType = API_TYPE_STT,
            description = "阿里百炼语音转写模型，支持多语种音频识别",
            toolNames = emptyList(),
            thinkingCapability = THINKING_NONE
        )
    )

    val qwenPresets get() = allPresets.filter { it.category == "qwen" }
    val deepseekPresets get() = allPresets.filter { it.category == "deepseek" }

    fun isBailianModel(modelId: String): Boolean {
        return modelId.startsWith("bailian_")
    }

    fun isResponsesApiModel(modelConfig: ModelConfigItem): Boolean {
        return modelConfig.apiType == API_TYPE_RESPONSES
    }

    fun isBailianWithTools(modelConfig: ModelConfigItem): Boolean {
        if (!isBailianModel(modelConfig.id)) return false
        val preset = allPresets.find { it.id == modelConfig.id } ?: return false
        return preset.toolNames.isNotEmpty()
    }

    fun getAvailableTools(modelConfig: ModelConfigItem): List<String> {
        val preset = allPresets.find { it.id == modelConfig.id } ?: return emptyList()
        return preset.toolNames
    }

    fun getThinkingCapability(modelConfig: ModelConfigItem?): String {
        if (modelConfig == null) return THINKING_BINARY
        val preset = allPresets.find { it.id == modelConfig.id }
        return preset?.thinkingCapability ?: THINKING_BINARY
    }

    fun getPresetById(id: String): BailianModelPreset? {
        return allPresets.find { it.id == id }
    }

    fun initBailianPresets(apiKey: String) {
        if (apiKey.isBlank()) return
        val currentList = encryptedPrefsManager.getModelsList().toMutableList()
        var changed = false

        for (preset in allPresets) {
            val existingIndex = currentList.indexOfFirst { it.id == preset.id }
            val modelConfig = ModelConfigItem(
                id = preset.id,
                displayName = preset.displayName,
                baseUrl = BAILIAN_BASE_URL,
                apiKey = apiKey,
                modelName = preset.modelName,
                protocolStyle = EncryptedPrefsManager.PROTOCOL_OPENAI_COMPATIBLE,
                apiType = preset.apiType,
                provider = BAILIAN_PROVIDER
            )
            if (existingIndex >= 0) {
                currentList[existingIndex] = modelConfig
            } else {
                currentList.add(modelConfig)
            }
            changed = true
        }

        if (changed) {
            encryptedPrefsManager.saveModelsList(currentList)
        }
    }

    fun updateBailianApiKey(newApiKey: String) {
        val currentList = encryptedPrefsManager.getModelsList().toMutableList()
        var changed = false
        for (i in currentList.indices) {
            if (currentList[i].provider == BAILIAN_PROVIDER) {
                currentList[i] = currentList[i].copy(apiKey = newApiKey)
                changed = true
            }
        }
        if (changed) {
            encryptedPrefsManager.saveModelsList(currentList)
        }
    }

    fun removeBailianPresets() {
        val currentList = encryptedPrefsManager.getModelsList().toMutableList()
        val filtered = currentList.filter { it.provider != BAILIAN_PROVIDER }
        if (filtered.size != currentList.size) {
            encryptedPrefsManager.saveModelsList(filtered)
        }
    }
}
