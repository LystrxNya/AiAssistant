package com.aiassistant.data.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

class ProgressRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType() = contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = file.length()
        var bytesWritten = 0L
        val bufferSize = 8192L

        file.source().use { source ->
            var read: Long
            val buffer = okio.Buffer()
            while (source.read(buffer, bufferSize).also { read = it } != -1L) {
                sink.write(buffer, read)
                bytesWritten += read
                onProgress(bytesWritten, totalBytes)
            }
        }
    }
}