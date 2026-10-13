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
 *
 * 识别器带两个可切换维度:
 *  - modelType    : 精度档位 (标准 SenseVoice / 高精度 FunASR-Nano)
 *  - languageCode : 识别语言模式 ("zh" / "en" / "" 自动)
 * 两者都是构造期写进 native config 的, 任一变化都需要重建识别器。
 */
class AsrEngine(private val assets: AssetManager) {

    companion object {
        const val TAG = "ShengjiAsr"
        const val SAMPLE_RATE = 16000
        const val VAD_WINDOW = 512

        /** 空串 = 让模型自动判别语种 (sherpa-onnx 的 auto)。 */
        const val LANGUAGE_AUTO = ""
    }

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var vad: Vad? = null

    @Volatile
    private var loadedModelType: Int = -1

    @Volatile
    private var loadedLanguage: String = LANGUAGE_AUTO

    val isReady: Boolean get() = recognizer != null

    val currentModelType: Int get() = loadedModelType

    val currentLanguage: String get() = loadedLanguage

    /**
     * 加载识别模型 (幂等; 模型或语言模式变化时会释放旧实例)。
     *
     * @param modelType    AsrModels 里的档位常量
     * @param languageCode "zh" / "en" / ""(自动), 见 AsrModels.languages
     */
    @Synchronized
    fun load(modelType: Int, languageCode: String = LANGUAGE_AUTO): OfflineRecognizer {
        recognizer?.let {
            if (loadedModelType == modelType && loadedLanguage == languageCode) return it
        }
        release()
        Log.i(TAG, "load offline model type=$modelType language='$languageCode'")
        val modelConfig = requireNotNull(getOfflineModelConfig(type = modelType)) {
            "unknown model type $modelType"
        }
        tuneForLanguage(modelType, languageCode, modelConfig)
        val config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = modelConfig,
        )
        val r = OfflineRecognizer(assetManager = assets, config = config)
        recognizer = r
        loadedModelType = modelType
        loadedLanguage = languageCode
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
     * 按语言模式调优 native 配置。
     *
     * 背景: sherpa-onnx 内置 getOfflineModelConfig(type) 给 SenseVoice / FunASR-Nano
     * 传的都是 language 缺省值, 且 itn=false。缺省即「自动判别语种」, 在短句或
     * 口音重时会误判成日文 / 其他语种, 于是把中文吐成拼音或乱码 ——
     * 实测「你好」短片段会输出 "Lihao." / "い"。
     *
     * 因此这里显式指定语言, 并统一打开 ITN (自动补标点、数字与英文大小写规整):
     *
     *  - zh  : 强制中文。短句稳定输出中文, 不再乱判语种, 不再出拼音。
     *          中英混说时英文单词仍能正常输出, 只是偶有英文被「拉回」中文的倾向。
     *  - en  : 强制英文。英文最稳; 此时中文会明显退化, 只适合纯英文素材。
     *  - auto: 恢复模型原生自动判别, 中 / 英 / 日 / 韩 / 粤 全支持。
     *
     * SenseVoice 的 language 字段直接吃 "zh"/"en"/"ja"/"ko"/"yue", 空串即 auto。
     * FunASR-Nano 的 language 字段是自由文本, 会被拼进 prompt
     * (「语音转写成<language>：」), 所以这里映射成中文词而不是语言码。
     */
    private fun tuneForLanguage(
        modelType: Int,
        languageCode: String,
        modelConfig: OfflineModelConfig,
    ) {
        when (modelType) {
            AsrModels.SENSE_VOICE_2024 -> modelConfig.senseVoice?.let { sv ->
                sv.language = languageCode
                sv.useInverseTextNormalization = true
                Log.i(TAG, "tune sense-voice: language='$languageCode', itn=true")
            }

            AsrModels.FUNASR_NANO -> modelConfig.funasrNano?.let { fn ->
                fn.language = funAsrNanoLanguage(languageCode)
                fn.itn = true
                Log.i(TAG, "tune funasr-nano: language='${fn.language}', itn=true")
            }
        }
    }

    /** FunASR-Nano 的 language 是 prompt 文本, 不是语言码。 */
    private fun funAsrNanoLanguage(languageCode: String): String = when (languageCode) {
        "zh" -> "中文"
        "en" -> "英文"
        else -> "" // 空串 = 不带语言约束, 即原生「语音转写：」
    }

    @Synchronized
    fun release() {
        recognizer?.release()
        recognizer = null
        loadedModelType = -1
        loadedLanguage = LANGUAGE_AUTO
    }

    @Synchronized
    fun releaseAll() {
        release()
        vad?.release()
        vad = null
    }
}
