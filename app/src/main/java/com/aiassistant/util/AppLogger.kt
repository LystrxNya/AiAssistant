package com.aiassistant.util

import com.aiassistant.data.dao.AppLogDao
import com.aiassistant.data.entity.AppLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppLogger {

    private var appLogDao: AppLogDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(dao: AppLogDao) {
        appLogDao = dao
    }

    fun log(module: String, event: String, errorTrace: String? = null) {
        scope.launch {
            try {
                appLogDao?.insert(
                    AppLogEntity(
                        module = sanitize(module),
                        event = sanitize(event),
                        errorTrace = errorTrace?.let { sanitize(it) }
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun sanitize(input: String): String {
        return input.replace(Regex("[\\r\\n\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), " ")
    }

    fun installGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(
                module = "System",
                event = "未捕获异常: ${throwable.message}",
                errorTrace = throwable.stackTraceToString()
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
