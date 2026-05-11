package com.aiassistant.manager

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Base64
import android.util.Base64OutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed class TranscodeState {
    data object Idle : TranscodeState()
    data class Transcoding(val progress: Int) : TranscodeState()
    data class Success(val file: File, val base64: String, val durationMs: Long) : TranscodeState()
    data class Error(val message: String) : TranscodeState()
}

@Singleton
class AudioTranscoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uriSandboxManager: UriSandboxManager
) {

    companion object {
        private const val TARGET_BIT_RATE = 64_000
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNEL_COUNT = 1
        private const val MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024L
        private val SUPPORTED_FORMATS = setOf("audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/mp4", "audio/m4a", "audio/flac", "audio/aac")
    }

    fun isFormatSupported(mimeType: String): Boolean {
        return SUPPORTED_FORMATS.any { mimeType.contains(it, ignoreCase = true) }
    }

    fun getFileSizeLimit(): Long = MAX_FILE_SIZE_BYTES

    fun transcodeAudio(sourceFile: File, mimeType: String): Flow<TranscodeState> = channelFlow {
        send(TranscodeState.Transcoding(0))

        if (sourceFile.length() > MAX_FILE_SIZE_BYTES) {
            send(TranscodeState.Error("音频文件超过20MB限制，请压缩后重试"))
            close()
            return@channelFlow
        }

        if (!isFormatSupported(mimeType)) {
            send(TranscodeState.Error("不支持的音频格式: $mimeType"))
            close()
            return@channelFlow
        }

        try {
            val outputFile = File(context.cacheDir, "transcoded_${System.currentTimeMillis()}.mp3")

            if (isAlreadyLowBitrate(sourceFile)) {
                sourceFile.copyTo(outputFile, overwrite = true)
                send(TranscodeState.Transcoding(100))
            } else {
                val job = coroutineContext[Job]!!
                transcodeWithMediaCodec(sourceFile, outputFile, isCancelled = { !job.isActive }) { progress ->
                    trySend(TranscodeState.Transcoding(progress))
                }
            }

            send(TranscodeState.Transcoding(90))

            val base64 = encodeFileToBase64(outputFile)

            val durationMs = getAudioDuration(outputFile)

            send(TranscodeState.Transcoding(100))
            send(TranscodeState.Success(file = outputFile, base64 = base64, durationMs = durationMs))
        } catch (e: Exception) {
            send(TranscodeState.Error("音频转码失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun isAlreadyLowBitrate(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.trackCount > 0) {
                val format = extractor.getTrackFormat(0)
                val bitRate = format.getIntegerOrDefault(MediaFormat.KEY_BIT_RATE, 0)
                bitRate in 1 until TARGET_BIT_RATE
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            extractor.release()
        }
    }

    private fun transcodeWithMediaCodec(
        sourceFile: File,
        outputFile: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null

        try {
            extractor.setDataSource(sourceFile.absolutePath)

            val trackIndex = findAudioTrack(extractor) ?: throw IllegalStateException("未找到音频轨道")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)

            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                TARGET_SAMPLE_RATE,
                TARGET_CHANNEL_COUNT
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BIT_RATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val decoderMimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            decoder = MediaCodec.createDecoderByType(decoderMimeType)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var inputDone = false
            var outputDone = false
            val totalDuration = inputFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)

            while (!outputDone) {
                if (isCancelled()) break

                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (decoderOutputIndex >= 0) {
                    val decodedBuffer = decoder.getOutputBuffer(decoderOutputIndex) ?: continue
                    if (bufferInfo.size > 0) {
                        val encoderInputIndex = encoder.dequeueInputBuffer(timeoutUs)
                        if (encoderInputIndex >= 0) {
                            val encoderInputBuffer = encoder.getInputBuffer(encoderInputIndex) ?: continue
                            encoderInputBuffer.clear()
                            encoderInputBuffer.put(decodedBuffer)
                            encoder.queueInputBuffer(encoderInputIndex, 0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                        }
                    }
                    decoder.releaseOutputBuffer(decoderOutputIndex, false)

                    if (totalDuration > 0 && bufferInfo.presentationTimeUs > 0) {
                        val progress = ((bufferInfo.presentationTimeUs * 100) / totalDuration).toInt().coerceIn(0, 80)
                        onProgress(progress)
                    }
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    val encoderInputIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (encoderInputIndex >= 0) {
                        encoder.queueInputBuffer(encoderInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }

                val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (encoderOutputIndex >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(encoderOutputIndex) ?: continue

                    if (!muxerStarted) {
                        val encoderOutputFormat = encoder.getOutputFormat(encoderOutputIndex)
                        muxerTrackIndex = muxer.addTrack(encoderOutputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedBuffer.position(bufferInfo.offset)
                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndex, encodedBuffer, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(encoderOutputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun encodeFileToBase64(file: File): String {
        val base64File = File(context.cacheDir, "base64_${System.currentTimeMillis()}.txt")
        try {
            FileOutputStream(base64File).use { fos ->
                fos.write("data:audio/mp3;base64,".toByteArray())
                Base64OutputStream(fos, android.util.Base64.NO_WRAP).use { b64os ->
                    FileInputStream(file).use { fis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            b64os.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            return base64File.readText()
        } finally {
            base64File.delete()
        }
    }

    private fun getAudioDuration(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.trackCount > 0) {
                val format = extractor.getTrackFormat(0)
                format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L) / 1000
            } else 0L
        } catch (_: Exception) {
            0L
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int {
        return try { getInteger(key) } catch (_: Exception) { default }
    }

    private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long {
        return try { getLong(key) } catch (_: Exception) { default }
    }
}