package io.legado.app.ui.book.readRecord

import io.legado.app.utils.formatReadDuration

object ReadRecordFormatter {
    fun formatWords(words: Long): String {
        return if (words >= 10000) {
            String.format("%.1f万字", words / 10000f)
        } else {
            "${words}字"
        }
    }

    fun formatDuration(millis: Long): String = formatReadDuration(millis)
}
