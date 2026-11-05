package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import io.legado.app.domain.model.manga.*

/** Executes authored geometry; repairs clearly row-shaped CJK columns in tall vertical areas. */
internal object MangaAiTypesetter {
    private data class Metrics(val width: Float, val height: Float, val row: Float, val column: Float)

    fun draw(bitmap: Bitmap, region: MangaTranslatedRegion, scale: Float, patch: Boolean, protectedRects: List<Rect>) {
        val authoredStyle = checkNotNull(region.typesetting)
        val area = region.layoutBox.layoutPixels(bitmap.width, bitmap.height)
        val source = region.textBox.layoutPixels(bitmap.width, bitmap.height)
        val narrow = area.height() >= area.width() * 2f
        val cjk = MangaTextTypesetter.vertical(region.translation, true)
        val punctuationOnly = region.translation.codePoints().noneMatch { Character.isLetterOrDigit(it) }
        // A tall balloon alone is not source-direction evidence. Require the independently
        // recognized vertical source and its own slender text rectangle before correcting direction.
        val sourceVertical = region.vertical && narrow && source.height() >= source.width() * 2f && (cjk || punctuationOnly)
        val style = if (sourceVertical && !authoredStyle.vertical) authoredStyle.copy(vertical = true) else authoredStyle
        val rows = style.lines.map { it.trim() }
        val compact = rows.joinToString("").filterNot { it.isWhitespace() }
        val compactGlyphs = mangaVerticalGlyphs(compact)
        val shortCapsule = style.vertical && narrow && (cjk || punctuationOnly) && compactGlyphs.size in 1..5 &&
            mangaAiLinesPreserveText(listOf(compact), region.translation)
        var columns = if (shortCapsule) listOf(compactGlyphs) else if (style.vertical) rows.map(::mangaVerticalGlyphs) else emptyList()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", if (style.bold) Typeface.BOLD else Typeface.NORMAL) }
        fun metrics(size: Float): Metrics {
            paint.textSize = size
            val stroke = maxOf(1f, size * .06f)
            val pieces = if (style.vertical) columns.flatten() else rows
            val ink = Rect()
            var width = 0f; var height = 0f
            for (piece in pieces) {
                val display = if (style.vertical) mangaVerticalDisplay(piece) else piece
                paint.getTextBounds(display, 0, display.length, ink)
                val advance = maxOf(paint.measureText(display), ink.width().toFloat())
                val rotated = style.vertical && mangaVerticalRotates(piece)
                width = maxOf(width, if (rotated) ink.height().toFloat() else advance)
                height = maxOf(height, if (rotated) advance else ink.height().toFloat())
            }
            val row = maxOf(height + stroke * 2, size * if (style.vertical) 1.05f else style.lineSpacing)
            val column = maxOf(width + stroke * 2, size * style.lineSpacing)
            return if (style.vertical) Metrics(column * columns.size, row * columns.maxOf { it.size }, row, column)
                else Metrics(width + stroke * 2, row * rows.size, row, width + stroke * 2)
        }
        val requested = (style.fontSize * bitmap.height / 1000 * scale).coerceAtLeast(.5f)
        val preferred = if (shortCapsule) 1 else mangaSourceColumnCount(region)
        if (style.vertical && preferred != null && preferred != columns.size && MangaTextTypesetter.vertical(region.translation, true)) {
            val fitted = MangaShapeTextLayout.layout(area.width(), area.height(), null, region.translation,
                MangaShapeTextLayout.Orientation.VERTICAL_RIGHT_TO_LEFT,
                1, maxOf(1, minOf(requested.toInt(), area.width(), area.height())),
                MangaShapeTextLayout.TextMeasurer { text, px -> paint.textSize = px.toFloat(); paint.measureText(text) },
                preferSingleLine = false, preferredColumnCount = preferred)
            val candidate = fitted.runs.map { it.text }
            if (fitted.accepted && candidate.size == preferred && mangaAiLinesPreserveText(candidate, region.translation))
                columns = candidate.map(::mangaVerticalGlyphs)
        }
        var size = requested
        var measured = metrics(size)
        // Some vision models return one character/two characters per `lines` entry, as though
        // these were horizontal rows. Preserve ordinary authored columns, but do not shrink a
        // whole sentence into a one-cell-high strip. Metadata must not disable this final check.
        // Only the derived drawing columns change; cached translations and authored rows stay intact.
        val projectedFit = minOf(1f, area.width() / measured.width.coerceAtLeast(1f),
            area.height() / measured.height.coerceAtLeast(1f))
        val preservesWideSource = preferred != null && preferred == columns.size &&
            area.width() > area.height() * 1.25f && measured.width <= area.width()
        if (!preservesWideSource && style.vertical && columns.size > 1 && columns.sumOf { it.size } >= 6 &&
            measured.height * projectedFit < area.height() * .55f && measured.width > measured.height * 1.15f &&
            (measured.width > area.width() || columns.maxOf { it.size } <= 3) &&
            MangaTextTypesetter.vertical(rows.joinToString(""), true)) {
            val font = minOf(requested.toInt(), area.width(), area.height()).coerceAtLeast(1)
            val fitted = MangaShapeTextLayout.layout(area.width(), area.height(), null, rows.joinToString("\n"),
                MangaShapeTextLayout.Orientation.VERTICAL_RIGHT_TO_LEFT, 1, font,
                MangaShapeTextLayout.TextMeasurer { text, px -> paint.textSize = px.toFloat(); paint.measureText(text) },
                preferSingleLine = false, preferredColumnCount = (preferred ?: 2).coerceIn(1, 2))
            val candidate = fitted.runs.map { it.text }
            if (fitted.accepted && candidate.size < columns.size && mangaAiLinesPreserveText(candidate, region.translation)) {
                columns = candidate.map(::mangaVerticalGlyphs)
                measured = metrics(size)
            }
        }
        val ratio = minOf(1f, area.width() / measured.width.coerceAtLeast(1f), area.height() / measured.height.coerceAtLeast(1f))
        if (ratio < 1) { size = (size * ratio * .99f).coerceAtLeast(.5f); measured = metrics(size) }
        var attempts = 0
        while ((measured.width > area.width() || measured.height > area.height()) && size > .5f && attempts++ < 16) {
            size = (size * .9f).coerceAtLeast(.5f); measured = metrics(size)
        }
        if (measured.width > area.width() || measured.height > area.height()) throw MangaTextLayoutException(MangaShapeTextLayout.Reason.NO_FIT)
        val background = MangaPageComposer.background(bitmap, region.textBox.pixels(bitmap.width, bitmap.height), samplePadding = 1)
        if (patch) MangaPageComposer.erase(bitmap, MangaTranslationPlan(listOf(region)), patch = true, protectedRects = protectedRects)
        val light = ((background ushr 16 and 255) + (background ushr 8 and 255) + (background and 255)) >= 384
        val automaticColor = if (light) Color.rgb(24, 24, 24) else Color.rgb(245, 245, 245)
        val foreground = style.color?.let(Color::parseColor)?.takeIf { mangaInkContrast(it, background, 48) } ?: automaticColor
        paint.textSize = size; paint.strokeWidth = maxOf(1f, size * .06f); paint.strokeJoin = Paint.Join.ROUND
        val canvas = Canvas(bitmap)
        canvas.save(); canvas.clipRect(area)
        try {
            fun top(height: Float): Float = when (style.verticalAlign) {
                MangaTextVerticalAlign.TOP -> area.top.toFloat()
                MangaTextVerticalAlign.CENTER -> area.top + (area.height() - height) / 2
                MangaTextVerticalAlign.BOTTOM -> area.bottom - height
            }
            fun left(width: Float): Float = when (style.align) {
                MangaTextAlign.LEFT -> area.left.toFloat()
                MangaTextAlign.CENTER -> area.left + (area.width() - width) / 2
                MangaTextAlign.RIGHT -> area.right - width
            }
            fun glyph(text: String, x: Float, y: Float) {
                paint.style = Paint.Style.STROKE; paint.color = if (light) Color.WHITE else Color.BLACK
                canvas.drawText(text, x, y, paint)
                paint.style = Paint.Style.FILL; paint.color = foreground
                canvas.drawText(text, x, y, paint)
            }
            val ink = Rect()
            if (!style.vertical) rows.forEachIndexed { index, text ->
                paint.getTextBounds(text, 0, text.length, ink)
                val x = when (style.align) {
                    MangaTextAlign.LEFT -> area.left + paint.strokeWidth - ink.left
                    MangaTextAlign.CENTER -> area.exactCenterX() - ink.exactCenterX()
                    MangaTextAlign.RIGHT -> area.right - paint.strokeWidth - ink.right
                }
                glyph(text, x, top(measured.height) + (index + .5f) * measured.row - ink.exactCenterY())
            } else columns.forEachIndexed { column, text ->
                val centerX = left(measured.width) + (columns.size - column - .5f) * measured.column
                val start = top(text.size * measured.row)
                text.forEachIndexed { index, value ->
                    val centerY = start + (index + .5f) * measured.row
                    paint.style = Paint.Style.STROKE; paint.color = if (light) Color.WHITE else Color.BLACK
                    drawMangaVerticalGlyph(canvas, paint, value, centerX, centerY)
                    paint.style = Paint.Style.FILL; paint.color = foreground
                    drawMangaVerticalGlyph(canvas, paint, value, centerX, centerY)
                }
            }
        } finally { canvas.restore() }
        android.util.Log.d("MangaTypesetter", "owner=AI requestedPx=$requested finalPx=$size authoredRows=${rows.size} drawnColumns=${columns.size} sourceColumns=${mangaSourceColumnCount(region)} preferredColumns=$preferred shortCapsule=$shortCapsule authoredVertical=${authoredStyle.vertical} vertical=${style.vertical}")
    }
}
