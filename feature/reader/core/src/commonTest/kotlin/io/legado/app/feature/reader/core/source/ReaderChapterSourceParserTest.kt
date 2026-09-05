package io.legado.app.feature.reader.core.source

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ReaderChapterSourceParserTest {
    @Test
    fun titleCanBeShownAfterHiddenLoadWithoutChangingBodyAnchors() {
        val source = ReaderChapterSourceParser.parse(
            2, "章名\n副题", listOf("甲<img src=\"a.jpg\">乙", "后段"), false, true,
        )
        val shown = source.withTitleVisibility(true)
        val titles = shown.blocks.take(2).filterIsInstance<ReaderChapterSourceBlock.Text>()
        assertEquals(listOf("章名", "副题"), titles.map { it.value })
        assertEquals(listOf(0, 3), titles.map { it.chapterPosition })
        assertEquals(source.blocks, shown.blocks.drop(2))
        assertEquals(source.semanticContent, shown.semanticContent)
        assertEquals(source.characterCount, shown.characterCount)
        assertEquals(source, shown.withTitleVisibility(false))
        assertEquals(shown, shown.withTitleVisibility(true))
    }

    @Test
    fun parsesMixedTextImagesAndSpecialBlocksWithStablePositions() {
        val source = ReaderChapterSourceParser.parse(
            chapterIndex = 2,
            title = "章名",
            paragraphs = listOf("甲<img src=\"a.jpg\">乙", "[newpage]", "<usehtml><b>丙</b></usehtml>"),
            includeTitle = true,
            adaptSpecialStyle = true,
        )
        assertTrue((source.blocks.first() as ReaderChapterSourceBlock.Text).isTitle)
        val body = source.blocks.drop(1)
        val paragraph = body[0] as ReaderChapterSourceBlock.Paragraph
        assertEquals(listOf(0, 1, 2), paragraph.items.map { it.chapterPosition })
        assertEquals(4, body[1].chapterPosition)
        assertTrue(body[2] is ReaderChapterSourceBlock.Html)
        assertEquals("甲\uFFFC乙\n丙\n", source.semanticContent)
        assertEquals(source.characterCount, source.semanticContent.length)
    }

    @Test
    fun leavesSpecialMarkersAsTextWhenAdaptationIsDisabled() {
        val source = ReaderChapterSourceParser.parse(0, "", listOf("[newpage]"), false, false)
        val paragraph = source.blocks.single() as ReaderChapterSourceBlock.Paragraph
        assertEquals("[newpage]", (paragraph.items.single() as ReaderChapterInlineSource.Text).value)
    }

    @Test
    fun htmlUsesRenderedCharacterLengthForFollowingPositions() {
        val source = ReaderChapterSourceParser.parse(
            0, "", listOf("<usehtml><b>甲</b>&amp;乙<img src=\"x\"></usehtml>", "后"), false, true,
        )
        val html = source.blocks[0] as ReaderChapterSourceBlock.Html
        val following = source.blocks[1] as ReaderChapterSourceBlock.Paragraph
        assertEquals(4, html.semanticLength)
        assertEquals(5, following.chapterPosition)
        assertEquals("甲&乙\uFFFC\n后\n", source.semanticContent)
        assertEquals(source.characterCount, source.semanticContent.length)
    }
}
