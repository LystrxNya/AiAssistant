package com.aiassistant.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CompressedImageResult(
    val file: File,
    val base64: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uriSandboxManager: UriSandboxManager
) {

    companion object {
        private const val MAX_LONG_EDGE = 1024
        private const val MAX_FILE_SIZE_BYTES = 500 * 1024L
        private const val INITIAL_QUALITY = 80
        private const val MIN_QUALITY = 30
        private const val MAX_IMAGES = 9
    }

    suspend fun compressImage(sourceFile: File): CompressedImageResult = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        val sampleSize = calculateSampleSize(originalWidth, originalHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        val decodedBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: throw IllegalStateException("无法解码图片文件")

        val scaledBitmap = scaleToLongEdge(decodedBitmap, MAX_LONG_EDGE)
        if (scaledBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        var quality = INITIAL_QUALITY
        var compressedBytes: ByteArray

        do {
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressedBytes = outputStream.toByteArray()
            outputStream.close()
            quality -= 10
        } while (compressedBytes.size > MAX_FILE_SIZE_BYTES && quality >= MIN_QUALITY)

        val compressedFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
        compressedFile.writeBytes(compressedBytes)

        val base64 = "data:image/jpeg;base64," + Base64.encodeToString(
            compressedBytes,
            Base64.NO_WRAP
        )

        val resultWidth = scaledBitmap.width
        val resultHeight = scaledBitmap.height
        scaledBitmap.recycle()

        CompressedImageResult(
            file = compressedFile,
            base64 = base64,
            width = resultWidth,
            height = resultHeight,
            sizeBytes = compressedFile.length()
        )
    }

    suspend fun compressMultipleImages(
        sourceFiles: List<File>,
        maxCount: Int = MAX_IMAGES
    ): List<CompressedImageResult> = withContext(Dispatchers.IO) {
        val filesToProcess = sourceFiles.take(maxCount)
        filesToProcess.map { file -> compressImage(file) }
    }

    fun validateImageCount(count: Int): Boolean {
        return count in 1..MAX_IMAGES
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val longEdge = maxOf(width, height)
        while (longEdge / sampleSize > MAX_LONG_EDGE * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun scaleToLongEdge(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longEdge = maxOf(width, height)

        if (longEdge <= maxLongEdge) return bitmap

        val scale = maxLongEdge.toFloat() / longEdge
        val matrix = Matrix().apply {
            postScale(scale, scale)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }
}
