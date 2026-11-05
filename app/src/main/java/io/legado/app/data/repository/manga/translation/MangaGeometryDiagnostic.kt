package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslationPlan
import java.util.Locale

/** Standalone working preview; never used as an AI input, erasure base or published reader image. */
internal fun mangaGeometryDiagnostic(source: Bitmap, plan: MangaTranslationPlan): Bitmap {
    val result = checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    try {
        val canvas = Canvas(result)
        val cleaned = checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
        try {
            MangaPageComposer.erase(cleaned, plan)
            val before = IntArray(source.width); val after = IntArray(source.width)
            for (y in 0 until source.height) {
                source.getPixels(before, 0, source.width, 0, y, source.width, 1)
                cleaned.getPixels(after, 0, source.width, 0, y, source.width, 1)
                for (x in before.indices) if (before[x] != after[x]) before[x] = Color.rgb(190, 0, 190)
                result.setPixels(before, 0, source.width, 0, y, source.width, 1)
            }
        } finally { cleaned.recycle() }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; style = Paint.Style.STROKE }
        fun box(bounds: MangaTextBox, color: Int, layout: Boolean = false) {
            paint.style = Paint.Style.STROKE; paint.color = color
            canvas.drawRect(if (layout) bounds.layoutPixels(source.width, source.height) else bounds.pixels(source.width, source.height), paint)
        }
        plan.regions.forEach { region ->
            region.aiGeometry?.containerBox?.let { box(it, Color.rgb(200, 150, 0)) }
            // 客户端最终采用的气泡轮廓：区分"模型没给框"和"给了但被本地校验丢弃"。
            region.bubbleShape?.let { shape ->
                val bounds = shape.bounds.pixels(source.width, source.height)
                val mask = MangaContainerGeometry.raster(shape, source.width, source.height, bounds)
                val colors = IntArray(bounds.width() * bounds.height())
                result.getPixels(colors, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
                val w = bounds.width(); val h = bounds.height()
                for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    if (!mask[i]) continue
                    val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                        !mask[i - 1] || !mask[i + 1] || !mask[i - w] || !mask[i + w]
                    if (edge) colors[i] = Color.rgb(0, 190, 220)
                }
                result.setPixels(colors, 0, w, bounds.left, bounds.top, w, h)
            }
            box(region.layoutBox, Color.rgb(0, 160, 40), layout = true); box(region.textBox, Color.RED)
            region.aiGeometry?.glyphSamples?.forEach { box(it.bounds, Color.BLUE) }
            val r = region.textBox.pixels(source.width, source.height)
            paint.style = Paint.Style.FILL; paint.color = Color.RED; paint.textSize = 16f
            val label = region.typesetting?.let { "AI " + String.format(Locale.ROOT, "%.1fpx", it.fontSize * source.height / 1000) + " ×" + it.lines.size }
                ?: String.format(Locale.ROOT, "%.1fpx", (region.sourceFontSize ?: 0f) * source.height / 1000)
            canvas.drawText(region.id + " " + label,
                r.left.toFloat(), maxOf(16f, r.top - 3f), paint)
        }
        return result
    } catch (failure: Throwable) { result.recycle(); throw failure }
}
