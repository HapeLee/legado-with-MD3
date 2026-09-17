package io.legado.app.feature.reader.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBodyOnlyFilterTest {

    private val imageTag = "<img src=\"data:image/png;base64,AAAA\">"

    @Test
    fun inlineImageIsFoldedInPlace() {
        assertEquals(
            "正文前〔图片1〕正文后",
            ReaderBodyOnlyFilter.toBodyOnly("正文前${imageTag}正文后"),
        )
    }

    @Test
    fun useHtmlParagraphAndPageBreakBecomeWholeLineMarkers() {
        val text = "第一段\n<usehtml><div>hello</div></usehtml>\n[newpage]\n第二段"
        val spans = ReaderBodyOnlyFilter.scan(text)
        assertEquals(
            listOf(ReaderBodyOnlyFilter.Kind.Html, ReaderBodyOnlyFilter.Kind.PageBreak),
            spans.map { it.kind },
        )
        assertEquals(
            "第一段\n〔富文本1〕\n〔分页2〕\n第二段",
            ReaderBodyOnlyFilter.toBodyOnly(text, spans),
        )
    }

    @Test
    fun withoutAdaptSpecialStyleHtmlStaysVisible() {
        val text = "第一段\n<usehtml><div>hello</div></usehtml>\n[newpage]"
        assertTrue(ReaderBodyOnlyFilter.scan(text, adaptSpecialStyle = false).isEmpty())
        assertEquals(text, ReaderBodyOnlyFilter.toBodyOnly(text, emptyList()))
    }

    @Test
    fun plainTextIsUntouched() {
        val text = "第一段\n第二段"
        assertTrue(ReaderBodyOnlyFilter.scan(text).isEmpty())
        assertEquals(text, ReaderBodyOnlyFilter.toBodyOnly(text))
    }

    @Test
    fun reassembleWithoutEditsRestoresOriginalText() {
        val text = "正文前${imageTag}正文后\n<usehtml>x</usehtml>\n[newpage]\n尾"
        val spans = ReaderBodyOnlyFilter.scan(text)
        val folded = ReaderBodyOnlyFilter.toBodyOnly(text, spans)
        assertEquals(text, ReaderBodyOnlyFilter.reassemble(folded, spans))
    }

    @Test
    fun reassembleKeepsNonBodySourceAfterBodyEdit() {
        val text = "第一行\n${imageTag}\n第二行"
        val spans = ReaderBodyOnlyFilter.scan(text)
        assertEquals(
            "改过的第一行\n${imageTag}\n第二行加字",
            ReaderBodyOnlyFilter.reassemble("改过的第一行\n〔图片1〕\n第二行加字", spans),
        )
    }

    @Test
    fun deletedMarkerStillWritesBackSourceInOrder() {
        val text = "第一行\n${imageTag}\n第二行"
        val spans = ReaderBodyOnlyFilter.scan(text)
        val restored = ReaderBodyOnlyFilter.reassemble("第一行\n第二行", spans)
        assertTrue(restored.contains(imageTag))
        assertEquals("第一行\n第二行$imageTag", restored)
    }

    @Test
    fun bodyOnlyOffsetFollowsFoldedCharacters() {
        val text = "abc${imageTag}def"
        val spans = ReaderBodyOnlyFilter.scan(text)
        assertEquals(3, ReaderBodyOnlyFilter.toBodyOnlyOffset(3, spans))
        assertEquals(11, ReaderBodyOnlyFilter.toBodyOnlyOffset(text.length, spans))
        assertEquals("abc〔图片1〕def", ReaderBodyOnlyFilter.toBodyOnly(text, spans))
    }
}
