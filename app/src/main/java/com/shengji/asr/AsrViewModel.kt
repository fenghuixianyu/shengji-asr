package com.shengji.asr

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

data class AsrUiState(
    val loading: Boolean = true,
    val switching: Boolean = false,
    val modelId: String = AsrModels.default.id,
    val modelName: String = AsrModels.default.tagline,
    val modelSpeed: String = AsrModels.default.speedHint,
    val utterances: List<Utterance> = emptyList(),
    val progress: Float = 0f,
    val busy: Boolean = false,
    val recording: Boolean = false,
    val status: String = "",
    val sourceName: String = "",
    val error: String? = null,
) {
    val hasResult: Boolean get() = utterances.isNotEmpty()
    val fullText: String get() = TranscriptExporter.toPlainText(utterances)
}

class AsrViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = AsrEngine(app.assets)

    private val _state = MutableStateFlow(AsrUiState())
    val state: StateFlow<AsrUiState> = _state.asStateFlow()

    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private val recordQueue = LinkedBlockingQueue<FloatArray>()
    private val recordRunning = AtomicBoolean(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.load(AsrModels.default.recognizerType)
                engine.loadVad()
                _state.update { it.copy(loading = false, status = "模型已就绪") }
            } catch (t: Throwable) {
                Log.e(AsrEngine.TAG, "model init failed", t)
                _state.update {
                    it.copy(loading = false, error = "模型加载失败: ${t.message}")
                }
            }
        }
    }

    // ------------------------------------------------------------ 精度档位

    /** 切换识别精度档位 (换模型, 更准更慢)。 */
    fun selectModel(spec: AsrModels.ModelSpec) {
        if (_state.value.modelId == spec.id && engine.currentModelType == spec.recognizerType) {
            return
        }
        if (_state.value.busy || _state.value.recording) {
            _state.update { it.copy(error = "请先停止当前任务再切换精度") }
            return
        }
        _state.update {
            it.copy(
                switching = true, error = null,
                modelId = spec.id, modelName = spec.tagline, modelSpeed = spec.speedHint,
                status = "正在切换模型…",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.load(spec.recognizerType)
                _state.update {
                    it.copy(switching = false, status = "已切换到 ${spec.tagline}")
                }
            } catch (t: Throwable) {
                Log.e(AsrEngine.TAG, "switch model failed", t)
                _state.update {
                    it.copy(switching = false, error = "切换失败: ${t.message}")
                }
            }
        }
    }

    // ------------------------------------------------------------ 实时录音

    fun toggleRecording() {
        if (_state.value.recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (!engine.isReady) {
            _state.update { it.copy(error = "模型尚未就绪, 请稍候") }
            return
        }
        val minBuf = AudioRecord.getMinBufferSize(
            AsrEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufBytes = if (minBuf > 0) minBuf * 4 else AsrEngine.SAMPLE_RATE
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AsrEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufBytes,
            )
        } catch (t: Throwable) {
            _state.update { it.copy(error = "无法打开麦克风: ${t.message}") }
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            _state.update { it.copy(error = "麦克风初始化失败") }
            return
        }

        audioRecord = record
        recordQueue.clear()
        recordRunning.set(true)
        cancelled.set(false)

        _state.update {
            it.copy(
                recording = true, busy = true, error = null, sourceName = "实时录音",
                utterances = emptyList(), progress = 0f, status = "正在聆听…",
            )
        }

        val segmenter = SpeechSegmenter(engine.loadVad())
        segmenter.reset()

        recordThread = Thread({
            val buffer = ShortArray(AsrEngine.VAD_WINDOW)
            val floats = FloatArray(AsrEngine.VAD_WINDOW)
            try {
                record.startRecording()
                while (recordRunning.get()) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n > 0) {
                        for (i in 0 until n) floats[i] = buffer[i] / 32768.0f
                        val chunk = if (n == floats.size) floats.copyOf() else floats.copyOf(n)
                        segmenter.feed(chunk) { samples, _ -> recordQueue.put(samples) }
                    }
                }
                segmenter.finish { samples, _ -> recordQueue.put(samples) }
            } catch (t: Throwable) {
                Log.e(AsrEngine.TAG, "record loop error", t)
            } finally {
                recordRunning.set(false)
                try {
                    record.stop()
                } catch (_: Throwable) {
                }
                try {
                    record.release()
                } catch (_: Throwable) {
                }
                audioRecord = null
            }
        }, "asr-record").also { it.start() }

        job = viewModelScope.launch(Dispatchers.Default) {
            while (recordRunning.get() || recordQueue.isNotEmpty()) {
                val seg = recordQueue.poll()
                if (seg == null) {
                    delay(50)
                    continue
                }
                val text = engine.transcribe(seg)
                if (text.isNotBlank()) appendUtterance(text, 0L)
            }
            _state.update { it.copy(busy = false, recording = false, status = "已停止") }
        }
    }

    private fun stopRecording() {
        if (!recordRunning.get()) {
            _state.update { it.copy(recording = false, busy = false) }
            return
        }
        recordRunning.set(false)
        _state.update { it.copy(recording = false, status = "正在收尾…") }
        val t = recordThread
        recordThread = null
        viewModelScope.launch(Dispatchers.IO) { t?.join(3000) }
    }

    // ------------------------------------------------------------ 导入文件

    fun transcribeFile(uri: Uri, displayName: String) {
        if (_state.value.busy || _state.value.recording) {
            _state.update { it.copy(error = "请先停止当前任务") }
            return
        }
        if (!engine.isReady) {
            _state.update { it.copy(error = "模型尚未就绪, 请稍候") }
            return
        }
        cancelled.set(false)
        _state.update {
            it.copy(
                busy = true, error = null, sourceName = displayName,
                utterances = emptyList(), progress = 0f, status = "正在解码音频…",
            )
        }
        job = viewModelScope.launch(Dispatchers.IO) {
            val segmenter = SpeechSegmenter(engine.loadVad())
            segmenter.reset()
            try {
                val app = getApplication<Application>()
                AudioDecoder.decodeTo16kMono(
                    context = app,
                    uri = uri,
                    onChunk = { chunk ->
                        segmenter.feed(chunk) { samples, startSample ->
                            val text = engine.transcribe(samples)
                            if (text.isNotBlank()) {
                                val startMs = startSample * 1000L / AsrEngine.SAMPLE_RATE
                                val endMs =
                                    (startSample + samples.size) * 1000L / AsrEngine.SAMPLE_RATE
                                appendUtterance(text, startMs, endMs)
                            }
                        }
                    },
                    onProgress = { p ->
                        _state.update { s ->
                            s.copy(
                                progress = p,
                                status = "解码中 ${(p * 100).toInt()}% · 已识别 ${s.utterances.size} 段",
                            )
                        }
                    },
                    isCancelled = { cancelled.get() },
                )
                segmenter.finish { samples, startSample ->
                    val text = engine.transcribe(samples)
                    if (text.isNotBlank()) {
                        val startMs = startSample * 1000L / AsrEngine.SAMPLE_RATE
                        val endMs = (startSample + samples.size) * 1000L / AsrEngine.SAMPLE_RATE
                        appendUtterance(text, startMs, endMs)
                    }
                }
                _state.update { s ->
                    s.copy(
                        busy = false, progress = 1f,
                        status = if (cancelled.get()) "已取消"
                        else "识别完成 · 共 ${s.utterances.size} 段",
                    )
                }
            } catch (t: Throwable) {
                Log.e(AsrEngine.TAG, "file transcribe failed", t)
                _state.update {
                    it.copy(
                        busy = false, status = "",
                        error = t.message ?: t.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun cancel() {
        cancelled.set(true)
        if (recordRunning.get()) stopRecording()
        job?.cancel()
        job = null
        _state.update { it.copy(busy = false, recording = false, status = "已取消") }
    }

    fun clearResult() {
        _state.update {
            it.copy(
                utterances = emptyList(), progress = 0f, status = "",
                error = null, sourceName = "",
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun appendUtterance(text: String, startMs: Long, endMs: Long = startMs) {
        _state.update { s ->
            val safeEnd = if (endMs > startMs) endMs else startMs + 1000
            s.copy(utterances = s.utterances + Utterance(startMs, safeEnd, text.trim()))
        }
    }

    override fun onCleared() {
        cancelled.set(true)
        recordRunning.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        engine.releaseAll()
        super.onCleared()
    }
}