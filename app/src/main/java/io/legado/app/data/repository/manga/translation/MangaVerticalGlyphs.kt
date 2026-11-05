package io.legado.app.data.repository.manga.translation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.text.BreakIterator
import java.util.Locale

/** Display tokens retain the original characters; three periods occupy one ellipsis cell. */
internal fun mangaVerticalGlyphs(text: String): List<String> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val result = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result += text.substring(start, end)
        start = end
        end = iterator.next()
    }
    var index = 0
    return buildList {
        while (index < result.size) {
            val value = result[index]
            if (value in listOf(".", "．") && index + 2 < result.size &&
                result[index + 1] == value && result[index + 2] == value) {
                add(value.repeat(3)); index += 3
            } else { add(value); index++ }
        }
    }
}

internal fun mangaVerticalDisplay(value: String): String = when (value) {
    "...", "．．．" -> "…"
    else -> value
}

// Unicode vertical layout fallback for glyphs whose horizontal outlines must turn sideways.
private val rotatedPunctuation = setOf("…", "⋯", "‥", "—", "―", "–", "ー", "（", "）", "(", ")",
        "「", "」", "『", "』", "【", "】", "〈", "〉", "《", "》", "［", "］", "｛", "｝")

internal fun mangaVerticalRotates(value: String): Boolean = mangaVerticalDisplay(value) in rotatedPunctuation

internal fun drawMangaVerticalGlyph(canvas: Canvas, paint: Paint, value: String, centerX: Float, centerY: Float) {
    val display = mangaVerticalDisplay(value)
    val ink = Rect()
    paint.getTextBounds(display, 0, display.length, ink)
    canvas.save()
    try {
        canvas.translate(centerX, centerY)
        if (mangaVerticalRotates(value)) canvas.rotate(90f)
        canvas.drawText(display, -ink.exactCenterX(), -ink.exactCenterY(), paint)
    } finally { canvas.restore() }
}
