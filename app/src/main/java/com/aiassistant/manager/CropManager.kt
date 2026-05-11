package com.aiassistant.manager

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CropManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createCropOptions(sourceUri: Uri): UCrop {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val destFile = File(context.cacheDir, "CROP_${timeStamp}.jpg")
        val destUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )

        return UCrop.of(sourceUri, destUri)
            .withOptions(
                UCrop.Options().apply {
                    setCompressionQuality(90)
                    setHideBottomControls(false)
                    setFreeStyleCropEnabled(true)
                    setShowCropGrid(true)
                    setShowCropFrame(true)
                    setStatusBarColor(0xFF2E7D32.toInt())
                    setToolbarColor(0xFF2E7D32.toInt())
                    setActiveControlsWidgetColor(0xFF2E7D32.toInt())
                }
            )
            .withAspectRatio(0f, 0f)
            .withMaxResultSize(2048, 2048)
    }

    fun getCropResult(data: android.content.Intent?): Uri? {
        return UCrop.getOutput(data ?: return null)
    }

    fun getCropError(data: android.content.Intent?): Throwable? {
        return UCrop.getError(data ?: return null)
    }

    fun deleteCropCache(uri: Uri) {
        try {
            val path = uri.path ?: return
            val file = File(path)
            if (file.exists() && file.parentFile?.canonicalPath == context.cacheDir.canonicalPath) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
