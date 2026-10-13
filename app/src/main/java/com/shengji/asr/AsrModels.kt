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

    /** 应用标题 (与启动器标签一致, 按发行版区分)。 */
    val appTitle: String = if (BuildConfig.FULL_BUILD) "声迹 Pro" else "声迹"

    /** 默认档位 = 性价比最高的标准档。 */
    val default: ModelSpec = senseVoice2024
}