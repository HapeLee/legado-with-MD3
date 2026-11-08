package io.legado.app.service

import io.legado.app.feature.reader.core.cast.CastMarkers

/** 裁音、句中合成和进度回报共用同一份发声文本与正文坐标映射。 */
internal class HttpReadAloudSpeechPosition(
    val spoken: CastMarkers.Spoken,
    storedStart: Int,
) {
    private val start = spoken.spokenOf(storedStart)
    val remainingText: String get() = spoken.text.substring(start)
    val startFraction: Double get() = start.toDouble() / spoken.text.length.coerceAtLeast(1)

    fun storedOffset(positionMs: Long, durationMs: Long): Int {
        var spokenOffset = httpReadAloudParagraphOffset(
            spoken.text.length, start, positionMs, durationMs,
        ).coerceAtMost((spoken.text.length - 1).coerceAtLeast(start))
        if (spokenOffset in 1 until spoken.text.length &&
            spoken.text[spokenOffset].isLowSurrogate() && spoken.text[spokenOffset - 1].isHighSurrogate()
        ) spokenOffset--
        return spoken.storedOf(spokenOffset)
    }
}
