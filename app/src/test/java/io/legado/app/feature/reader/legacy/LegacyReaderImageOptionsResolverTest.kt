package io.legado.app.feature.reader.legacy

import io.legado.app.feature.reader.core.layout.ReaderImageLayoutMode
import io.legado.app.feature.reader.core.layout.ReaderInlineImagePlaceholder
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyReaderImageOptionsResolverTest {
    @Test
    fun parsesTypedImageUrlOptions() {
        val options = LegacyReaderImageOptionsResolver.resolve(
            "https://example/image, {\"style\":\"right\",\"width\":\"37.5%\",\"click\":\"open(\\\"x\\\")\"}",
            htmlParagraph = false,
        )!!
        assertEquals(ReaderImageLayoutMode.STANDALONE, options.layoutMode)
        assertEquals(ReaderTextAlignment.END, options.horizontalAlignment)
        assertEquals(.375f, options.requestedWidthFraction!!, 0f)
        assertEquals("open(\"x\")", options.action)
    }

    @Test
    fun parsesAbsoluteWidthAndKnownModesCaseInsensitively() {
        val options = LegacyReaderImageOptionsResolver
            .resolve("x,{\"style\":\"full\",\"width\":\"123\"}", htmlParagraph = false)!!
        assertEquals(ReaderImageLayoutMode.FULL_WIDTH, options.layoutMode)
        assertEquals(123f, options.requestedWidthPx!!, 0f)
    }

    /** 旧纯文本路径的 `iStyle == "text" || iStyle == "TEXT"` 是精确匹配，迁移必须保留。 */
    @Test
    fun onlyExactLegacyTextSpellingsBecomeInlineImages() {
        fun layoutMode(style: String, htmlParagraph: Boolean = false) =
            LegacyReaderImageOptionsResolver
                .resolve("x,{\"style\":\"$style\"}", htmlParagraph)!!.layoutMode

        assertEquals(ReaderImageLayoutMode.INLINE, layoutMode("text"))
        assertEquals(ReaderImageLayoutMode.INLINE, layoutMode("TEXT"))
        // 混合大小写/带空格的拼写在旧 `setTypeImage` 里按整图排版（不区分大小写的前提不成立）。
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("Text"))
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("tExT"))
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("text "))
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("Text "))

        // 旧 HTML 路径（`setTypeHtml`）用的是 `iStyle?.uppercase()`，不区分大小写……
        assertEquals(ReaderImageLayoutMode.INLINE, layoutMode("Text", htmlParagraph = true))
        assertEquals(ReaderImageLayoutMode.INLINE, layoutMode("tExT", htmlParagraph = true))
        // ……但带空格的拼写 uppercase 之后也对不上 "TEXT"。
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("text ", htmlParagraph = true))
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("Text ", htmlParagraph = true))
    }

    /**
     * 旧 `TextChapterLayout` 按拼写选占位字：`"text"` → `srcReplaceChar`（袮），
     * `"TEXT"` → `reviewChar`（꧁）。两个字的 advance 不同，行内图的宽也随之不同。
     */
    @Test
    fun exactTextSpellingPicksItsLegacyPlaceholderGlyph() {
        fun placeholder(style: String, htmlParagraph: Boolean = false) =
            LegacyReaderImageOptionsResolver
                .resolve("x,{\"style\":\"$style\"}", htmlParagraph)!!.inlinePlaceholder

        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, placeholder("text"))
        assertEquals(ReaderInlineImagePlaceholder.REVIEW, placeholder("TEXT"))
        // 非文字嵌入时占位字不参与排版（书级 TEXT 走默认的袮）。
        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, placeholder("full"))
        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, placeholder("Text"))
        // 旧 HTML 路径没有占位字（行内宽来自 ImageSpan），统一取纯文本默认的袮。
        assertEquals(
            ReaderInlineImagePlaceholder.SRC_REPLACE,
            placeholder("TEXT", htmlParagraph = true),
        )
        // 没有 style（布局模式回落到书级设置，书级 TEXT 旧版也用袮）时保持旧默认。
        assertEquals(
            ReaderInlineImagePlaceholder.SRC_REPLACE,
            LegacyReaderImageOptionsResolver
                .resolve("x,{\"click\":\"go()\"}", htmlParagraph = false)!!.inlinePlaceholder,
        )
    }

    /**
     * 旧 `setTypeImage` 的对齐取自"有效样式"：单图 style 存在就只用它（不是 LEFT/RIGHT 即居中，
     * 不回落到书级）；只有 style 缺失时 `horizontalAlignment` 才是 null（未指定，由测量层回落书级）。
     */
    @Test
    fun alignmentIsUnspecifiedOnlyWhenTheImageStyleIsAbsent() {
        fun alignment(source: String) = LegacyReaderImageOptionsResolver
            .resolve(source, htmlParagraph = false)!!.horizontalAlignment

        assertEquals(ReaderTextAlignment.START, alignment("x,{\"style\":\"left\"}"))
        assertEquals(ReaderTextAlignment.END, alignment("x,{\"style\":\"right\"}"))
        assertEquals(ReaderTextAlignment.CENTER, alignment("x,{\"style\":\"full\"}"))
        assertNull(alignment("x,{\"click\":\"go()\"}"))
    }

    @Test fun malformedOrAbsentOptionsDoNotInventOverrides() {
        assertNull(LegacyReaderImageOptionsResolver.resolve("x", htmlParagraph = false))
        assertNull(LegacyReaderImageOptionsResolver.resolve("x,{bad}", htmlParagraph = false))
        assertNull(LegacyReaderImageOptionsResolver.resolve("x", htmlParagraph = true))
        assertNull(LegacyReaderImageOptionsResolver.resolve("x,{bad}", htmlParagraph = true))
    }
}
