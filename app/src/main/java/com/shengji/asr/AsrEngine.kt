package com.shengji.asr

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.k2fsa.sherpa.onnx.getVadModelConfig

/**
 * sherpa-onnx 离线识别引擎封装。
 *
 * 识别器与 VAD 都从 assets 直接加载, 全离线运行, 不联网。
 */
class AsrEngine(private val assets: AssetManager) {

    companion object {
        const val TAG = "ShengjiAsr"
        const val SAMPLE_RATE = 16000
        const val VAD_WINDOW = 512
    }

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var vad: Vad? = null

    @Volatile
    private var loadedModelType: Int = -1

    val isReady: Boolean get() = recognizer != null

    val currentModelType: Int get() = loadedModelType

    /** 加载识别模型 (幂等; 切换模型时会释放旧实例)。 */
    @Synchronized
    fun load(modelType: Int): OfflineRecognizer {
        recognizer?.let { if (loadedModelType == modelType) return it }
        release()
        Log.i(TAG, "load offline model type=$modelType")
        val modelConfig = requireNotNull(getOfflineModelConfig(type = modelType)) {
            "unknown model type $modelType"
        }
        tuneForChinese(modelType, modelConfig)
        val config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = modelConfig,
        )
        val r = OfflineRecognizer(assetManager = assets, config = config)
        recognizer = r
        loadedModelType = modelType
        Log.i(TAG, "offline model loaded")
        return r
    }

    /** 加载 VAD (幂等)。 */
    @Synchronized
    fun loadVad(): Vad {
        vad?.let { return it }
        Log.i(TAG, "load silero vad")
        val config = requireNotNull(getVadModelConfig(type = 0)) { "silero vad config missing" }
        val v = Vad(assetManager = assets, config = config)
        vad = v
        Log.i(TAG, "vad loaded")
        return v
    }

    /** 一次性识别一段音频 (16kHz 单声道 float 样本)。 */
    fun transcribe(samples: FloatArray): String {
        val r = recognizer ?: return ""
        val stream = r.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            r.decode(stream)
            r.getResult(stream).text
        } catch (t: Throwable) {
            Log.e(TAG, "transcribe failed", t)
            ""
        } finally {
            stream.release()
        }
    }

    /**
     * 中文优先调优: 覆写 sherpa-onnx 内置的「自动语言检测 + 关闭 ITN」默认值。
     *
     * 内置 getOfflineModelConfig(type) 对 SenseVoice / FunASR-Nano 都传 language=null
     * (自动判别语种) 且 itn=false。自动判别在短句或口音重时会误判成日文 / 其他语种,
     * 于是把中文吐成拼音或乱码 —— 实测「你好」短片段会输出 "Lihao." / "い"。
     *
     * 这里强制 language="zh" 并开启 ITN, 效果:
     *  - 中文短句稳定输出中文, 不再乱判语种, 不再出拼音;
     *  - 自动补标点, 数字与英文大小写规整 (ITN);
     *  - 对英文识别无副作用 —— 实测 zh 模式英文结果与 auto 完全一致, 只是多了标点。
     */
    private fun tuneForChinese(modelType: Int, modelConfig: OfflineModelConfig) {
        when (modelType) {
            AsrModels.SENSE_VOICE_2024 -> modelConfig.senseVoice?.let { sv ->
                sv.language = "zh"
                sv.useInverseTextNormalization = true
                Log.i(TAG, "tune sense-voice: language=zh, itn=true")
            }

            AsrModels.FUNASR_NANO -> modelConfig.funasrNano?.let { fn ->
                fn.language = "zh"
                fn.itn = true
                Log.i(TAG, "tune funasr-nano: language=zh, itn=true")
            }
        }
    }

    @Synchronized
    fun release() {
        recognizer?.release()
        recognizer = null
        loadedModelType = -1
    }

    @Synchronized
    fun releaseAll() {
        release()
        vad?.release()
        vad = null
    }
}