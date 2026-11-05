package io.legado.app.model

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.util.Size
import io.legado.app.feature.reader.legacy.LegacyReaderPaginationStyleFactory
import io.legado.app.feature.reader.platform.ReaderAndroidPaintFactory
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.BubbleSvgPolicy
import io.legado.app.help.config.PackageSvgResourceResolver
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.dpToPx
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

/**
 * 把段评气泡的 SVG 模板渲染成位图。
 *
 * 模板支持 `${num}` / `${displayText}` / `${color}` / `${displayColor}` 占位；配色随昼夜与
 * `status`（normal / emphasis）取当前气泡包配置，允许图片自带 `displayColor` 覆盖。
 *
 * 尺寸口径与旧 `TextChapterLayout` 一致：排版槽宽取占位字的 advance（密度无关的 em 量）再乘
 * [inlineWidthScale]，位图按最终槽宽栅格化；[getSize] 只提供无目标宽度时的名义边长（dp）。
 */
object ParagraphBubbleRenderer {

    const val SCHEME_PREFIX = "bubble://paragraph"

    /** 名义边长（dp）。真正参与排版的是占位字 advance × [inlineWidthScale]。 */
    private const val BASE_SIDE_DP = 64f

    fun isBubbleSrc(src: String): Boolean {
        return ParagraphBubbleSource.parse(src) != null
    }

    /** 行内槽宽倍数：包内 `sizeScale`（对齐旧 `TextChapterLayout` 的 `inlineWidth`）。 */
    fun inlineWidthScale(): Float = currentScale()

    fun getSize(src: String): Size {
        val side = (BASE_SIDE_DP.dpToPx() * currentScale()).roundToInt().coerceAtLeast(1)
        return Size(side, side)
    }

    fun cacheKey(src: String, width: Int, height: Int?): String {
        val entry = BubblePackageManager.currentEntry()
        val config = entry.config
        val spec = ParagraphBubbleSource.parse(src)
        return buildString {
            append(src)
            append('#')
            append(width)
            append('x')
            append(height ?: 0)
            append('#')
            append(entry.dirName)
            append('#')
            append(config.updatedAt)
            append('#')
            append(config.sizeScale)
            append('#')
            append(if (ReadStyleResolver.isNightTheme()) "night" else "day")
            append('#')
            append(resolveColor(config, spec))
        }
    }

    fun render(src: String, width: Int, height: Int?): Bitmap? {
        val spec = ParagraphBubbleSource.parse(src) ?: return null
        val entry = BubblePackageManager.currentEntry()
        val config = entry.config
        val color = resolveColor(config, spec)
        val text = BubbleSvgPolicy.escapeText(spec.displayText)
        val svg = config.svgTemplate
            .replaceBubbleValue(listOf("displayText", "num"), text)
            .replaceBubbleValue(listOf("displayColor", "color"), color)
        val targetWidth = width.coerceAtLeast(1)
        val targetHeight = height ?: targetWidth
        val input = ByteArrayInputStream(svg.toByteArray())
        return SvgUtils.createBitmap(
            input,
            targetWidth,
            height,
            PackageSvgResourceResolver(
                entry.localDir,
                config.resources,
                targetWidth,
                targetHeight
            ) { bodyTypeface() }
        )
    }

    /**
     * 气泡文字沿用正文 Typeface（旧 `ParagraphBubbleRenderer` 直接取 `ChapterProvider.contentPaint`）。
     * 包内 `asset://` 字体仍由 [PackageSvgResourceResolver] 优先命中，这里只是兜底。
     */
    private fun bodyTypeface(): Typeface? = runCatching {
        ReaderAndroidPaintFactory.loadTypeface(
            path = ReadBookConfig.textFont,
            weight = LegacyReaderPaginationStyleFactory.resolveWeight(ReadBookConfig.textBold),
            italic = ReadBookConfig.textItalic,
            family = when (ReadBookConfig.systemTypefaces) {
                1 -> "serif"
                2 -> "monospace"
                else -> "sans-serif"
            },
        )
    }.getOrNull()

    private fun currentScale(): Float {
        return BubblePackageManager.currentEntry().config.sizeScale
            .coerceIn(BubblePackageManager.MIN_SIZE_SCALE, BubblePackageManager.MAX_SIZE_SCALE)
    }

    private fun resolveColor(
        config: BubblePackageManager.Config,
        spec: ParagraphBubbleSource.Spec?
    ): String {
        val emphasis = spec?.status.equals("emphasis", ignoreCase = true)
        val fallback = if (emphasis) {
            BubblePackageManager.DEFAULT_EMPHASIS_COLOR
        } else {
            BubblePackageManager.DEFAULT_NORMAL_COLOR
        }
        val themeColor = if (ReadStyleResolver.isNightTheme()) {
            if (emphasis) config.nightEmphasisColor else config.nightNormalColor
        } else {
            if (emphasis) config.dayEmphasisColor else config.dayNormalColor
        }
        val value = spec?.displayColor?.takeIf { it.isNotBlank() }
            ?: themeColor?.takeIf { it.isNotBlank() }
            ?: fallback
        return runCatching {
            val normalized = if (value.startsWith("#")) value else "#$value"
            Color.parseColor(normalized)
            normalized
        }.getOrDefault(fallback)
    }

    private fun String.replaceBubbleValue(names: List<String>, value: String): String {
        val namePattern = names.joinToString("|") { Regex.escape(it) }
        val regex = Regex(
            """\$\{(?:$namePattern)\}|\$(?:$namePattern)\b|\{\{(?:$namePattern)\}\}""",
            RegexOption.IGNORE_CASE
        )
        return replace(regex) { value }
    }
}
