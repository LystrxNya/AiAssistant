package com.aiassistant.manager

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class SandboxResult {
    data class Success(val file: File) : SandboxResult()
    data class Error(val message: String, val exception: Exception? = null) : SandboxResult()
}

@Singleton
class UriSandboxManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun sandboxFile(uri: Uri, mimeType: String): SandboxResult = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext SandboxResult.Error("无法打开文件: 内容提供者返回空流")

            val extension = getExtensionFromMimeType(mimeType)
            val tempFile = File(context.cacheDir, "tmp_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}$extension")

            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            SandboxResult.Success(tempFile)
        } catch (e: SecurityException) {
            SandboxResult.Error("文件访问被拒绝，请重新选择文件", e)
        } catch (e: FileNotFoundException) {
            SandboxResult.Error("文件未找到，可能已被删除或移动", e)
        } catch (e: IOException) {
            SandboxResult.Error("文件读取失败: ${e.message}", e)
        } catch (e: Exception) {
            SandboxResult.Error("文件处理异常: ${e.message}", e)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    suspend fun sandboxMultipleFiles(
        uris: List<Uri>,
        mimeType: String
    ): List<SandboxResult> = withContext(Dispatchers.IO) {
        uris.map { uri -> sandboxFile(uri, mimeType) }
    }

    fun cleanupTempFiles() {
        try {
            val prefixes = listOf("tmp_", "compressed_", "transcoded_", "crop_")
            context.cacheDir.listFiles()?.filter { file ->
                prefixes.any { prefix -> file.name.startsWith(prefix) }
            }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    fun cleanupSpecificFile(file: File) {
        try {
            if (file.exists() && file.parentFile?.canonicalPath == context.cacheDir.canonicalPath) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    private fun getExtensionFromMimeType(mimeType: String): String {
        return when {
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
            mimeType.contains("png") -> ".png"
            mimeType.contains("webp") -> ".webp"
            mimeType.contains("gif") -> ".gif"
            mimeType.contains("pdf") -> ".pdf"
            mimeType.contains("mp3") -> ".mp3"
            mimeType.contains("wav") -> ".wav"
            mimeType.contains("m4a") -> ".m4a"
            mimeType.contains("flac") -> ".flac"
            mimeType.contains("audio") -> ".audio"
            mimeType.contains("image") -> ".jpg"
            else -> ".bin"
        }
    }
}
