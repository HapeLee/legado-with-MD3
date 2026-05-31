package io.legado.app.utils

fun formatReadDuration(millis: Long): String {
    val days = millis / (1000 * 60 * 60 * 24)
    val hours = millis % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
    val minutes = millis % (1000 * 60 * 60) / (1000 * 60)
    val seconds = millis % (1000 * 60) / 1000
    val d = if (days > 0) "${days}d" else ""
    val h = if (hours > 0) "${hours}h" else ""
    val m = if (minutes > 0) "${minutes}m" else ""
    val s = if (seconds > 0) "${seconds}s" else ""
    return if ("$d$h$m$s".isBlank()) "0s" else "$d$h$m$s"
}
