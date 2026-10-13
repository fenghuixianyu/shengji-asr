package com.shengji.asr

/**
 * 内置模型清单。
 *
 * assets 目录名必须与 sherpa-onnx AAR 内 getOfflineModelConfig(type) 的硬编码
 * 路径完全一致, 否则 native 侧读不到模型。
 *
 * 分两个发行版:
 *  - std : 只打包标准档 (SenseVoice 中文增强), 体积小。
 *  - full: 额外打包高精度档 (FunASR-Nano 大模型), 体积大但可切换。
 * 由 BuildConfig.FULL_BUILD 决定档位列表。
 */
object AsrModels {

    /** 标准档: SenseVoice 中英日韩粤 2024-07-17 (idx = 15), 官方 iic 版, 带标点。 */
    const val SENSE_VOICE_2024 = 15

    /** 高精度档: FunASR-Nano 大语言模型语音识别 (idx = 46), 中文更准但明显更慢。 */
    const val FUNASR_NANO = 46

    data class ModelSpec(
        val id: String,
        val displayName: String,
        val tagline: String,
        val recognizerType: Int,
        val description: String,
        /** 相对标准档的速度倍率提示, 仅用于文案。 */
        val speedHint: String,
    )

    /** 默认档: 性价比最高, 速度最快, 中文质量已很强。 */
    val senseVoice2024 = ModelSpec(
        id = "sensevoice2024",
        displayName = "标准",
        tagline = "SenseVoice 中文增强",
        recognizerType = SENSE_VOICE_2024,
        description = "中 / 英 / 日 / 韩 / 粤, 自带标点与数字规整, 速度快, 性价比最高。",
        speedHint = "速度最快",
    )

    /** 高精度档: LLM 解码, 中文识别更准, 但耗时约 4 倍。仅 full 版内置。 */
    val funAsrNano = ModelSpec(
        id = "funasrnano",
        displayName = "高精度",
        tagline = "FunASR-Nano 大模型",
        recognizerType = FUNASR_NANO,
        description = "大语言模型语音识别, 中文准确率更高, 代价是速度更慢、占用更大。",
        speedHint = "更准但约 4 倍慢",
    )

    /** 当前发行版实际内置的档位 (std 只有标准档, full 两档都有)。 */
    val bundled: List<ModelSpec> =
        if (BuildConfig.FULL_BUILD) listOf(senseVoice2024, funAsrNano) else listOf(senseVoice2024)

    /**
     * 识别语言模式。
     *
     * sherpa-onnx 的 SenseVoice / FunASR-Nano 都支持在构造识别器时指定语种。
     * languageCode 会透传给 native 侧:
     *  - "zh" : 强制中文, 短句不会乱判语种, 中文最稳
     *  - "en" : 强制英文, 英文最稳, 中文会退化
     *  - ""   : 自动判别 (auto), 中 / 英 / 日 / 韩 / 粤 全支持
     */
    data class LanguageMode(
        val id: String,
        val displayName: String,
        val tagline: String,
        val languageCode: String,
        val description: String,
    )

    /** 默认模式: 中文优先 (本 App 主要面向中文场景)。 */
    val langChinese = LanguageMode(
        id = "zh",
        displayName = "中文优先",
        tagline = "中文优先",
        languageCode = "zh",
        description = "强制中文解码: 短句与口音不会乱判语种, 也不会把中文吐成拼音。中英混说时英文单词仍能正常输出。",
    )

    /** 英文优先: 适合整段英文素材。 */
    val langEnglish = LanguageMode(
        id = "en",
        displayName = "英文优先",
        tagline = "英文优先",
        languageCode = "en",
        description = "强制英文解码: 英文最稳。此时中文会明显退化, 只建议纯英文素材使用。",
    )

    /** 多语种自动: 恢复模型原生 auto 判别, 覆盖日 / 韩 / 粤等。 */
    val langAuto = LanguageMode(
        id = "auto",
        displayName = "多语种自动",
        tagline = "多语种自动",
        languageCode = "",
        description = "模型自动判别语种, 支持中 / 英 / 日 / 韩 / 粤。语种混杂或非中文素材选它; 短句偶有误判, 可能出拼音。",
    )

    /** 全部语言模式, 列表顺序即界面顺序。 */
    val languages: List<LanguageMode> = listOf(langChinese, langEnglish, langAuto)

    /** 默认语言模式 = 中文优先。 */
    val defaultLanguage: LanguageMode = langChinese

    /** 应用标题 (与启动器标签一致, 按发行版区分)。 */
    val appTitle: String = if (BuildConfig.FULL_BUILD) "声迹 Pro" else "声迹"

    /** 默认档位 = 性价比最高的标准档。 */
    val default: ModelSpec = senseVoice2024
}