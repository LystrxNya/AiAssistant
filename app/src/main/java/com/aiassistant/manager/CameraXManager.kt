package com.aiassistant.manager

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraXManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createTempImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(context.cacheDir, "CAMERA_${timeStamp}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    fun getCacheFile(uri: Uri): File? {
        return try {
            val path = uri.path ?: return null
            val cachePath = File(context.cacheDir, File(path).name)
            if (cachePath.exists()) cachePath else null
        } catch (e: Exception) {
            null
        }
    }

    fun deleteCacheFile(uri: Uri) {
        try {
            val path = uri.path ?: return
            val file = File(path)
            if (file.exists() && file.parentFile?.canonicalPath == context.cacheDir.canonicalPath) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    fun deleteAllCameraCache() {
        try {
            context.cacheDir.listFiles()?.filter {
                it.name.startsWith("CAMERA_") || it.name.startsWith("CROP_")
            }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
