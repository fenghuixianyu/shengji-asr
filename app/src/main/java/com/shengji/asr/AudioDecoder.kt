package com.shengji.asr

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 线性插值重采样器 (有状态, 可跨 buffer 连续调用)。
 */
class LinearResampler(private val srcRate: Int, private val dstRate: Int) {

    private val step: Double = if (dstRate <= 0) 1.0 else srcRate.toDouble() / dstRate
    private var pos: Double = 0.0

    fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        if (srcRate <= 0 || srcRate == dstRate) return input
        val n = input.size
        if (n < 2) return FloatArray(0)

        val out = FloatArray((n / step).toInt() + 2)
        var count = 0
        var p = pos
        while (p < n - 1 && count < out.size) {
            val i = p.toInt()
            val f = (p - i).toFloat()
            val a = input[i]
            val b = input[i + 1]
            out[count++] = a + (b - a) * f
            p += step
        }
        pos = p - (n - 1)
        return if (count == out.size) out else out.copyOf(count)
    }
}

/**
 * 用 MediaExtractor + MediaCodec 把任意音视频文件解码成 16kHz 单声道 float 样本。
 *
 * 解码器输出可能是 16-bit PCM 也可能是 float PCM, 声道数也不固定, 这里统一
 * 下混成单声道, 再线性插值重采样到 16kHz (VAD 与识别器都要求 16kHz)。
 */
object AudioDecoder {

    private const val TAG = "AudioDecoder"
    private const val OUT_RATE = AsrEngine.SAMPLE_RATE
    private const val TIMEOUT_US = 10_000L

    class NoAudioTrackException : IllegalStateException("该文件里没有找到音频轨道")

    /**
     * 流式解码, 边解边通过 [onChunk] 抛出 16kHz 单声道样本, 避免整段音频驻留内存。
     *
     * @return 输出的 16kHz 样本总数
     */
    fun decodeTo16kMono(
        context: Context,
        uri: Uri,
        onChunk: (FloatArray) -> Unit,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) throw NoAudioTrackException()
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            var channels = intOf(format, MediaFormat.KEY_CHANNEL_COUNT, 1)
            var srcRate = intOf(format, MediaFormat.KEY_SAMPLE_RATE, OUT_RATE)
            var pcmEncoding =
                intOf(format, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            var resampler = LinearResampler(srcRate, OUT_RATE)

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var total = 0L
            var lastProgressAt = 0L
            var lastProgress = -1f

            while (!outputDone) {
                if (isCancelled()) break

                if (!inputDone) {
                    val inIndex = c.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = c.getInputBuffer(inIndex)
                        if (inBuf == null) {
                            c.queueInputBuffer(inIndex, 0, 0, 0, 0)
                        } else {
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                c.queueInputBuffer(
                                    inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                c.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = c.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = c.outputFormat
                        val newRate = intOf(of, MediaFormat.KEY_SAMPLE_RATE, srcRate)
                        val newCh = intOf(of, MediaFormat.KEY_CHANNEL_COUNT, channels)
                        val newEnc = intOf(of, MediaFormat.KEY_PCM_ENCODING, pcmEncoding)
                        if (newRate != srcRate || newCh != channels || newEnc != pcmEncoding) {
                            Log.i(TAG, "output format rate=$newRate ch=$newCh enc=$newEnc")
                            srcRate = newRate
                            channels = newCh
                            pcmEncoding = newEnc
                            resampler = LinearResampler(srcRate, OUT_RATE)
                        }
                    }

                    outIndex >= 0 -> {
                        var mono: FloatArray? = null
                        if (info.size > 0) {
                            val outBuf = c.getOutputBuffer(outIndex)
                            if (outBuf != null) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                mono = toMonoFloat(outBuf, channels, pcmEncoding)
                            }
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val ptsUs = info.presentationTimeUs
                        c.releaseOutputBuffer(outIndex, false)

                        if (mono != null && mono.isNotEmpty()) {
                            val chunk = resampler.process(mono)
                            if (chunk.isNotEmpty()) {
                                total += chunk.size
                                onChunk(chunk)
                            }
                        }

                        if (durationUs > 0) {
                            val p = (ptsUs.toFloat() / durationUs).coerceIn(0f, 1f)
                            val now = System.currentTimeMillis()
                            if (p - lastProgress > 0.01f || now - lastProgressAt > 250) {
                                lastProgress = p
                                lastProgressAt = now
                                onProgress(p)
                            }
                        }
                        if (eos) outputDone = true
                    }
                }
            }

            onProgress(1f)
            return total
        } finally {
            try {
                codec?.stop()
            } catch (_: Throwable) {
            }
            try {
                codec?.release()
            } catch (_: Throwable) {
            }
            try {
                extractor.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun intOf(format: MediaFormat, key: String, fallback: Int): Int =
        if (format.containsKey(key)) {
            try {
                format.getInteger(key)
            } catch (_: Throwable) {
                fallback
            }
        } else fallback

    private fun toMonoFloat(buf: ByteBuffer, channels: Int, pcmEncoding: Int): FloatArray {
        val ch = if (channels < 1) 1 else channels
        buf.order(ByteOrder.nativeOrder())
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val fb = buf.asFloatBuffer()
            val frames = fb.remaining() / ch
            if (frames <= 0) return FloatArray(0)
            val out = FloatArray(frames)
            for (i in 0 until frames) {
                if (ch == 1) {
                    out[i] = fb.get()
                } else {
                    var s = 0f
                    for (c2 in 0 until ch) s += fb.get()
                    out[i] = s / ch
                }
            }
            return out
        }
        val sb = buf.asShortBuffer()
        val frames = sb.remaining() / ch
        if (frames <= 0) return FloatArray(0)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            if (ch == 1) {
                out[i] = sb.get() / 32768.0f
            } else {
                var s = 0f
                for (c2 in 0 until ch) s += sb.get()
                out[i] = s / ch / 32768.0f
            }
        }
        return out
    }
}