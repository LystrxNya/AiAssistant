package com.aiassistant.ui.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.network.model.ChatMessage
import com.aiassistant.data.network.model.ChatRequest
import com.aiassistant.data.network.model.PromptTemplates
import com.aiassistant.data.network.ApiClient
import com.aiassistant.data.repository.ChatRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PasswordEntry(
    val id: String = "",
    val site: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = ""
)

data class PasswordUiState(
    val entries: List<PasswordEntry> = emptyList(),
    val isFormVisible: Boolean = false,
    val editingEntry: PasswordEntry? = null,
    val site: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val aiInput: String = "",
    val isExtracting: Boolean = false,
    val extractMessage: String? = null
)

// TODO: SECURITY RISK - Password entries are stored only via EncryptedSharedPreferences.
// EncryptedSharedPreferences encrypts at rest but the master key is protected only by the
// Android Keystore (no user passphrase). A rooted device or device with unlocked bootloader
// could extract the key. Consider adding an additional layer of encryption derived from a
// user-supplied passphrase or integrating with the Android Keystore more robustly (e.g.,
// requiring biometric auth per access, not just per screen entry).
@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val apiClient: ApiClient,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries() {
        val keys = encryptedPrefsManager.getAllPasswordEntryKeys()
        val entries = keys.mapNotNull { key ->
            try {
                val json = encryptedPrefsManager.getPasswordEntry(key) ?: return@mapNotNull null
                gson.fromJson(json, PasswordEntry::class.java)
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.site.lowercase() }
        _uiState.value = _uiState.value.copy(entries = entries)
    }

    fun showAddForm() {
        _uiState.value = _uiState.value.copy(
            isFormVisible = true,
            editingEntry = null,
            site = "",
            username = "",
            password = "",
            notes = ""
        )
    }

    fun showEditForm(entry: PasswordEntry) {
        _uiState.value = _uiState.value.copy(
            isFormVisible = true,
            editingEntry = entry,
            site = entry.site,
            username = entry.username,
            password = entry.password,
            notes = entry.notes
        )
    }

    fun hideForm() {
        _uiState.value = _uiState.value.copy(isFormVisible = false, extractMessage = null)
    }

    fun updateSite(value: String) {
        _uiState.value = _uiState.value.copy(site = value)
    }

    fun updateUsername(value: String) {
        _uiState.value = _uiState.value.copy(username = value)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun updateNotes(value: String) {
        _uiState.value = _uiState.value.copy(notes = value)
    }

    fun updateAiInput(value: String) {
        _uiState.value = _uiState.value.copy(aiInput = value)
    }

    fun saveEntry() {
        val state = _uiState.value
        if (state.site.isBlank()) return

        val entry = PasswordEntry(
            id = state.editingEntry?.id ?: System.currentTimeMillis().toString(),
            site = state.site.trim(),
            username = state.username.trim(),
            password = state.password,
            notes = state.notes.trim()
        )
        encryptedPrefsManager.savePasswordEntry(entry.id, gson.toJson(entry))
        loadEntries()
        hideForm()
    }

    fun deleteEntry(entry: PasswordEntry) {
        encryptedPrefsManager.removePasswordEntry(entry.id)
        loadEntries()
    }

    fun extractWithAi() {
        val text = _uiState.value.aiInput.trim()
        if (text.isBlank()) return

        if (!encryptedPrefsManager.isAuxModelConfigured()) {
            _uiState.value = _uiState.value.copy(extractMessage = "请先配置辅助小模型")
            return
        }

        _uiState.value = _uiState.value.copy(isExtracting = true, extractMessage = null)

        viewModelScope.launch {
            try {
                val messages = listOf(
                    ChatMessage(role = "system", content = PromptTemplates.PASSWORD_EXTRACT),
                    ChatMessage(role = "user", content = text)
                )
                val request = ChatRequest(
                    model = encryptedPrefsManager.getAuxModelName(),
                    messages = messages,
                    stream = false
                )
                val response = apiClient.sendAuxChatMessage(request)
                val content = response.choices?.firstOrNull()?.message?.content as? String ?: ""

                applyExtractedResult(content)
                _uiState.value = _uiState.value.copy(
                    isExtracting = false,
                    extractMessage = "提取成功，已自动填充"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExtracting = false,
                    extractMessage = "提取失败: ${e.message}"
                )
            }
        }
    }

    private fun applyExtractedResult(raw: String) {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        try {
            val json = JsonParser.parseString(cleaned).asJsonObject
            _uiState.value = _uiState.value.copy(
                site = json.get("site")?.asString ?: "",
                username = json.get("username")?.asString ?: "",
                password = json.get("password")?.asString ?: ""
            )
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(notes = cleaned)
        }
    }

    fun clearExtractMessage() {
        _uiState.value = _uiState.value.copy(extractMessage = null)
    }

    fun backupEntries(context: Context): String? {
        return try {
            val entries = _uiState.value.entries
            if (entries.isEmpty()) return null

            val json = gson.toJson(entries)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "password_backup_$timestamp.json"

            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val file = File(backupDir, fileName)
            file.writeText(json)
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
