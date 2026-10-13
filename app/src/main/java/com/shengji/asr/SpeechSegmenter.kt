package com.shengji.asr

import com.k2fsa.sherpa.onnx.Vad

/**
 * 语音活动检测 (VAD) 分段器。
 *
 * sherpa-onnx 的 Vad 要求每次喂入长度正好等于 windowSize (默认 512), 且内部
 * 环形缓冲区溢出时会直接结束进程, 因此这里统一做定长切分后再喂。
 *
 * @param onSegment 回调参数为 (该段 16kHz 单声道样本, 该段在整条流中的起始样本下标)
 */
class SpeechSegmenter(
    private val vad: Vad,
    private val windowSize: Int = AsrEngine.VAD_WINDOW,
) {
    private val pending = FloatArray(windowSize)
    private var pendingSize = 0

    fun feed(samples: FloatArray, onSegment: (FloatArray, Int) -> Unit) {
        var offset = 0
        while (offset < samples.size) {
            val take = minOf(windowSize - pendingSize, samples.size - offset)
            System.arraycopy(samples, offset, pending, pendingSize, take)
            pendingSize += take
            offset += take
            if (pendingSize == windowSize) {
                vad.acceptWaveform(pending)
                pendingSize = 0
                drain(onSegment)
            }
        }
    }

    /** 输入结束后调用, 把最后不足一窗的样本与尾音一起冲出来。 */
    fun finish(onSegment: (FloatArray, Int) -> Unit) {
        if (pendingSize > 0) {
            val window = FloatArray(windowSize)
            System.arraycopy(pending, 0, window, 0, pendingSize)
            pendingSize = 0
            vad.acceptWaveform(window)
            drain(onSegment)
        }
        vad.flush()
        drain(onSegment)
    }

    fun reset() {
        pendingSize = 0
        vad.reset()
    }

    private fun drain(onSegment: (FloatArray, Int) -> Unit) {
        while (!vad.empty()) {
            val seg = vad.front()
            vad.pop()
            if (seg.samples.isNotEmpty()) onSegment(seg.samples, seg.start)
        }
    }
}