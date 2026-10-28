package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReviewCountParser
import io.legado.app.help.ImageSourceOptions
import io.legado.app.help.ParsedImageSource
import java.net.URLDecoder
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * 书源段评图的语义与取数。
 *
 * 书源把评论数画在图片上，各站点写法不同：选项里直接给 `displayText`/`num`/`count`/`text`/`label`，
 * 画进 `click` 脚本的 `createSvg2(...)` 参数，写进内嵌 SVG 的 `<text>`，或只留下 `type`/`showcmt()`
 * 这类段评语义。这里把四种来源统一成一份 [ReviewBubbleSpec]，交给排版层占位、绘制层自绘。
 */
object ReviewBubbleSourcePolicy {

    private const val MAX_LABEL_CHARS = 24
    private const val MAX_SOURCE_CHARS = 2 * 1024 * 1024

    private val displayKeys = listOf("displayText", "num", "count", "text", "label")
    private val colorKeys = listOf("displayColor", "color")
    private val reviewTypes = setOf(
        "qd", "fqpl", "fanqie", "cmt", "comment", "comments",
        "review", "paragraph", "paragraphcomment",
    )
    private val displayParameter = parameterPattern(displayKeys, 48)
    private val colorParameter = parameterPattern(colorKeys, 32)
    private val statusParameter = parameterPattern(listOf("status"), 32)
    private val createSvgCount = Regex(
        """createSvg2?\s*\((?:[^,)]*,){3}\s*([0-9]{1,8})""",
        RegexOption.IGNORE_CASE,
    )
    private val colorPattern = Regex("^#?[0-9a-fA-F]{3,8}$")

    /**
     * @param source 正文里的图片源（可能带 `,{...}` 选项）
     * @param style 段落/整图样式（`TEXT` 表示文字嵌入）
     * @param click 调用方已经解析出的点击动作
     * @return 不是段评图时返回 null
     */
    fun resolve(source: String, style: String?, click: String?): ReviewBubbleSpec? {
        if (source.length > MAX_SOURCE_CHARS) return null
        val parsed = ImageSourceOptions.parse(source) ?: return null
        return resolve(parsed, style, click)
    }

    fun resolve(parsed: ParsedImageSource, style: String?, click: String?): ReviewBubbleSpec? {
        val uri = parsed.source
        val effectiveStyle = (parsed.style ?: style).orEmpty().trim()
        val action = parsed.click ?: click
        val inlineText = effectiveStyle.equals("TEXT", ignoreCase = true)
        // 书源显式声明整图/单页排版时，不再按段评处理。
        if (effectiveStyle.isNotEmpty() && !inlineText) return null

        val inlineSvg = inlineText && isDataSvg(uri)
        val semantic = hasReviewSemantics(parsed, action)
        val text = displayText(parsed, action)
        if (!semantic && !inlineSvg && text == null) return null
        return ReviewBubbleSpec(
            text = text,
            count = text?.let(::countOf),
            status = status(parsed),
            color = color(parsed),
        )
    }

    /** 书源没写 `style` 但确实带段评语义：排版期要把它当行内段评而不是整图。 */
    fun isImplicitInlineComment(parsed: ParsedImageSource, click: String?): Boolean =
        (parsed.style).isNullOrBlank() && hasReviewSemantics(parsed, parsed.click ?: click)

    private fun hasReviewSemantics(parsed: ParsedImageSource, click: String?): Boolean {
        val knownType = parsed.option("type")?.lowercase(Locale.ROOT) in reviewTypes
        val actions = listOfNotNull(parsed.click, parsed.option("pclick"), parsed.option("js"), click)
            .joinToString("\n")
            .lowercase(Locale.ROOT)
        return knownType ||
            actions.contains("showcmt(") ||
            actions.contains("showcomment(") ||
            actions.contains("paragraph")
    }

    private fun displayText(parsed: ParsedImageSource, click: String?): String? {
        displayKeys.firstNotNullOfOrNull { normalizeLabel(parsed.option(it)) }?.let { return it }
        listOfNotNull(parsed.click, parsed.option("pclick"), parsed.option("js"), click)
            .firstNotNullOfOrNull { script -> createSvgCount.find(script)?.groupValues?.getOrNull(1) }
            ?.let { return normalizeLabel(it) }
        listOf(parsed.source, click.orEmpty())
            .firstNotNullOfOrNull { value -> parameter(value, displayParameter) }
            ?.let { return it }
        return svgText(parsed.source)
    }

    private fun status(parsed: ParsedImageSource): String {
        val value = normalizeLabel(parsed.option("status"))
            ?: parameter(parsed.source, statusParameter)
        return if (value.equals("emphasis", ignoreCase = true)) "emphasis" else "normal"
    }

    private fun color(parsed: ParsedImageSource): String? {
        val value = normalizeLabel(parsed.option("displayColor"))
            ?: normalizeLabel(parsed.option("color"))
            ?: parameter(parsed.source, colorParameter)
        val candidate = value?.trim()?.takeIf { colorPattern.matches(it) } ?: return null
        return if (candidate.startsWith("#")) candidate else "#$candidate"
    }

    private fun parameter(source: String, pattern: Regex): String? =
        pattern.find(source)?.groupValues?.getOrNull(1)?.let(::decodePercent)

    private fun parameterPattern(names: List<String>, maximum: Int): Regex {
        val keys = names.joinToString("|", transform = Regex::escape)
        return Regex(
            """(?:^|[?&,])(?:$keys)=([^&,\s]{1,$maximum})""",
            RegexOption.IGNORE_CASE,
        )
    }

    private fun svgText(source: String): String? {
        val svg = ReviewCountParser.decodeDataUri(source) ?: return null
        val texts = SvgTextExtractor.texts(svg)
        return texts.firstOrNull { value -> value.any(Char::isDigit) } ?: texts.firstOrNull()
    }

    private fun normalizeLabel(value: String?): String? = value?.let {
        val plain = Jsoup.parseBodyFragment(it).text().trim()
        plain.takeIf(String::isNotEmpty)?.let { text ->
            val length = text.codePointCount(0, text.length).coerceAtMost(MAX_LABEL_CHARS)
            text.substring(0, text.offsetByCodePoints(0, length))
        }
    }

    private fun countOf(text: String): Int? =
        text.trim().removeSuffix("+").toIntOrNull()

    private fun decodePercent(value: String): String? = runCatching {
        // 正文链路用的是 Android Uri.decode 语义：字面 '+' 不当空格。
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }.getOrNull()

    private fun isDataSvg(source: String) = source.startsWith("data:image/svg+xml", ignoreCase = true)
}

/** 段评图的显示信息；[text] 为空表示只知道这是段评、还没有可见数字。 */
data class ReviewBubbleSpec(
    val text: String?,
    val count: Int?,
    val status: String,
    val color: String?,
)

/** 从内嵌 SVG 里读 `<text>`，供显示文本兜底。 */
internal object SvgTextExtractor {
    fun texts(svg: String): List<String> {
        val document = runCatching { Jsoup.parse(svg, "", Parser.xmlParser()) }.getOrNull() ?: return emptyList()
        if (document.allElements.none { it.normalName().substringAfter(':') == "svg" }) return emptyList()
        return document.allElements.asSequence()
            .filter { it.normalName().substringAfter(':') == "text" }
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
