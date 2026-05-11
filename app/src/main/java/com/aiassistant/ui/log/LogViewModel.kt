package com.aiassistant.ui.log

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.dao.AppLogDao
import com.aiassistant.data.entity.AppLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val appLogDao: AppLogDao,
    application: Application
) : AndroidViewModel(application) {

    private val _selectedModule = MutableStateFlow<String?>(null)
    val selectedModule: StateFlow<String?> = _selectedModule.asStateFlow()

    val modules = listOf("全部", "A-对话", "B-OCR", "C-录音", "D-待办", "E-设置", "F-密码本", "System")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val logs: StateFlow<List<AppLogEntity>> = _selectedModule.flatMapLatest { module ->
        if (module == null || module == "全部") {
            appLogDao.getAllLogs()
        } else {
            val dbModule = module.substringAfter("-")
            appLogDao.getLogsByModule(dbModule)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    fun selectModule(module: String) {
        _selectedModule.value = if (module == "全部") null else module
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            appLogDao.deleteAll()
        }
    }

    fun exportLogs() {
        viewModelScope.launch {
            try {
                val allLogs = appLogDao.getAllLogsOnce()
                if (allLogs.isEmpty()) {
                    _exportResult.value = "暂无日志可导出"
                    return@launch
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val content = buildString {
                    appendLine("=== AI助手 日志导出 ===")
                    appendLine("导出时间: ${sdf.format(Date())}")
                    appendLine("日志总数: ${allLogs.size}")
                    appendLine("========================\n")
                    allLogs.forEach { log ->
                        appendLine("[${sdf.format(Date(log.timestamp))}] [${log.module}] ${log.event}")
                        if (!log.errorTrace.isNullOrBlank()) {
                            appendLine("  堆栈: ${log.errorTrace.take(500)}")
                        }
                        appendLine()
                    }
                }

                val context = getApplication<Application>()
                val logDir = File(context.cacheDir, "logs")
                logDir.mkdirs()
                val exportFile = File(logDir, "app_logs_${System.currentTimeMillis()}.txt")
                exportFile.writeText(content)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "AI助手日志")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "导出日志").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })

                _exportResult.value = "日志已导出，请选择分享方式"
            } catch (e: Exception) {
                _exportResult.value = "导出失败: ${e.message}"
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }
}
