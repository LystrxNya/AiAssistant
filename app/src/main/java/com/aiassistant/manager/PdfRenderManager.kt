package com.aiassistant.manager

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

sealed class PdfRenderState {
    data class Loading(val currentPage: Int, val totalPage: Int) : PdfRenderState()
    data class PageRendered(val pageIndex: Int, val totalPages: Int, val bitmap: Bitmap) : PdfRenderState()
    data object Success : PdfRenderState()
    data class Error(val message: String) : PdfRenderState()
}

@Singleton
class PdfRenderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val RENDER_DPI = 150f
    }

    fun renderPdf(uri: Uri): Flow<PdfRenderState> = flow {
        var document: PDDocument? = null
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("无法打开PDF文件")

            document = PDDocument.load(inputStream)
            inputStream.close()

            val renderer = PDFRenderer(document)
            val totalPages = document.numberOfPages

            for (pageIndex in 0 until totalPages) {
                emit(PdfRenderState.Loading(currentPage = pageIndex + 1, totalPage = totalPages))

                val bitmap = renderer.renderImageWithDPI(pageIndex, RENDER_DPI)
                emit(PdfRenderState.PageRendered(pageIndex, totalPages, bitmap))
            }

            emit(PdfRenderState.Success)
        } catch (e: OutOfMemoryError) {
            System.gc()
            emit(PdfRenderState.Error("PDF文件过大，内存不足，请尝试更小的文件"))
        } catch (e: Exception) {
            emit(PdfRenderState.Error("PDF解析失败: ${e.message}"))
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    fun getPageCount(uri: Uri): Int {
        var document: PDDocument? = null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return 0
            document = PDDocument.load(inputStream)
            inputStream.close()
            document.numberOfPages
        } catch (_: Exception) {
            0
        } finally {
            try { document?.close() } catch (_: Exception) {}
        }
    }
}
