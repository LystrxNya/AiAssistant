package com.aiassistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.ModelConfigItem
import com.aiassistant.data.network.ApiClient
import com.aiassistant.data.preset.BailianPresetManager
import com.aiassistant.data.preset.DeepSeekPresetManager
import com.aiassistant.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ModelConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val protocolStyle: String = EncryptedPrefsManager.PROTOCOL_OPENAI_COMPATIBLE
)

data class SearchApiConfig(
    val baseUrl: String = "",
    val apiKey: String = ""
)

data class ConnectivityState(
    val isTesting: Boolean = false,
    val result: ConnectivityResult = ConnectivityResult.Idle
)

sealed class ConnectivityResult {
    data object Idle : ConnectivityResult()
    data class Success(val message: String) : ConnectivityResult()
    data class Failure(val error: String) : ConnectivityResult()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val chatRepository: ChatRepository,
    private val apiClient: ApiClient,
    private val bailianPresetManager: BailianPresetManager,
    private val deepSeekPresetManager: DeepSeekPresetManager
) : ViewModel() {

    private val _searchApi = MutableStateFlow(SearchApiConfig())
    val searchApi: StateFlow<SearchApiConfig> = _searchApi.asStateFlow()

    private val _searchMode = MutableStateFlow(encryptedPrefsManager.getSearchMode())
    val searchMode: StateFlow<String> = _searchMode.asStateFlow()

    private val _searchConnectivity = MutableStateFlow(ConnectivityState())
    val searchConnectivity: StateFlow<ConnectivityState> = _searchConnectivity.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _modelsList = MutableStateFlow<List<ModelConfigItem>>(emptyList())
    val modelsList: StateFlow<List<ModelConfigItem>> = _modelsList.asStateFlow()

    private val _mainModelId = MutableStateFlow("")
    val mainModelId: StateFlow<String> = _mainModelId.asStateFlow()

    private val _visionModelId = MutableStateFlow("")
    val visionModelId: StateFlow<String> = _visionModelId.asStateFlow()

    private val _auxModelId = MutableStateFlow("")
    val auxModelId: StateFlow<String> = _auxModelId.asStateFlow()

    private val _sttModelId = MutableStateFlow("")
    val sttModelId: StateFlow<String> = _sttModelId.asStateFlow()

    private val _editingModel = MutableStateFlow<ModelConfigItem?>(null)
    val editingModel: StateFlow<ModelConfigItem?> = _editingModel.asStateFlow()

    init {
        loadConfigs()
    }

    private fun loadConfigs() {
        _searchApi.value = SearchApiConfig(
            baseUrl = encryptedPrefsManager.getSearchApiBaseUrl(),
            apiKey = encryptedPrefsManager.getSearchApiKey() ?: ""
        )
        _modelsList.value = encryptedPrefsManager.getModelsList()
        _mainModelId.value = encryptedPrefsManager.getMainModelId()
        _visionModelId.value = encryptedPrefsManager.getVisionModelId()
        _auxModelId.value = encryptedPrefsManager.getAuxModelId()
        _sttModelId.value = encryptedPrefsManager.getSttModelId()
    }

    fun updateSearchBaseUrl(url: String) {
        _searchApi.value = _searchApi.value.copy(baseUrl = url)
    }

    fun updateSearchApiKey(key: String) {
        _searchApi.value = _searchApi.value.copy(apiKey = key)
    }

    fun saveSearchApi() {
        val config = _searchApi.value
        encryptedPrefsManager.saveSearchApiBaseUrl(config.baseUrl)
        encryptedPrefsManager.saveSearchApiKey(config.apiKey)
        _saveMessage.value = "联网搜索服务配置已保存"
    }

    fun saveSearchMode(mode: String) {
        _searchMode.value = mode
        encryptedPrefsManager.saveSearchMode(mode)
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

    fun startEditModel(model: ModelConfigItem) {
        _editingModel.value = model
    }

    fun startNewModel() {
        _editingModel.value = ModelConfigItem(
            id = UUID.randomUUID().toString(),
            displayName = "",
            baseUrl = encryptedPrefsManager.getBaseUrl(),
            apiKey = "",
            modelName = "",
            protocolStyle = EncryptedPrefsManager.PROTOCOL_OPENAI_COMPATIBLE
        )
    }

    fun updateEditingModel(model: ModelConfigItem) {
        _editingModel.value = model
    }

    fun saveEditingModel() {
        val model = _editingModel.value ?: return
        val currentList = _modelsList.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == model.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = model
        } else {
            currentList.add(model)
        }
        encryptedPrefsManager.saveModelsList(currentList)
        _modelsList.value = currentList
        _editingModel.value = null
        apiClient.invalidateDynamicCache()
        _saveMessage.value = "模型「${model.displayName}」已保存"
    }

    fun cancelEditModel() {
        _editingModel.value = null
    }

    fun deleteModel(modelId: String) {
        val currentList = _modelsList.value.toMutableList()
        currentList.removeAll { it.id == modelId }
        encryptedPrefsManager.saveModelsList(currentList)
        _modelsList.value = currentList
        if (_mainModelId.value == modelId) {
            _mainModelId.value = ""
            encryptedPrefsManager.saveMainModelId("")
        }
        if (_visionModelId.value == modelId) {
            _visionModelId.value = ""
            encryptedPrefsManager.saveVisionModelId("")
        }
        if (_auxModelId.value == modelId) {
            _auxModelId.value = ""
            encryptedPrefsManager.saveAuxModelId("")
        }
        if (_sttModelId.value == modelId) {
            _sttModelId.value = ""
            encryptedPrefsManager.saveSttModelId("")
        }
        apiClient.invalidateDynamicCache()
        _saveMessage.value = "模型已删除"
    }

    fun setMainModel(modelId: String) {
        _mainModelId.value = modelId
        encryptedPrefsManager.saveMainModelId(modelId)
    }

    fun setVisionModel(modelId: String) {
        _visionModelId.value = modelId
        encryptedPrefsManager.saveVisionModelId(modelId)
    }

    fun setAuxModel(modelId: String) {
        _auxModelId.value = modelId
        encryptedPrefsManager.saveAuxModelId(modelId)
    }

    fun setSttModel(modelId: String) {
        _sttModelId.value = modelId
        encryptedPrefsManager.saveSttModelId(modelId)
    }

    fun resetSearchConnectivity() {
        _searchConnectivity.value = ConnectivityState()
    }

    private val _enabledNavItems = MutableStateFlow(encryptedPrefsManager.getEnabledNavItems())
    val enabledNavItems: StateFlow<Set<String>> = _enabledNavItems.asStateFlow()

    val allNavItems = listOf(
        "chat" to "对话",
        "ocr" to "图文识别",
        "transcription" to "转写",
        "todo" to "待办",
        "roleplay" to "角色扮演",
        "password" to "密码本",
        "log" to "日志",
        "settings" to "设置"
    )

    fun toggleNavItem(route: String) {
        val current = _enabledNavItems.value.toMutableSet()
        if (route in current) {
            if (current.size > 1) {
                current.remove(route)
            }
        } else {
            current.add(route)
        }
        _enabledNavItems.value = current
        encryptedPrefsManager.saveEnabledNavItems(current)
    }

    private val _bailianConfigured = MutableStateFlow(encryptedPrefsManager.getBailianApiKey() != null)
    val bailianConfigured: StateFlow<Boolean> = _bailianConfigured.asStateFlow()

    fun setupBailianPresets(apiKey: String) {
        if (!isValidApiKey(apiKey)) {
            _saveMessage.value = "API Key 格式不正确，不应包含中文或特殊字符"
            return
        }
        bailianPresetManager.initBailianPresets(apiKey)
        encryptedPrefsManager.saveBailianApiKey(apiKey)
        _modelsList.value = encryptedPrefsManager.getModelsList()
        _bailianConfigured.value = true
        apiClient.invalidateDynamicCache()
        // Auto-select Bailian STT model if not configured yet
        if (_sttModelId.value.isBlank()) {
            val sttPreset = encryptedPrefsManager.getModelById("bailian_qwen3_asr_flash")
            if (sttPreset != null) {
                _sttModelId.value = sttPreset.id
                encryptedPrefsManager.saveSttModelId(sttPreset.id)
            }
        }
        _saveMessage.value = "百炼模型已配置，共11个模型可用"
    }

    fun updateBailianApiKey(newApiKey: String) {
        if (!isValidApiKey(newApiKey)) {
            _saveMessage.value = "API Key 格式不正确，不应包含中文或特殊字符"
            return
        }
        bailianPresetManager.updateBailianApiKey(newApiKey)
        encryptedPrefsManager.saveBailianApiKey(newApiKey)
        _modelsList.value = encryptedPrefsManager.getModelsList()
        apiClient.invalidateDynamicCache()
        _saveMessage.value = "百炼API Key已更新"
    }

    fun removeBailianPresets() {
        bailianPresetManager.removeBailianPresets()
        encryptedPrefsManager.clearBailianApiKey()
        _modelsList.value = encryptedPrefsManager.getModelsList()
        _bailianConfigured.value = false
        apiClient.invalidateDynamicCache()
        _saveMessage.value = "百炼模型已移除"
    }

    fun getBailianPresets() = bailianPresetManager.allPresets

    private val _deepseekConfigured = MutableStateFlow(encryptedPrefsManager.getDeepSeekApiKey() != null)
    val deepseekConfigured: StateFlow<Boolean> = _deepseekConfigured.asStateFlow()

    fun setupDeepSeekPresets(apiKey: String) {
        deepSeekPresetManager.initDeepSeekPresets(apiKey)
        encryptedPrefsManager.saveDeepSeekApiKey(apiKey)
        _modelsList.value = encryptedPrefsManager.getModelsList()
        _deepseekConfigured.value = true
        apiClient.invalidateDynamicCache()
        _saveMessage.value = "DeepSeek 官方模型已配置"
    }

    fun updateDeepSeekApiKey(newApiKey: String) {
        deepSeekPresetManager.updateDeepSeekApiKey(newApiKey)
        encryptedPrefsManager.saveDeepSeekApiKey(newApiKey)
        _modelsList.value = encryptedPrefsManager.getModelsList()
        apiClient.invalidateDynamicCache()
        _saveMessage.value = "DeepSeek API Key已更新"
    }

    fun removeDeepSeekPresets() {
        deepSeekPresetManager.removeDeepSeekPresets()
        encryptedPrefsManager.clearDeepSeekApiKey()
        _modelsList.value = encryptedPrefsManager.getModelsList()
        _deepseekConfigured.value = false
        apiClient.invalidateDynamicCache()
        _saveMessage.value = "DeepSeek 官方模型已移除"
    }

    fun getDeepSeekPresets() = deepSeekPresetManager.allPresets

    private fun isValidApiKey(key: String): Boolean {
        return key.isNotBlank() && key.all { it.code in 0x20..0x7E }
    }
}
