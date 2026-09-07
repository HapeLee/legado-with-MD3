package io.legado.app.feature.reader.platform

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import androidx.core.net.toUri
import io.legado.app.feature.reader.core.layout.GlyphClusters
import io.legado.app.feature.reader.core.layout.ReaderTextShaper
import io.legado.app.feature.reader.core.layout.ReaderFontBounds
import io.legado.app.feature.reader.core.layout.ReaderFontLineMetrics
import io.legado.app.feature.reader.core.layout.clusterGlyphs
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.utils.validFontLeading
import java.io.File
import splitties.init.appCtx

object ReaderAndroidPaintFactory {
    /** 进程级字体缓存：同一 path/weight/italic/family 只做一次磁盘读取与解析。 */
    private val typefaceCache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()

    fun create(style: ReaderTextStyle): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = style.colorArgb
            textSize = style.fontSizePx
            typeface = loadTypeface(style.fontPath, style.fontWeight, false, style.fontFamily)
            // Match the reader's synthetic italic; don't substitute another font's italic face.
            textSkewX = if (style.italic) -0.25f else 0f
            isLinearText = style.linearText
            isStrikeThruText = style.strikeThrough
            isUnderlineText = style.nativeUnderline
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setFontVariationSettings("'wght' ${style.fontWeight}")
            }
            style.shadow?.let { setShadowLayer(it.radiusPx, it.dxPx, it.dyPx, it.colorArgb) }
        }

    fun createTextPaint(style: ReaderTextStyle): TextPaint = TextPaint(create(style))

    /** 行盒基线偏移：行高已排除异常 leading（见 PaintExtensions.validFontLeading）。 */
    fun baselineOffset(paint: Paint): Float = paint.fontMetrics.let { paint.validFontLeading - it.ascent }

    fun loadTypeface(path: String, weight: Int, italic: Boolean, family: String = "sans-serif"): Typeface {
        val key = "$path|$weight|$italic|$family"
        typefaceCache[key]?.let { return it }
        val base = runCatching {
            when {
                path.startsWith("content://", ignoreCase = true) ->
                    appCtx.contentResolver.openFileDescriptor(path.toUri(), "r")?.use {
                        Typeface.Builder(it.fileDescriptor).build()
                    }
                path.isNotBlank() && File(path).isFile -> Typeface.Builder(File(path)).build()
                else -> null
            }
        }.getOrNull() ?: Typeface.create(family, Typeface.NORMAL)
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, weight.coerceIn(1, 1000), italic)
        } else {
            val typefaceStyle = when {
                weight >= 600 && italic -> Typeface.BOLD_ITALIC
                weight >= 600 -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(base, typefaceStyle)
        }
        typefaceCache[key] = typeface
        return typeface
    }
}

/** Android shaping boundary. The paint must match the paint used by ReaderCanvasSurface. */
class AndroidReaderTextShaper(paint: TextPaint) : ReaderTextShaper {
    // Pagination inserts the gap between clusters. Do not include it again in glyph widths.
    private val paint = TextPaint(paint).apply { letterSpacing = 0f }
    override val fontBounds = this.paint.fontMetrics.let { ReaderFontBounds(it.top, it.bottom, it.descent) }
    override val fontLineMetrics = this.paint.fontMetrics.let {
        // 行盒高度与 utils/PaintExtensions.textHeight 同规则：异常 leading 不计入
        //（如方正新楷体声明 leading≈1em，原样计入会让行高翻倍、空隙全在字形上方）
        val height = it.descent - it.ascent + this.paint.validFontLeading
        ReaderFontLineMetrics(
            heightPx = height,
            baselineOffsetPx = height - it.descent,
            ascentPx = -it.ascent,
            descentPx = it.descent,
        )
    }

    override fun shape(text: String): GlyphClusters {
        if (text.isEmpty()) return GlyphClusters(emptyList(), emptyList())
        val widths = FloatArray(text.length)
        paint.getTextWidths(text, widths)
        return clusterGlyphs(text, widths)
    }
}
