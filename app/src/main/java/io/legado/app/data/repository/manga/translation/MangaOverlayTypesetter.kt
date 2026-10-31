package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import io.legado.app.domain.model.manga.MangaTranslationPlan
import kotlin.math.ceil

internal class MangaTextLayoutException(val reason: MangaShapeTextLayout.Reason) :
    IllegalArgumentException("Translation does not fit its text area ($reason)")

/** Source-calibrated target, bounded shrink range and complete mask-backed runs for both local modes. */
internal object MangaOverlayTypesetter {
    private fun textPaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }

    private fun solve(paint: TextPaint, text: String, vertical: Boolean, width: Int, height: Int,
        mask: BooleanArray?, minimum: Int, maximum: Int, preferredColumns: Int? = null): MangaShapeTextLayout.Result =
        MangaShapeTextLayout.layout(width, height, mask, text,
            if (vertical) MangaShapeTextLayout.Orientation.VERTICAL_RIGHT_TO_LEFT else MangaShapeTextLayout.Orientation.HORIZONTAL,
            minimum, maximum, MangaShapeTextLayout.TextMeasurer { value, size ->
                paint.textSize = size.toFloat()
                val ink = Rect(); paint.getTextBounds(value, 0, value.length, ink)
                maxOf(paint.measureText(value), ink.width().toFloat()) + maxOf(1f, size * .08f) * 2
            }, preferSingleLine = false, preferredColumnCount = preferredColumns)

    /** Same normalization, line heights, stroke allowance and wrapping as final drawing. */
    fun fitsAtSize(text: String, vertical: Boolean, width: Int, height: Int, size: Float): Boolean {
        val font = ceil(size.toDouble()).toInt().coerceAtLeast(1)
        return solve(textPaint(), MangaShapeTextLayout.normalize(text), vertical, width, height, null, font, font).accepted
    }

    fun draw(bitmap: Bitmap, region: MangaTranslatedRegion, sourceSize: Float, scale: Float, patch: Boolean, background: Int, protectedRects: List<Rect> = emptyList()) {
        val area = region.layoutBox.layoutPixels(bitmap.width, bitmap.height)
        val mask = region.bubbleShape?.let { MangaContainerGeometry.raster(it, bitmap.width, bitmap.height, area) }
        val paint = textPaint()
        val text = MangaShapeTextLayout.normalize(region.translation)
        val range = requireNotNull(measureTranslationGlyphRange(paint, text, sourceSize * scale, constrainToBounds = true,
            measuredInk = region.fontMeasurement != null))
        val vertical = MangaTextTypesetter.vertical(text, region.vertical)
        val primary = solve(paint, text, vertical, area.width(), area.height(), mask, range.minPx, range.targetPx, mangaSourceColumnCount(region))
        // Source ink size is uncertain for coarse AI boxes. Keep the normal overlay range as
        // first choice, then fit the complete text below 35%, with the legacy 8px safety floor.
        // Small original lettering keeps its own lower floor; masks and bounds never expand.
        val emergencyMinimum = minOf(8, range.minPx)
        val fitted = if (!primary.accepted && emergencyMinimum < range.minPx &&
            primary.reason in setOf(MangaShapeTextLayout.Reason.NO_FIT, MangaShapeTextLayout.Reason.MASK_TOO_SMALL))
            solve(paint, text, vertical, area.width(), area.height(), mask, emergencyMinimum, range.minPx - 1) else primary
        if (!fitted.accepted) throw MangaTextLayoutException(fitted.reason)
        val anchors = region.sourceLines.map { it.bounds }.ifEmpty { region.eraseBoxes }.map { it.pixels(bitmap.width, bitmap.height) }
        val source = Rect(anchors.first()).apply { anchors.drop(1).forEach(::union) }
        val layout = MangaShapeTextLayout.anchor(fitted, area.width(), area.height(), mask,
            source.exactCenterX() - area.left, source.exactCenterY() - area.top)
        Log.d("MangaTypesetter", "sourcePx=$sourceSize scale=$scale range=${range.minPx}..${range.targetPx} finalPx=${layout.fontSizePx} shrinkFallback=${layout.fontSizePx < range.minPx} shape=${region.bubbleShape != null}")
        val canvas = Canvas(bitmap)
        if (patch) {
            val bg = Paint().apply { color = background }
            MangaPageComposer.erase(bitmap, MangaTranslationPlan(listOf(region)), patch = true, protectedRects = protectedRects)
            layout.runs.forEach { run -> canvas.drawRect((area.left + run.bounds.left).toFloat(), (area.top + run.bounds.top).toFloat(),
                (area.left + run.bounds.right).toFloat(), (area.top + run.bounds.bottom).toFloat(), bg) }
        }
        paint.textSize = layout.fontSizePx.toFloat()
        paint.strokeWidth = maxOf(1f, paint.textSize * .08f)
        paint.strokeJoin = Paint.Join.ROUND
        val light = ((background ushr 16 and 255) + (background ushr 8 and 255) + (background and 255)) >= 384
        fun glyph(value: String, centerX: Float, centerY: Float) {
            val ink = Rect(); paint.getTextBounds(value, 0, value.length, ink)
            val x = centerX - ink.exactCenterX(); val baseline = centerY - ink.exactCenterY()
            paint.style = Paint.Style.STROKE; paint.color = if (light) Color.WHITE else Color.BLACK
            if (vertical) drawMangaVerticalGlyph(canvas, paint, value, centerX, centerY)
            else canvas.drawText(value, x, baseline, paint)
            paint.style = Paint.Style.FILL; paint.color = if (light) Color.rgb(24, 24, 24) else Color.rgb(245, 245, 245)
            if (vertical) drawMangaVerticalGlyph(canvas, paint, value, centerX, centerY)
            else canvas.drawText(value, x, baseline, paint)
        }
        layout.runs.forEach { run ->
            val r = run.bounds
            canvas.save()
            canvas.clipRect(area.left + r.left, area.top + r.top, area.left + r.right, area.top + r.bottom)
            if (!vertical) glyph(run.text, area.left + (r.left + r.right) / 2f, area.top + (r.top + r.bottom) / 2f)
            else {
                val characters = mangaVerticalGlyphs(run.text)
                val cell = r.height.toFloat() / characters.size
                characters.forEachIndexed { i, value -> glyph(value, area.left + (r.left + r.right) / 2f, area.top + r.top + (i + .5f) * cell) }
            }
            canvas.restore()
        }
    }
}
