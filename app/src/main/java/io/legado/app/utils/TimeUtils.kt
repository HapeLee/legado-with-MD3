package io.legado.app.utils

import kotlin.math.abs

fun Long.toTimeAgo(): String {
    val curTime = System.currentTimeMillis()
    val time = this
    val seconds = abs(System.currentTimeMillis() - time) / 1000f
    val end = if (time < curTime) " ago" else " from now"

    val start = when {
        seconds < 60 -> "${seconds.toInt()}s"
        seconds < 3600 -> {
            val minutes = seconds / 60f
            "${minutes.toInt()}m"
        }
        seconds < 86400 -> {
            val hours = seconds / 3600f
            "${hours.toInt()}h"
        }
        seconds < 604800 -> {
            val days = seconds / 86400f
            "${days.toInt()}d"
        }
        seconds < 2_628_000 -> {
            val weeks = seconds / 604800f
            "${weeks.toInt()}w"
        }
        seconds < 31_536_000 -> {
            val months = seconds / 2_628_000f
            "${months.toInt()}mo"
        }
        else -> {
            val years = seconds / 31_536_000f
            "${years.toInt()}y"
        }
    }
    return start + end
}