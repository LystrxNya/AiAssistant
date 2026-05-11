package com.aiassistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ModelConfigItem(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val protocolStyle: String = EncryptedPrefsManager.PROTOCOL_OPENAI_COMPATIBLE,
    val apiType: String = "chat",
    val provider: String = ""
)

@Singleton
class EncryptedPrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(provider: String, apiKey: String) {
        prefs.edit().putString("${KEY_API_KEY_PREFIX}$provider", apiKey).apply()
    }

    fun getApiKey(provider: String): String? {
        return prefs.getString("${KEY_API_KEY_PREFIX}$provider", null)
    }

    fun removeApiKey(provider: String) {
        prefs.edit().remove("${KEY_API_KEY_PREFIX}$provider").apply()
    }

    fun savePasswordEntry(id: String, encryptedData: String) {
        prefs.edit().putString("${KEY_PASSWORD_PREFIX}$id", encryptedData).apply()
    }

    fun getPasswordEntry(id: String): String? {
        return prefs.getString("${KEY_PASSWORD_PREFIX}$id", null)
    }

    fun removePasswordEntry(id: String) {
        prefs.edit().remove("${KEY_PASSWORD_PREFIX}$id").apply()
    }

    fun getAllPasswordEntryKeys(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_PASSWORD_PREFIX) }
            .map { it.removePrefix(KEY_PASSWORD_PREFIX) }
            .toSet()
    }

    fun getAllApiKeyProviders(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_API_KEY_PREFIX) }
            .map { it.removePrefix(KEY_API_KEY_PREFIX) }
            .toSet()
    }

    fun saveBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_BASE_URL, baseUrl.trimEnd('/')).apply()
    }

    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun saveModelName(model: String) {
        prefs.edit().putString(KEY_MODEL_NAME, model).apply()
    }

    fun getModelName(): String {
        return prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL_NAME) ?: DEFAULT_MODEL_NAME
    }

    fun saveAuxBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_AUX_BASE_URL, baseUrl.trimEnd('/')).apply()
    }

    fun getAuxBaseUrl(): String {
        return prefs.getString(KEY_AUX_BASE_URL, "") ?: ""
    }

    fun saveAuxApiKey(apiKey: String) {
        prefs.edit().putString(KEY_AUX_API_KEY, apiKey).apply()
    }

    fun getAuxApiKey(): String? {
        return prefs.getString(KEY_AUX_API_KEY, null)
    }

    fun saveAuxModelName(model: String) {
        prefs.edit().putString(KEY_AUX_MODEL_NAME, model).apply()
    }

    fun getAuxModelName(): String {
        return prefs.getString(KEY_AUX_MODEL_NAME, DEFAULT_AUX_MODEL_NAME) ?: DEFAULT_AUX_MODEL_NAME
    }

    fun isMainModelConfigured(): Boolean {
        val apiKey = getApiKey("default")
        val baseUrl = getBaseUrl()
        return !apiKey.isNullOrBlank() && baseUrl.isNotBlank()
    }

    fun saveVisionBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_VISION_BASE_URL, baseUrl.trimEnd('/')).apply()
    }

    fun getVisionBaseUrl(): String {
        return prefs.getString(KEY_VISION_BASE_URL, "") ?: ""
    }

    fun saveVisionApiKey(apiKey: String) {
        prefs.edit().putString(KEY_VISION_API_KEY, apiKey).apply()
    }

    fun getVisionApiKey(): String? {
        return prefs.getString(KEY_VISION_API_KEY, null)
    }

    fun saveVisionModelName(model: String) {
        prefs.edit().putString(KEY_VISION_MODEL_NAME, model).apply()
    }

    fun getVisionModelName(): String {
        return prefs.getString(KEY_VISION_MODEL_NAME, DEFAULT_VISION_MODEL_NAME) ?: DEFAULT_VISION_MODEL_NAME
    }

    fun saveVisionProtocolStyle(style: String) {
        prefs.edit().putString(KEY_VISION_PROTOCOL_STYLE, style).apply()
    }

    fun getVisionProtocolStyle(): String {
        return prefs.getString(KEY_VISION_PROTOCOL_STYLE, PROTOCOL_OPENAI_COMPATIBLE) ?: PROTOCOL_OPENAI_COMPATIBLE
    }

    fun isVisionModelConfigured(): Boolean {
        val apiKey = getVisionApiKey()
        val baseUrl = getVisionBaseUrl()
        return !apiKey.isNullOrBlank() && !baseUrl.isNullOrBlank()
    }

    fun isAuxModelConfigured(): Boolean {
        val apiKey = getAuxApiKey()
        val baseUrl = getAuxBaseUrl()
        return !apiKey.isNullOrBlank() && !baseUrl.isNullOrBlank()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private val gson = Gson()

    fun saveModelsList(models: List<ModelConfigItem>) {
        prefs.edit().putString(KEY_MODELS_LIST, gson.toJson(models)).apply()
    }

    fun getModelsList(): List<ModelConfigItem> {
        val json = prefs.getString(KEY_MODELS_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ModelConfigItem>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getModelById(id: String): ModelConfigItem? {
        return getModelsList().find { it.id == id }
    }

    fun saveVisionModelId(id: String) {
        prefs.edit().putString(KEY_VISION_MODEL_ID, id).apply()
    }

    fun getVisionModelId(): String {
        return prefs.getString(KEY_VISION_MODEL_ID, "") ?: ""
    }

    fun saveAuxModelId(id: String) {
        prefs.edit().putString(KEY_AUX_MODEL_ID, id).apply()
    }

    fun getAuxModelId(): String {
        return prefs.getString(KEY_AUX_MODEL_ID, "") ?: ""
    }

    fun saveMainModelId(id: String) {
        prefs.edit().putString(KEY_MAIN_MODEL_ID, id).apply()
    }

    fun getMainModelId(): String {
        return prefs.getString(KEY_MAIN_MODEL_ID, "") ?: ""
    }

    fun saveSttModelId(id: String) {
        prefs.edit().putString(KEY_STT_MODEL_ID, id).apply()
    }

    fun getSttModelId(): String {
        return prefs.getString(KEY_STT_MODEL_ID, "") ?: ""
    }

    fun isSttModelConfigured(): Boolean {
        val sttModelId = getSttModelId()
        if (sttModelId.isBlank()) return false
        return getModelById(sttModelId) != null
    }

    fun saveMainProtocolStyle(style: String) {
        prefs.edit().putString(KEY_MAIN_PROTOCOL_STYLE, style).apply()
    }

    fun getMainProtocolStyle(): String {
        return prefs.getString(KEY_MAIN_PROTOCOL_STYLE, PROTOCOL_OPENAI_COMPATIBLE) ?: PROTOCOL_OPENAI_COMPATIBLE
    }

    fun saveAuxProtocolStyle(style: String) {
        prefs.edit().putString(KEY_AUX_PROTOCOL_STYLE, style).apply()
    }

    fun getAuxProtocolStyle(): String {
        return prefs.getString(KEY_AUX_PROTOCOL_STYLE, PROTOCOL_OPENAI_COMPATIBLE) ?: PROTOCOL_OPENAI_COMPATIBLE
    }

    fun saveSearchApiBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_SEARCH_API_BASE_URL, baseUrl.trimEnd('/')).apply()
    }

    fun getSearchApiBaseUrl(): String {
        return prefs.getString(KEY_SEARCH_API_BASE_URL, "") ?: ""
    }

    fun saveSearchApiKey(apiKey: String) {
        prefs.edit().putString(KEY_SEARCH_API_KEY, apiKey).apply()
    }

    fun getSearchApiKey(): String? {
        return prefs.getString(KEY_SEARCH_API_KEY, null)
    }

    fun isSearchConfigured(): Boolean {
        return getSearchApiBaseUrl().isNotBlank()
    }

    fun saveSearchMode(mode: String) {
        prefs.edit().putString(KEY_SEARCH_MODE, mode).apply()
    }

    fun getSearchMode(): String {
        return prefs.getString(KEY_SEARCH_MODE, SEARCH_MODE_DEEPSEEK) ?: SEARCH_MODE_DEEPSEEK
    }

    fun isDeepSeekSearchReady(): Boolean {
        return getBailianApiKey()?.isNotBlank() == true
    }

    fun isThirdPartySearchReady(): Boolean {
        return getSearchApiBaseUrl().isNotBlank()
    }

    fun isSearchReady(): Boolean {
        return when (getSearchMode()) {
            SEARCH_MODE_DEEPSEEK -> isDeepSeekSearchReady()
            SEARCH_MODE_THIRD_PARTY -> isThirdPartySearchReady()
            else -> false
        }
    }

    fun saveEnabledNavItems(items: Set<String>) {
        prefs.edit().putString(KEY_ENABLED_NAV_ITEMS, gson.toJson(items.toList())).apply()
    }

    fun getEnabledNavItems(): Set<String> {
        val json = prefs.getString(KEY_ENABLED_NAV_ITEMS, null)
        return if (json.isNullOrBlank()) {
            DEFAULT_NAV_ITEMS
        } else {
            try {
                val arr = com.google.gson.JsonParser.parseString(json).asJsonArray
                arr.map { it.asString }.toSet()
            } catch (_: Exception) {
                DEFAULT_NAV_ITEMS
            }
        }
    }

    fun enabledNavItemsFlow(): kotlinx.coroutines.flow.Flow<Set<String>> {
        return callbackFlow {
            trySend(getEnabledNavItems())
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_ENABLED_NAV_ITEMS) {
                    trySend(getEnabledNavItems())
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun getBailianApiKey(): String? {
        return prefs.getString(KEY_BAILIAN_API_KEY, null)
    }

    fun saveBailianApiKey(apiKey: String) {
        prefs.edit().putString(KEY_BAILIAN_API_KEY, apiKey).apply()
    }

    fun clearBailianApiKey() {
        prefs.edit().remove(KEY_BAILIAN_API_KEY).apply()
    }

    fun getDeepSeekApiKey(): String? {
        return prefs.getString(KEY_DEEPSEEK_API_KEY, null)
    }

    fun saveDeepSeekApiKey(apiKey: String) {
        prefs.edit().putString(KEY_DEEPSEEK_API_KEY, apiKey).apply()
    }

    fun clearDeepSeekApiKey() {
        prefs.edit().remove(KEY_DEEPSEEK_API_KEY).apply()
    }

    companion object {
        val DEFAULT_NAV_ITEMS = setOf("chat", "ocr", "transcription", "todo", "settings")
        private const val PREFS_FILE_NAME = "ai_assistant_encrypted_prefs"
        private const val KEY_API_KEY_PREFIX = "api_key_"
        private const val KEY_PASSWORD_PREFIX = "pwd_"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_AUX_BASE_URL = "aux_base_url"
        private const val KEY_AUX_API_KEY = "aux_api_key"
        private const val KEY_AUX_MODEL_NAME = "aux_model_name"
        private const val KEY_MAIN_PROTOCOL_STYLE = "main_protocol_style"
        private const val KEY_AUX_PROTOCOL_STYLE = "aux_protocol_style"
        private const val KEY_SEARCH_API_BASE_URL = "search_api_base_url"
        private const val KEY_SEARCH_API_KEY = "search_api_key"
        private const val KEY_ENABLED_NAV_ITEMS = "enabled_nav_items"
        private const val KEY_VISION_BASE_URL = "vision_base_url"
        private const val KEY_VISION_API_KEY = "vision_api_key"
        private const val KEY_VISION_MODEL_NAME = "vision_model_name"
        private const val KEY_VISION_PROTOCOL_STYLE = "vision_protocol_style"
        private const val KEY_MODELS_LIST = "models_list"
        private const val KEY_MAIN_MODEL_ID = "main_model_id"
        private const val KEY_VISION_MODEL_ID = "vision_model_id"
        private const val KEY_AUX_MODEL_ID = "aux_model_id"
        private const val KEY_STT_MODEL_ID = "stt_model_id"
        private const val KEY_BAILIAN_API_KEY = "bailian_api_key"
        private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
        private const val KEY_SEARCH_MODE = "search_mode"
        const val SEARCH_MODE_DEEPSEEK = "deepseek"
        const val SEARCH_MODE_THIRD_PARTY = "thirdparty"
        const val DEFAULT_BASE_URL = "https://api.openai.com"
        const val DEFAULT_MODEL_NAME = "gpt-4o"
        const val DEFAULT_AUX_MODEL_NAME = "gpt-4o-mini"
        const val DEFAULT_VISION_MODEL_NAME = "gemini-1.5-flash"
        const val PROTOCOL_OPENAI_COMPATIBLE = "openai_compatible"
        const val PROTOCOL_PURE_BASE64 = "pure_base64"
        const val PROTOCOL_LOCAL = "local"
    }
}
