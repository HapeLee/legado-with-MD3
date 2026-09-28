package io.legado.app.feature.reader.legacy

import io.legado.app.feature.reader.core.layout.ReaderImageLayoutMode
import io.legado.app.feature.reader.core.layout.ReaderImageOptions
import io.legado.app.feature.reader.core.layout.ReaderImageOptionsResolver
import io.legado.app.feature.reader.core.layout.ReaderInlineImagePlaceholder
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/** Keeps source-URL JSON parsing at the legacy input boundary; the Canvas core receives typed options. */
object LegacyReaderImageOptionsResolver : ReaderImageOptionsResolver {
    /** 与 `Book.imgStyleText` 一致；这里不引入数据层依赖，靠单测钉住取值。 */
    private const val imgStyleText = "TEXT"
    private const val imgStyleTextLowercase = "text"

    override fun resolve(source: String, htmlParagraph: Boolean): ReaderImageOptions? {
        val separator = paramPattern.find(source) ?: return null
        val values = GSON.fromJsonObject<Map<String, String>>(
            source.substring(separator.range.last + 1),
        ).getOrNull() ?: return null
        val rawStyle = values["style"]
        val style = rawStyle?.uppercase()
        val width = values["width"]
        return ReaderImageOptions(
            layoutMode = when {
                rawStyle == null -> null
                // 两条旧路径对"文字嵌入"的判据不同：
                // - HTML（`setTypeHtml`）用 `iStyle?.uppercase() == "TEXT"`，不区分大小写；
                // - 纯文本（`getTextChapter`）用字面量 `iStyle == "text" || iStyle == "TEXT"`，
                //   "Text"、"TEXT " 等拼写会走 `setTypeImage` 按整图排版。
                textIsInline(rawStyle, htmlParagraph) -> ReaderImageLayoutMode.INLINE
                // FULL/SINGLE 两条路径都是 `imageStyle?.uppercase()` 比较，不区分大小写。
                style == "FULL" -> ReaderImageLayoutMode.FULL_WIDTH
                style == "SINGLE" -> ReaderImageLayoutMode.SINGLE_PAGE
                else -> ReaderImageLayoutMode.STANDALONE
            },
            requestedWidthPx = width?.takeUnless { it.endsWith('%') }?.toFloatOrNull()
                ?.takeIf { it > 0f },
            requestedWidthFraction = width?.takeIf { it.endsWith('%') }
                ?.dropLast(1)?.toFloatOrNull()?.div(100f)?.takeIf { it > 0f },
            // 旧 `setTypeImage` 的对齐取自"有效样式"：单图 style 存在就只用它（不是 LEFT/RIGHT 即
            // 居中，**不**回落到书级）；缺失时才用书级样式。null = 未指定，由测量层回落书级。
            horizontalAlignment = when (style) {
                "LEFT" -> ReaderTextAlignment.START
                "RIGHT" -> ReaderTextAlignment.END
                else -> if (rawStyle == null) null else ReaderTextAlignment.CENTER
            },
            action = values["click"]?.takeIf(String::isNotBlank),
            // 占位字只是**纯文本**路径的概念：`"text"` 用 `srcReplaceChar`（袮）、`"TEXT"` 用
            // `reviewChar`（꧁）。旧 HTML 路径的行内图宽来自 ImageSpan 的 advance（没有占位字），
            // 这里取纯文本默认的袮。
            inlinePlaceholder = if (!htmlParagraph && rawStyle == imgStyleText) {
                ReaderInlineImagePlaceholder.REVIEW
            } else {
                ReaderInlineImagePlaceholder.SRC_REPLACE
            },
        )
    }

    private fun textIsInline(rawStyle: String, htmlParagraph: Boolean): Boolean =
        if (htmlParagraph) rawStyle.uppercase() == imgStyleText
        else rawStyle == imgStyleText || rawStyle == imgStyleTextLowercase
}
