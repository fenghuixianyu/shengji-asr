# 声迹 · 音视频转文字（Shengji ASR）

一个**完全离线、不联网**的 Android 音视频转文字 App，基于
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 的离线中文语音识别模型。
应用名 **声迹**（标准版）/ **声迹 Pro**（双精度档）。

在官方示例 APK 的基础上做了这些改造：

1. **新增「导入音频 / 视频自动转文字」** —— 选一个本地文件，自动抽音轨、重采样、
   切分、识别、出文字，可导出纯文本 / 带时间戳 / SRT 字幕。
2. **界面布局重做** —— 顶部标题 + 精度档位区 + 双 Tab（导入文件 / 实时录音）+ 结果面板。
3. **新增「识别精度」档位切换** —— 默认档性价比最高；可切到更准但更慢的高精度档。
4. **产品化** —— 正式命名「声迹」，重做自适应图标（渐变卡片 + 声波字形）。
5. **中文识别增强** —— 强制 `language=zh` 并开启 ITN，修掉中文短句被吐成拼音 / 日文的问题
   （详见 [`docs/变更记录-中文识别增强.md`](docs/变更记录-中文识别增强.md)）。
6. **应用内语言模式切换** —— 中文优先（默认）/ 英文优先 / 多语种自动三档可切，
   兼顾「中文最稳」与「英文 / 日韩粤」场景
   （详见 [`docs/变更记录-语言模式切换.md`](docs/变更记录-语言模式切换.md)）。
7. **语言 / 精度收进设置页** —— 主界面保持原样，右上角齿轮进入「设置」页切换识别语言与精度档位
   （详见 [`docs/变更记录-设置页.md`](docs/变更记录-设置页.md)）。

---

## 功能特性

| 功能 | 说明 |
|---|---|
| 导入音频转文字 | 支持 `mp3 / wav / m4a / aac / flac / ogg / opus / amr / wma` 等常见音频 |
| 导入视频转文字 | 支持 `mp4 / mkv / mov / avi / webm / 3gp` 等常见视频，自动抽音轨 |
| 实时录音转文字 | 边说边出字，VAD 自动断句 |
| 自动断句 | 内置 Silero VAD，长音频自动切成语音段再识别，结果带时间轴 |
| 导出 | 复制到剪贴板、导出纯文本 TXT、导出带时间戳 TXT、导出 SRT 字幕、清空 |
| 精度档位 | 标准档（默认，性价比最高）/ 高精度档（更准但更慢） |
| 语言模式 | 中文优先（默认）/ 英文优先 / 多语种自动，应用内随时切换 |
| 设置页 | 右上角齿轮进入「设置」，内含「识别语言」与「识别精度」两个选择器；主界面保持原样 |
| 全离线 | 模型全部内置在 APK 里，运行期不联网、不上传任何数据 |

**支持语言**：中文（重点优化）、英文、日文、韩文、粤语。

> **语言模式**：识别器带三档可切的语言模式，默认**中文优先**（`language=zh` + ITN）。
>
> | 模式 | 底层 `language` | 特点 | 适用 |
> |---|---|---|---|
> | 中文优先（默认） | `zh` | 中文最稳，短句不出拼音；英文偶被拉成中文 | 绝大多数中文素材、中英混说 |
> | 英文优先 | `en` | 英文最稳；中文明显退化 | 纯英文素材 |
> | 多语种自动 | `""`（auto） | 中 / 英 / 日 / 韩 / 粤 自动判别；短句偶有误判 | 语种混杂、非中文素材 |
>
> 语种在**构造识别器时**写进 native 配置，所以切换语言模式会重建识别器
> （标准档瞬间完成；高精度档要重新加载大模型，会明显卡一下）。
> 改动记录与还原方法见 [`docs/变更记录-语言模式切换.md`](docs/变更记录-语言模式切换.md)。
>
> **入口**：识别语言与识别精度都在**设置页**里切换 —— 点主界面右上角齿轮进入；
> 主界面本身保持原样。改动记录见 [`docs/变更记录-设置页.md`](docs/变更记录-设置页.md)。

### 导出格式

结果面板提供 5 个操作按钮（从左到右）：

| 按钮 | 输出 |
|---|---|
| 复制 | 复制到系统剪贴板（纯文本） |
| 导出纯文本 TXT | 每行一段文本，无时间信息 |
| 导出带时间戳 TXT | 每行 `[起始时间 - 结束时间] 文本`，兼顾可读性与起止时间 |
| 导出 SRT 字幕 | 标准 SRT 字幕（含起止时间），可直接挂到播放器 |
| 清空 | 清空当前结果 |

带时间戳 TXT 示例：

```
[00:00:00 - 00:00:02] 这是一个视频文件的语音测试。
[00:00:03 - 00:00:06] 请把视频里的声音自动转成文字。
[00:00:07 - 00:00:10] 今天我们来测试离线语音识别功能。
[00:00:11 - 00:00:14] 识别准确率应该保持很高。
```

---

## 下载与安装

### 直接装 APK（推荐给普通用户）

| 版本 | 体积 | 下载 | SHA256 |
|---|---|---|---|
| 声迹（标准版） | 330.6 MB | [**Releases 页**](https://github.com/fenghuixianyu/shengji-asr/releases) | `101D7508...6F41B1A7` |
| 声迹 Pro（双精度档） | 1282.7 MB | 暂不提供（可自行构建） | `7A88CD54...D0AC3387` |

> SHA256 完整值（2026-10-14 应用内语言模式切换后重新构建）：
> - `声迹-标准版.apk`（346,720,604 B）：`101D7508E5DF4B7A204E3D15972772CB2156EF8BA12CF6D632EA769D6F41B1A7`
> - `声迹-双精度档.apk`（1,345,051,035 B）：`7A88CD545C83DCCEF9E7C5073CEDBB1FB90D02DC585BBCAAB925BD33D0AC3387`

> **标准版（330 MB）**放在本仓库的 [Releases 页](https://github.com/fenghuixianyu/shengji-asr/releases)（资产名 `shengji-v1.0.0.apk`），点进去直接下即可。
>
> **双精度档版（1.28 GB）**体积过大，**本仓库暂不提供预编译包**：GitHub Release 单文件上限 2 GB，
> 上传/下载体验都很差。需要的话按「从源码构建」自行打包（构建前跑
> `python tools/download_models.py` 拉取全部模型）。
>
> - 文件名：`声迹-双精度档.apk`（1,345,051,035 字节）
> - SHA256：`7A88CD545C83DCCEF9E7C5073CEDBB1FB90D02DC585BBCAAB925BD33D0AC3387`

安装：两个包名不同（`com.shengji.asr` / `com.shengji.asr.full`），可同时安装。

### 从源码构建（开发者）

源码仓库**不含**模型与原生库（GB 级）。克隆后先跑下载脚本，再构建：

```bash
git clone https://github.com/fenghuixianyu/shengji-asr.git ShengjiAsr && cd ShengjiAsr
python tools/download_models.py          # 下载全部大文件到正确位置
# 只想要标准版：python tools/download_models.py --std
```

脚本默认走 `hf-mirror.com`（国内快），失败自动回退 `huggingface.co`。
校验已下载文件是否齐全：`python tools/download_models.py --check`。

---

## 两个发行版本

工程用 Gradle product flavor 拆成两个版本，模型不重复打包：

| 版本 | APK 体积 | 内置档位 | 说明 |
|---|---|---|---|
| **标准版** `std` | **约 330 MB** | 仅标准档 | 只内置 SenseVoice，体积小，日常够用 |
| **双精度档** `full` | **约 1.28 GB** | 标准档 + 高精度档 | 额外内置 FunASR-Nano 大模型，可在 App 内切换 |

两个版本包名不同（`com.shengji.asr` / `com.shengji.asr.full`），可以**同时安装**在一台手机上，
方便对比或按需使用。标准版界面里不显示档位选择（只有一档），双精度档版显示两个档位 chips。

### 怎么选

- 只想快速、稳定地把中文音视频转成文字，手机存储不宽裕 → **标准版**。
- 经常处理人名 / 古文 / 专业术语等难场景，愿意用时间和体积换准确率 → **双精度档版**。

---

## 精度档位与模型对比

两个档位是**换模型**实现的（不是同一模型的量化精度差异，见下文），不是简单的解码参数切换。

| 档位 | 模型 | sherpa-onnx type | 体积 | 速度 | 特点 |
|---|---|---|---|---|---|
| 标准（默认） | SenseVoice 2024-07-17（iic 官方版, int8） | 15 | ~239 MB | 最快 | 中/英/日/韩/粤，**自带标点与数字规整** |
| 高精度 | FunASR-Nano（LLM 语音识别, int8） | 46 | ~993 MB | 约 4 倍慢 | 中文更准，尤其人名 / 古文 / 专有名词 |

### 实测数据（16 段官方中文测试音频）

- **耗时**：标准档合计 **11.37 s**，高精度档合计 **48.17 s** —— 约 **4.2 倍**。
- **准确率**：高精度档在 **8/16** 段更准，**1~2** 段反而更差，其余基本打平。

高精度档修正的典型例子：

| 音频 | 标准档 | 高精度档 | 谁对 |
|---|---|---|---|
| 8.wav | 起路友无限的感慨…侵炸污浊 | 唤起陆游无限的感慨…倾轧污浊 | 高精度 |
| 9.wav | 杜蝶为证 | 度牒为证 | 高精度 |
| 16.wav | 紧张的时代 | 增长的时代 | 高精度 |
| 7.wav | 脸上绽现出 | 脸上展现出 | 高精度 |
| 14.wav | 刘家浪回车里住 | 有家难回车里住 | 高精度 |

高精度档翻车的例子：

| 音频 | 标准档 | 高精度档 | 谁对 |
|---|---|---|---|
| 1.wav | 放就放嘛 | 放狗放马 | 标准 |
| 4.wav | 藏不起来 | 唱不起来 | 标准 |

**结论：日常用标准档就够了。**

- 标准档已经很强。普通对话、口播、会议、视频字幕这类场景，标准档与高精度档的
  差距基本可以忽略；
- 高精度档是「锦上添花」，只在**人名、古文、专业术语、易混淆同音词**上更准；
- 代价是约 **4 倍耗时** + 约 **3 倍体积**，换来的收益有限，**不是刚需**。

所以默认给标准档；只有遇到难场景（大量人名地名、文言文、专业名词）再临时切高精度档。
这也正是「标准版只内置标准档」的意义 —— 对绝大多数人，标准版就是最优解。

### 为什么不按「量化精度（int8 / fp32）」做档位

- SenseVoice / Paraformer 只支持 `greedy_search`，不能切解码方式。
- 同版本 int8 vs fp32 文本几乎 100% 一致（干净音频 / 低信噪比），耗时也几乎相同，
  但 fp32 会让 APK 从 328 MB 涨到 **1.2 GB 以上**，没有任何收益。
- 所以「精度档」采用**换模型**实现，语义更清晰：标准 = 快，高精度 = 更准更慢。

### 为什么默认档选 SenseVoice 2024-07-17 而不是 2025-09-09

- 官方 `...int8-2025-09-09` 的 ONNX 元数据是 `ASLP-lab / WSYue-ASR`，即**粤语版**，
  实测中文无标点、漏字、英文严重退化。
- `...int8-2024-07-17` 元数据是 `iic / SenseVoiceSmall`，是标准中文版、**带标点**，
  在官方测试集上明显优于粤语版及 zipformer-ctc-zh / telespeech-ctc-zh / paraformer-zh 等。

---

## 图标

- 自适应图标（adaptive icon）：满幅渐变背景层 + 声波字形前景层 + 单色层。
- 字形限制在 66dp 安全圆内，圆形 / 方形 / 圆角等各启动器裁切都不遮挡内容。
- 由源图（1254×1254 蓝色渐变卡片）抠出字形、拟合背景后按各密度生成，
  各密度 PNG 均已压缩，全部 mipmap 合计不到 150 KB。

---

## 构建

### 环境

- JDK 17
- Android SDK（compileSdk 36）
- Python 3（跑 `tools/download_models.py` 拉取大文件）

### 准备大文件

仓库不含模型 / AAR，先下载（会自动放到 `app/libs/` 与各 `assets/` 目录）：

```bash
python tools/download_models.py          # 全部；只标准版用 --std
```

### 命令

```bash
# 标准版
./gradlew assembleStdDebug     # 产物 app/build/outputs/apk/std/debug/app-std-debug.apk

# 双精度档
./gradlew assembleFullDebug     # 产物 app/build/outputs/apk/full/debug/app-full-debug.apk

# 两个都打
./gradlew assembleStdDebug assembleFullDebug
```

Windows 下先设 `JAVA_HOME`：

```powershell
$env:JAVA_HOME = "D:\Java\jdk-17.0.20+8"
$env:ANDROID_HOME = "D:\Android\Sdk"
.\gradlew.bat assembleStdDebug assembleFullDebug
```

---

## 目录结构

```
app/
├─ build.gradle.kts                  # flavor 定义：std / full
├─ libs/sherpa-onnx-1.13.8.aar       # sherpa-onnx 原生库 + Kotlin API
└─ src/
   ├─ main/                          # 两个版本共用的代码与「标准档」模型
   │  ├─ assets/
   │  │  ├─ silero_vad.onnx
   │  │  └─ sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/
   │  ├─ res/mipmap-*/                # 「声迹」自适应图标各密度切图
   │  └─ java/com/shengji/asr/
   │     ├─ MainActivity.kt
   │     ├─ AsrEngine.kt             # 识别器 / VAD 封装，全离线
   │     ├─ AsrModels.kt             # 模型清单，按 BuildConfig.FULL_BUILD 决定档位
   │     ├─ AsrViewModel.kt          # 状态机：导入文件 / 实时录音
   │     ├─ AudioDecoder.kt          # MediaExtractor + MediaCodec → 16kHz 单声道
   │     ├─ SpeechSegmenter.kt       # Silero VAD 切段
   │     ├─ Transcript.kt            # 结果模型 + TXT / 带时间戳 TXT / SRT 导出
   │     └─ ui/AsrApp.kt             # Compose 界面
   └─ full/                          # 只有完整版打包的「高精度档」模型
      └─ assets/sherpa-onnx-funasr-nano-int8-2025-12-30/

docs/                                # 变更记录与改造文档（反馈 / 根因 / 改动 / 还原）
├─ 改造总览与还原.md          # 相对官方示例 APK 的全部改造 + 如何还原
├─ 变更记录-中文识别增强.md
├─ 变更记录-TXT起止时间.md
└─ 变更记录-语言模式切换.md
```

---

## 模型来源与许可

| 资源 | 来源 | 许可 |
|---|---|---|
| sherpa-onnx（AAR / 运行时） | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 |
| SenseVoice 2024-07-17 | ModelScope `iic/SenseVoiceSmall` | 见上游模型页 |
| FunASR-Nano | ModelScope FunASR-Nano | 见上游模型页 |
| Silero VAD | [snakers4/silero-vad](https://github.com/snakers4/silero-vad) | MIT |

使用 / 分发时请遵守各上游模型的许可条款。

---

## 已验证

在 Android 模拟器上端到端验证（早期功能在 x86_64 / API 28；本轮语言模式在
Android 13 / API 33 模拟器 `emulator-5556`，1080x2340 @440dpi，4 GB 内存）：

- 标准版：模型加载 ✅ 导入 wav / mp4 识别（带标点，4 段全对）✅ 5 个导出按钮齐全 ✅
- 完整版：标准档加载 ✅ 高精度档加载 ✅ 双向切换 ✅ 导入 mp4 识别（4 段全对）✅
- 导出纯文本 TXT ✅ 导出带时间戳 TXT（`[起始 - 结束]` 格式）✅ 导出 SRT ✅ 实时录音面板 ✅
- 中文增强：日志 `tune sense-voice: language=zh, itn=true` ✅ 短句不再吐拼音 ✅
- 语言模式（API 33 模拟器实测）：
  - 标准档三档全部工作：`language='zh'` / `'en'` / `''` 均正确重建识别器 ✅
    中文优先 `开饭时间早上9点至下午5点。`、英文优先 `The tribal chieftain called for the boy.`、
    多语种自动粤语 `呢几个字都表达唔到，我想讲嘅意思。` ✅
  - 高精度档（FunASR-Nano）三档语言全部工作，且 `language` 正确映射为中文词
    （`'zh'→'中文'` / `'en'→'英文'` / `''→''`）✅
  - 高精度档端到端导入 `zh.wav` → 导出带时间戳 TXT
    `[00:00:00 - 00:00:05] 菜饭时间早上九点至下午五点。` ✅
- 应用名「声迹」/「声迹 Pro」✅ 自适应图标（圆形裁切不遮挡）✅
- 设置页（API 33 模拟器实测）：主界面恢复原样、右上角齿轮进入设置页 ✅
  设置页内语言 / 精度选择器齐全，切换后返回主界面副标题同步 ✅
  标准版 + 双精度档两个 flavor 的设置页均验证通过，切换后导入音频识别正常 ✅

---

## 注意

- 高精度档 FunASR-Nano 运行需要约 1 GB 内存，**建议真机 4 GB 以上**；
  2 GB 模拟器会因内存不足被系统杀掉，真机大内存无此问题。
- APK 体积大是因为**模型内置**（完整版内置两套模型），好处是离线、免下载、开箱即用。


---

## 大文件与模型下载

模型与原生库体积很大（合计约 2.5 GB），默认**不纳入 git**（见 `.gitignore`，
只忽略 `*.onnx` 与 `*.aar`，小体积文本资源仍入库）。克隆后运行：

```bash
python tools/download_models.py          # 全部
python tools/download_models.py --std   # 只标准档
python tools/download_models.py --check  # 只校验
```

脚本会把以下文件下载到对应位置（含期望字节数校验，默认 hf-mirror.com 镜像）：

| 文件 | 位置 | 大小 |
|---|---|---|
| `sherpa-onnx-1.13.8.aar` | `app/libs/` | 47.4 MB |
| `silero_vad.onnx` | `app/src/main/assets/` | 0.6 MB |
| `model.int8.onnx` + `tokens.txt` | `app/src/main/assets/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/` | 228.2 MB |
| `encoder_adaptor.int8.onnx` / `llm.int8.onnx` / `embedding.int8.onnx` | `app/src/full/assets/sherpa-onnx-funasr-nano-int8-2025-12-30/` | 947.7 MB |
| `Qwen3-0.6B/{tokenizer.json,merges.txt,vocab.json}` | 同上 | 15.1 MB |

assets 下的**目录名与文件名必须保持原样**，因为 sherpa-onnx 的
`getOfflineModelConfig(type)` 里对路径是硬编码的。

---

## 改造记录与还原

本项目相对**官方示例 APK** 的全部改造、涉及文件、以及**如何还原**（单项 / 整体），
见 [`docs/改造总览与还原.md`](docs/改造总览与还原.md)。

各次改动的完整记录（用户反馈 / 根因 / 实测对照 / 修改前后代码 / 还原步骤）：

- [`docs/变更记录-中文识别增强.md`](docs/变更记录-中文识别增强.md)
- [`docs/变更记录-TXT起止时间.md`](docs/变更记录-TXT起止时间.md)
- [`docs/变更记录-语言模式切换.md`](docs/变更记录-语言模式切换.md)
- [`docs/变更记录-设置页.md`](docs/变更记录-设置页.md)

### 版本记录

| 版本 | 变更 |
|---|---|
| v1.0.0 | 首次发布：导入音视频转文字、界面重做、精度档位、TXT / SRT 导出、命名与图标 |
| v1.0.0 (2026-10-13) | **中文识别增强**：强制 `language=zh` + ITN，修掉中文短句被吐成拼音 / 日文；重新构建并替换 Release 资产（SHA256 见「下载与安装」） |
| v1.0.0 (2026-10-14) | **带时间戳 TXT 修复**：由「仅起始时间」`[时:分:秒]` 改为「起止时间」`[起始 - 结束]`，与 SRT 的起止时间语义对齐；重新构建（SHA256 见「下载与安装」） |
| v1.0.0 (2026-10-14) | **应用内语言模式切换**：新增「中文优先（默认）/ 英文优先 / 多语种自动」三档，语言参数化透传 native；高精度档 FunASR-Nano 的 `language` 需映射为中文词；重新构建（SHA256 见「下载与安装」） |
| v1.0.0 (2026-10-14) | **设置页**：把「识别语言 / 识别精度」收进主界面右上角齿轮进入的设置页，主界面恢复原样；重新构建（SHA256 见「下载与安装」） |

---

## 许可

本项目代码以 [Apache-2.0](LICENSE) 发布。所用第三方模型 / 库的许可见上表，
分发时请一并遵守。
