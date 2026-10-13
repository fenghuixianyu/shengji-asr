# 变更记录 · 带时间戳 TXT 改为「起止时间」

> 本文记录一次**用户反馈 → 定位 → 代码修改 → 验证 → 还原**的完整过程，
> 便于复现，也便于日后回退到改动前的行为。

---

## 一、用户反馈

用户导出「带时间戳 TXT」后发现：**只有起始时间，没有结束时间**；
而 SRT 字幕是带「起止时间」的。

> 原话：原来的字幕文件是有持续时间的，但是 TXT 怎么只有起始时间？
> 我觉得就把原来的字幕文件格式改成 TXT 行了。

诉求：**带时间戳 TXT 也输出起止时间**（等价于「把 SRT 的时间信息换成 TXT 的行式写法」）。

---

## 二、定位

- 导出逻辑集中在 `Transcript.kt` 的 `TranscriptExporter`：
  - `toPlainText`：纯文本，无时间。
  - `toTimestampedText`：带时间戳 TXT —— 原本每行 `"[${it.startLabel()}] ${it.text}"`，
    **只用了 `startLabel()`（起始时间）**。
  - `toSrt`：SRT，用 `formatSrtTime(startMs) --> formatSrtTime(endMs)`，本来就带起止。
- `Utterance` 里**已经有** `rangeLabel()`：`"${formatClock(startMs)} - ${formatClock(endMs)}"`，
  形如 `00:00:00 - 00:00:02`。也就是说**只差把 `startLabel()` 换成 `rangeLabel()`**。

根因：`toTimestampedText` 当初只调了 `startLabel()`，没带上 `endMs`。

---

## 三、实际修改

**唯一改动文件**：`app/src/main/java/com/shengji/asr/Transcript.kt`

```diff
     fun toTimestampedText(items: List<Utterance>): String =
-        items.joinToString("\n") { "[${it.startLabel()}] ${it.text}" }
+        items.joinToString("\n") { "[${it.rangeLabel()}] ${it.text}" }
```

- 未新增/删除任何方法，只改了一行调用；`rangeLabel()` 本就存在，无新增格式化逻辑。
- 未改动界面按钮（结果面板仍是原 4 个操作按钮：复制 / 导出纯文本 TXT / 导出带时间戳 TXT / 导出 SRT / 清空）。
- 未改动 SRT、纯文本、复制等其它导出路径。

### 修改前 / 修改后输出对比

修改前（仅起始时间）：

```
[00:00:00] 这是一个视频文件的语音测试。
[00:00:03] 请把视频里的声音自动转成文字。
```

修改后（起止时间）：

```
[00:00:00 - 00:00:02] 这是一个视频文件的语音测试。
[00:00:03 - 00:00:06] 请把视频里的声音自动转成文字。
```

---

## 四、验证

- **构建**：`:app:assembleStdDebug` 与 `:app:assembleFullDebug` 均 `BUILD SUCCESSFUL`。
- **端到端**（模拟器 emulator-5554，x86_64，API 28）：
  安装标准版 → 导入 `test_video_zh.wav` → 识别 4 段 → 点「导出带时间戳 TXT」→
  系统文件选择器保存到 `/sdcard/Download/test_video_zh_带时间戳.txt` → `adb pull` 回读。
- **回读内容**（UTF-8 无 BOM）：

  ```
  [00:00:00 - 00:00:02] 这是一个视频文件的语音测试。
  [00:00:03 - 00:00:06] 请把视频里的声音自动转成文字。
  [00:00:07 - 00:00:10] 今天我们来测试离线语音识别功能。
  [00:00:11 - 00:00:14] 识别准确率应该保持很高。
  ```

  起止时间与 SRT 的 `00:00:00,000 --> 00:00:02,xxx` 一致。

---

## 五、如何还原（回退到「仅起始时间」）

1. 把 `Transcript.kt` 的 `toTimestampedText` 改回：

   ```kotlin
   fun toTimestampedText(items: List<Utterance>): String =
       items.joinToString("\n") { "[${it.startLabel()}] ${it.text}" }
   ```

2. 重新构建：`./gradlew assembleStdDebug assembleFullDebug`。

> 改动**可逆且隔离**：单文件、单行，改回一行即回到旧行为。

---

## 六、备注

- 本次改动**只影响带时间戳 TXT**；SRT 一直就是起止时间，未动。
- 与「中文识别增强」是两次独立的改动，互不影响，可分别还原。
