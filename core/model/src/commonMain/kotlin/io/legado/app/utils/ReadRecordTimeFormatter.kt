package io.legado.app.utils

/** Formats accumulated reading time using the existing Chinese display convention. */
fun formatReadDuration(millis: Long): String {
    val days = millis / (1_000 * 60 * 60 * 24)
    val hours = millis % (1_000 * 60 * 60 * 24) / (1_000 * 60 * 60)
    val minutes = millis % (1_000 * 60 * 60) / (1_000 * 60)
    val seconds = millis % (1_000 * 60) / 1_000
    val dayText = if (days > 0) "${days}天" else ""
    val hourText = if (hours > 0) "${hours}小时" else ""
    val minuteText = if (minutes > 0) "${minutes}分钟" else ""
    val secondText = if (seconds > 0) "${seconds}秒" else ""
    return if ("$dayText$hourText$minuteText$secondText".isBlank()) {
        "0秒"
    } else {
        "$dayText$hourText$minuteText$secondText"
    }
}
