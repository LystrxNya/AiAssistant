package com.aiassistant.data.preset

import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.ModelConfigItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepSeekPresetManager @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    companion object {
        const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
        const val DEEPSEEK_PROVIDER = "deepseek_official"
    }

    data class DeepSeekModelPreset(
        val id: String,
        val displayName: String,
        val modelName: String,
        val description: String
    )

    val allPresets: List<DeepSeekModelPreset> = listOf(
        DeepSeekModelPreset(
            id = "deepseek_official_v4_pro",
            displayName = "DeepSeek V4 Pro (官方API)",
            modelName = "deepseek-v4-pro",
            description = "DeepSeek 最强模型，支持深度思考"
        ),
        DeepSeekModelPreset(
            id = "deepseek_official_v4_flash",
            displayName = "DeepSeek V4 Flash (官方API)",
            modelName = "deepseek-v4-flash",
            description = "DeepSeek 快速模型，平衡速度与质量"
        )
    )

    fun initDeepSeekPresets(apiKey: String) {
        if (apiKey.isBlank()) return
        val currentList = encryptedPrefsManager.getModelsList().toMutableList()
        var changed = false

        for (preset in allPresets) {
            val existingIndex = currentList.indexOfFirst { it.id == preset.id }
            val modelConfig = ModelConfigItem(
                id = preset.id,
                displayName = preset.displayName,
                baseUrl = DEEPSEEK_BASE_URL,
                apiKey = apiKey,
                modelName = preset.modelName,
                protocolStyle = EncryptedPrefsManager.PROTOCOL_OPENAI_COMPATIBLE,
                apiType = "chat",
                provider = DEEPSEEK_PROVIDER
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

    fun updateDeepSeekApiKey(newApiKey: String) {
        val currentList = encryptedPrefsManager.getModelsList().toMutableList()
        var changed = false
        for (i in currentList.indices) {
            if (currentList[i].provider == DEEPSEEK_PROVIDER) {
                currentList[i] = currentList[i].copy(apiKey = newApiKey)
                changed = true
            }
        }
        if (changed) {
            encryptedPrefsManager.saveModelsList(currentList)
        }
    }

    fun removeDeepSeekPresets() {
        val currentList = encryptedPrefsManager.getModelsList().toMutableList()
        val filtered = currentList.filter { it.provider != DEEPSEEK_PROVIDER }
        if (filtered.size != currentList.size) {
            encryptedPrefsManager.saveModelsList(filtered)
        }
    }

    fun isDeepSeekOfficial(modelId: String): Boolean {
        return modelId.startsWith("deepseek_official_")
    }
}
