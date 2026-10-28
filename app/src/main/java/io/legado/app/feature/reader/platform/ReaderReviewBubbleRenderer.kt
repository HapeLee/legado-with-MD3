package io.legado.app.feature.reader.platform

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import com.caverock.androidsvg.SVG
import io.legado.app.feature.reader.core.layout.ReaderReviewBubbleMetrics
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.BubbleSvgPolicy
import io.legado.app.help.config.PackageSvgResourceResolver
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

/**
 * 段评气泡绘制：按当前外观（气泡包或旧版自定义 SVG）的模板渲染。
 *
 * 模板里的 `${num}` / `${color}` 由书源给的文本和取到的颜色替换；日间/夜间、普通/强调各有一套
 * 颜色，书源自己在图片选项里指定的颜色优先级最高。
 *
 * SVG 解析与栅格化只在缓存未命中时进行（按外观版本、文本、颜色、像素尺寸分键），绘制期只
 * `drawPicture`。模板不可用时退回一个与占位同尺寸的胶囊，保证不出现空白。
 */
object ReaderReviewBubbleRenderer {

    private const val MAX_CACHED_PICTURES = 48

    private data class PictureKey(
        val appearance: String,
        val text: String,
        val color: String,
        val width: Int,
        val height: Int,
    )

    private val pictures = object : LruCache<PictureKey, Picture>(MAX_CACHED_PICTURES) {}

    private val resolverLock = Any()

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        text: String,
        status: String,
        colorOverride: String?,
        textColorArgb: Int,
        isNight: Boolean,
        typeface: Typeface?,
    ) {
        val appearance = ReviewBubbleAppearances.current() ?: return
        drawAppearance(
            canvas = canvas,
            bounds = bounds,
            appearance = appearance,
            text = text,
            status = status,
            colorOverride = colorOverride,
            isNight = isNight,
            fallbackTextColorArgb = textColorArgb,
            typeface = typeface,
        )
    }

    /** 预览用：`source` 模式也画一个内置包外观，让设置页始终有可看的示例。 */
    fun drawPreview(
        canvas: Canvas,
        bounds: RectF,
        text: String,
        textColorArgb: Int,
        isNight: Boolean,
        typeface: Typeface?,
    ) {
        val appearance = ReviewBubbleAppearances.current()
            ?: ReviewBubbleAppearances.of(BubblePackageManager.builtinEntry())
        drawAppearance(
            canvas = canvas,
            bounds = bounds,
            appearance = appearance,
            text = text,
            status = "normal",
            colorOverride = null,
            isNight = isNight,
            fallbackTextColorArgb = textColorArgb,
            typeface = typeface,
        )
    }

    fun drawAppearance(
        canvas: Canvas,
        bounds: RectF,
        appearance: ReviewBubbleAppearance,
        text: String,
        status: String,
        colorOverride: String?,
        isNight: Boolean,
        fallbackTextColorArgb: Int,
        typeface: Typeface?,
    ) {
        val width = bounds.width().roundToInt().coerceAtLeast(1)
        val height = bounds.height().roundToInt().coerceAtLeast(1)
        val color = resolveColor(appearance, status, text, colorOverride, isNight)
        val key = PictureKey(appearance.cacheKey, text, color, width, height)
        val picture = pictures.get(key)
            ?: renderPicture(appearance, text, color, width, height, typeface)
                ?.also { pictures.put(key, it) }
        if (picture == null) {
            drawFallback(canvas, bounds, text, color, fallbackTextColorArgb, typeface)
            return
        }
        val save = canvas.save()
        canvas.translate(bounds.left, bounds.top)
        canvas.scale(bounds.width() / picture.width, bounds.height() / picture.height)
        canvas.drawPicture(picture)
        canvas.restoreToCount(save)
    }

    /** 模板的宽高比（`viewBox` 或文档宽高）；模板不可用时用默认值。 */
    fun aspectRatio(template: String): Float {
        val svg = runCatching { SVG.getFromString(template) }.getOrNull()
            ?: return ReaderReviewBubbleMetrics.DEFAULT_ASPECT_RATIO
        val box = svg.documentViewBox
        val width = box?.width()?.takeIf { it > 0f } ?: svg.documentWidth
        val height = box?.height()?.takeIf { it > 0f } ?: svg.documentHeight
        if (width <= 0f || height <= 0f) return ReaderReviewBubbleMetrics.DEFAULT_ASPECT_RATIO
        return (width / height).takeIf { it.isFinite() && it > 0f }
            ?: ReaderReviewBubbleMetrics.DEFAULT_ASPECT_RATIO
    }

    private fun renderPicture(
        appearance: ReviewBubbleAppearance,
        text: String,
        color: String,
        width: Int,
        height: Int,
        typeface: Typeface?,
    ): Picture? = synchronized(resolverLock) {
        runCatching {
            val svgText = appearance.template
                .replaceBubbleValue(listOf("displayText", "num"), BubbleSvgPolicy.escapeText(text))
                .replaceBubbleValue(listOf("displayColor", "color"), color)
            val svg = SVG.getFromInputStream(ByteArrayInputStream(svgText.toByteArray()))
            // androidsvg 的外部文件解析器是全局静态注册的，必须串行使用并按次注销。
            SVG.registerExternalFileResolver(
                PackageSvgResourceResolver(
                    appearance.resourceRoot,
                    appearance.resources,
                    width,
                    height,
                ) { typeface }
            )
            try {
                svg.renderToPicture(width, height)
            } finally {
                SVG.deregisterExternalFileResolver()
            }
        }.getOrNull()
    }

    private fun drawFallback(
        canvas: Canvas,
        bounds: RectF,
        text: String,
        color: String,
        textColorArgb: Int,
        typeface: Typeface?,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = parseColor(color) ?: textColorArgb
        paint.alpha = 40
        canvas.drawRoundRect(bounds, bounds.height() / 2f, bounds.height() / 2f, paint)
        if (text.isBlank()) return
        paint.color = parseColor(color) ?: textColorArgb
        paint.alpha = 255
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = bounds.height() * 0.62f
        if (typeface != null) paint.typeface = typeface
        val metrics = paint.fontMetrics
        val baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, bounds.centerX(), baseline, paint)
    }

    private fun resolveColor(
        appearance: ReviewBubbleAppearance,
        status: String,
        text: String,
        overrideColor: String?,
        isNight: Boolean,
    ): String {
        // 书源很少写 status=emphasis；`99+` 或三位数本身就表示评论很多，同样走强调色。
        val emphasis = status.equals("emphasis", ignoreCase = true) ||
            text.endsWith("+") ||
            (text.toIntOrNull() ?: 0) >= 100
        val fallback = if (emphasis) {
            BubblePackageManager.DEFAULT_EMPHASIS_COLOR
        } else {
            BubblePackageManager.DEFAULT_NORMAL_COLOR
        }
        val themeColor = if (isNight) {
            if (emphasis) appearance.nightEmphasisColor else appearance.nightNormalColor
        } else {
            if (emphasis) appearance.dayEmphasisColor else appearance.dayNormalColor
        }
        val value = overrideColor?.takeIf { it.isNotBlank() }
            ?: themeColor?.takeIf { it.isNotBlank() }
            ?: fallback
        val normalized = if (value.startsWith("#")) value else "#$value"
        return if (parseColor(normalized) != null) normalized else fallback
    }

    private fun parseColor(value: String): Int? =
        runCatching { android.graphics.Color.parseColor(value) }.getOrNull()

    private fun String.replaceBubbleValue(names: List<String>, value: String): String {
        val namePattern = names.joinToString("|") { Regex.escape(it) }
        val regex = Regex(
            """\$\{(?:$namePattern)\}|\$(?:$namePattern)\b|\{\{(?:$namePattern)\}\}""",
            RegexOption.IGNORE_CASE,
        )
        return replace(regex) { value }
    }
}
