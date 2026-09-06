package io.legado.app.feature.reader.platform

import android.app.Application
import android.graphics.Color
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.core.layout.ReaderHtmlParagraph
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidReaderHtmlSourceResolverTest {
    private val html = """
        <h2>甲&amp;&#x4E59;</h2>
        <p><a href="https://example">链接</a><br><big><font color="#123456">大</font></big><img src="pic"></p>
        <blockquote>末</blockquote>
    """.trimIndent()

    @Test fun sourcePositionsUseTheSameHtmlSemanticsAsCanvasRendering() {
        val semantic = AndroidReaderHtmlSemanticTextResolver.resolve(html)
        val source = ReaderChapterSourceParser.parse(
            0, "", listOf("<usehtml>$html</usehtml>", "后"), false, true,
            AndroidReaderHtmlSemanticTextResolver,
        )
        val htmlBlock = source.blocks.first() as ReaderChapterSourceBlock.Html
        val following = source.blocks.last() as ReaderChapterSourceBlock.Paragraph

        assertTrue(semantic.contains("甲&乙"))
        assertTrue(semantic.contains('\uFFFC'))
        assertEquals(semantic.length, htmlBlock.semanticLength)
        assertEquals(semantic.length + 1, following.chapterPosition)
        assertEquals(semantic + "\n后\n", source.semanticContent)
        assertEquals(source.semanticContent.length, source.characterCount)
    }

    @Test fun renderedRunsKeepLinksSizesColorsImagesAndAbsoluteOffsets() {
        val semantic = AndroidReaderHtmlSemanticTextResolver.resolve(html)
        val paragraphs = AndroidReaderHtmlSourceResolver(20f, 2f).resolve(html, 7)
        val items = paragraphs.flatMap { it.items }
        val link = items.filterIsInstance<ReaderChapterInlineSource.Text>()
            .first { it.value.contains("链接") }
        val large = items.filterIsInstance<ReaderChapterInlineSource.Text>()
            .first { it.value.contains("大") }
        val image = items.filterIsInstance<ReaderChapterInlineSource.Image>().single()

        assertEquals(7 + semantic.indexOf("链接"), link.chapterPosition)
        assertEquals("https://example", link.style.link)
        assertTrue(large.style.fontSizeScale > 1f)
        assertEquals(Color.rgb(0x12, 0x34, 0x56), large.style.colorArgb)
        assertEquals(7 + semantic.indexOf('\uFFFC'), image.chapterPosition)
        assertEquals("pic", image.source)
    }

    @Test fun inlineRunsKeepLegacyStrikeThroughAndTypefaceSpans() {
        val items = AndroidReaderHtmlSourceResolver(20f, 2f)
            .resolve("<p><del>删除</del><tt>等宽</tt><u>下划线</u></p>", 0)
            .flatMap { it.items }
            .filterIsInstance<ReaderChapterInlineSource.Text>()
        val deleted = items.first { it.value.contains("删除") }
        val monospace = items.first { it.value.contains("等宽") }
        val underlined = items.first { it.value.contains("下划线") }

        assertTrue(deleted.style.strikeThrough)
        assertEquals("monospace", monospace.style.fontFamily)
        assertTrue(underlined.style.underline)
    }

    @Test fun inlineRunsKeepSuperscriptAndSubscriptSemantics() {
        val items = AndroidReaderHtmlSourceResolver(20f, 2f)
            .resolve("<p>基<sup>上</sup><sub>下</sub></p>", 0)
            .flatMap { it.items }
            .filterIsInstance<ReaderChapterInlineSource.Text>()
        val superscript = items.first { it.value.contains("上") }
        val subscript = items.first { it.value.contains("下") }

        assertTrue(superscript.style.superscript)
        assertTrue(subscript.style.subscript)
        assertEquals(1f, superscript.style.fontSizeScale, 0f)
        assertEquals(1f, subscript.style.fontSizeScale, 0f)
    }

    @Test fun htmlImagesPreserveSourceUrlOptionsForTheTypedLayoutBoundary() {
        val source = "pic,{\"style\":\"full\",\"width\":\"50%\",\"click\":\"go()\"}"
        val image = AndroidReaderHtmlSourceResolver(20f, 2f)
            .resolve("<img src='$source'>", 3).flatMap { it.items }
            .filterIsInstance<ReaderChapterInlineSource.Image>().single()

        assertEquals(source, image.source)
        assertEquals(3, image.chapterPosition)
    }

    @Test fun consecutiveBreaksPreserveANewlineOnlyVisualParagraphWithoutATrailingPhantomLine() {
        val html = "甲<br><br>乙<br>"
        val semantic = AndroidReaderHtmlSemanticTextResolver.resolve(html)
        val paragraphs = AndroidReaderHtmlSourceResolver(20f, 2f).resolve(html, 10)

        assertEquals("甲\n\n乙\n", semantic)
        assertEquals(3, paragraphs.size)
        assertEquals("甲", (paragraphs[0].items.single() as ReaderChapterInlineSource.Text).value)
        assertEquals(12, (paragraphs[1].items.single() as ReaderChapterInlineSource.BlankLine).chapterPosition)
        assertEquals("乙", (paragraphs[2].items.single() as ReaderChapterInlineSource.Text).value)
    }

    @Test fun blockLevelLeadingMarginsArePreservedAsTypedParagraphGeometry() {
        val paragraphs = AndroidReaderHtmlSourceResolver(20f, 2f).resolve(
            "<blockquote>引文内容</blockquote><ul><li>列表项</li></ul>",
            0,
        )

        val quote = paragraphs.first { paragraph ->
            paragraph.items.filterIsInstance<ReaderChapterInlineSource.Text>()
                .any { it.value.contains("引文") }
        }
        val list = paragraphs.first { paragraph ->
            paragraph.items.filterIsInstance<ReaderChapterInlineSource.Text>()
                .any { it.value.contains("列表") }
        }
        assertTrue(quote.firstLineMarginPx > 0f)
        assertTrue(quote.restLineMarginPx > 0f)
        assertTrue(list.firstLineMarginPx > 0f)
        assertTrue(list.restLineMarginPx > 0f)
        assertEquals(ReaderParagraphDecorationKind.QUOTE, quote.decorations.single().kind)
        assertEquals(ReaderParagraphDecorationKind.BULLET, list.decorations.single().kind)
        assertTrue(quote.decorations.single().sizePx > 0f)
        assertTrue(list.decorations.single().sizePx > 0f)
    }

    @Test fun nestedListMarkersCarryIncreasingLeadingOffsets() {
        val paragraphs = AndroidReaderHtmlSourceResolver(20f, 2f).resolve(
            "<ul><li>外层<ul><li>内层</li></ul></li></ul>",
            0,
        )
        val inner = paragraphs.first { paragraph ->
            paragraph.items.filterIsInstance<ReaderChapterInlineSource.Text>()
                .any { it.value.contains("内层") }
        }
        val bullets = inner.decorations.filter { it.kind == ReaderParagraphDecorationKind.BULLET }

        assertTrue(bullets.size >= 2)
        assertTrue(bullets.zipWithNext().all { (outer, nested) -> nested.leadingOffsetPx > outer.leadingOffsetPx })
    }

    /**
     * `BulletSpan#getColor` 是 API 28 才加的读取方法。修复前 minSdk 26 上无条件调用它，
     * 在 Android 8.0/8.1（API 26/27）会抛 NoSuchMethodError；紧邻的 stripeWidth/bulletRadius
     * 当时都有守卫，只有 color 漏了。这两条用例分别钉住守卫两侧的行为。
     */
    @Test
    @Config(sdk = [35])
    fun bulletDecorationReadsColorFromThePlatformOnApi28AndAbove() {
        val bullet = bulletDecoration()

        assertEquals(ReaderParagraphDecorationKind.BULLET, bullet.kind)
        // 默认 Html 渲染的 bullet 未显式设色，读到 0 时按"未指定"归一成 null
        assertNull(bullet.colorArgb)
        assertTrue(bullet.sizePx > 0f)
    }

    @Test
    @Config(sdk = [26])
    fun bulletDecorationFallsBackWithoutReadingColorBelowApi28() {
        val bullet = bulletDecoration()

        assertEquals(ReaderParagraphDecorationKind.BULLET, bullet.kind)
        // API 26/27 读不到颜色：必须走守卫分支而不是调用不存在的方法
        assertNull(bullet.colorArgb)
        assertEquals(4f, bullet.sizePx, 0.001f)
    }

    private fun bulletDecoration() =
        AndroidReaderHtmlSourceResolver(20f, 2f)
            .resolve("<ul><li>列表项</li></ul>", 0)
            .first { paragraph: ReaderHtmlParagraph ->
                paragraph.items.filterIsInstance<ReaderChapterInlineSource.Text>()
                    .any { it.value.contains("列表") }
            }
            .decorations
            .single { it.kind == ReaderParagraphDecorationKind.BULLET }
}
