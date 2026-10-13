package com.shengji.asr

/** 一段识别结果。 */
data class Utterance(
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    /** 形如 00:01:23 */
    fun startLabel(): String = formatClock(startMs)

    fun rangeLabel(): String = "${formatClock(startMs)} - ${formatClock(endMs)}"

    companion object {
        fun formatClock(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }

        fun formatSrtTime(ms: Long): String {
            val t = ms.coerceAtLeast(0)
            val h = t / 3_600_000
            val m = (t % 3_600_000) / 60_000
            val s = (t % 60_000) / 1000
            val milli = t % 1000
            return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
        }
    }
}

/** 结果导出格式。 */
object TranscriptExporter {

    fun toPlainText(items: List<Utterance>): String =
        items.joinToString("\n") { it.text }

    fun toTimestampedText(items: List<Utterance>): String =
        items.joinToString("\n") { "[${it.rangeLabel()}] ${it.text}" }

    fun toSrt(items: List<Utterance>): String {
        val sb = StringBuilder()
        items.forEachIndexed { index, item ->
            sb.append(index + 1).append('\n')
            sb.append(Utterance.formatSrtTime(item.startMs))
                .append(" --> ")
                .append(Utterance.formatSrtTime(item.endMs))
                .append('\n')
            sb.append(item.text.trim()).append("\n\n")
        }
        return sb.toString()
    }
}