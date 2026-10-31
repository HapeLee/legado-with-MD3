package io.legado.app.feature.reader.core.semantic

import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource as Inline
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock as Block
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import org.junit.Assert.*
import org.junit.Test

class ReaderSemanticContractTest {
    @Test
    fun utf16BoundariesAndStrictIdentity() {
        val text = "A😀B"
        assertEquals(4, text.length)
        for (pos in listOf(0, 1, 3, 4)) assertTrue(ReaderSemanticLocation.isValidOffset(text, pos))
        for (pos in listOf(-1, 2, 5)) assertFalse(ReaderSemanticLocation.isValidOffset(text, pos))
        assertEquals(1, ReaderSemanticLocation.clampOffset(text, 2))
        assertEquals(0, ReaderSemanticLocation.clampOffset(text, Int.MIN_VALUE))
        assertEquals(4, ReaderSemanticLocation.clampOffset(text, Int.MAX_VALUE))
        assertTrue(ReaderSemanticLocation.matchesAt(text, 1, "😀"))
        assertFalse(ReaderSemanticLocation.matchesAt(text, 1, "\uD83D"))
        assertFalse(ReaderSemanticLocation.matchesAt(text, Int.MAX_VALUE, "B"))
        assertFalse(ReaderSemanticLocation.matchesAt(text, 0, "a"))
        assertFalse(ReaderSemanticLocation.validateRange(text, SemanticRange(1, 2)))
        assertTrue(ReaderSemanticLocation.validateRange(text, SemanticRange(1, 3)))
    }

    @Test
    fun searchDoesNotCreateHalfEmojiSelections() {
        val match = io.legado.app.feature.reader.core.selection.ReaderSearchMatcher.find(
            "A😀B", "\uD83D", io.legado.app.feature.reader.core.selection.ReaderSearchRequest(1, 1, 0, false))
        assertNull(match)
    }

    @Test
    fun pageSnippetMustUniquelyContainStoredPosition() {
        assertTrue(ReaderSemanticLocation.matchesContainingText("prefix page text suffix", 12, "page text"))
        assertFalse(ReaderSemanticLocation.matchesContainingText("page text page text", 3, "page text"))
        assertFalse(ReaderSemanticLocation.matchesContainingText("page text", 50, "page text"))
        assertFalse(ReaderSemanticLocation.matchesContainingText("A😀B", 2, "😀"))
        assertFalse(ReaderSemanticLocation.matchesContainingText("A😀B", 1, "\uD83D"))
    }

    @Test
    fun titlesBgmAndPageMarkersAreOutsideBodySpace() {
        val source = ReaderChapterSourceParser.parse(0, "标题", listOf("A😀B", "[newpage]"), true, true,
            bgmScenes = mapOf(0 to ("pool" to "song")))
        assertEquals("A😀B\n", source.semanticContent)
        assertTrue(ReaderSemanticContract.validate(source).isEmpty())
        assertTrue(ReaderSemanticContract.validate(source.withTitleVisibility(false)).isEmpty())
    }

    @Test
    fun detectsInvalidCountsOffsetsAndContentWithoutRepairing() {
        val text = "A😀\uFFFC\n"
        val bad = ReaderChapterSource(0, "", listOf(
            Block.Paragraph(listOf(Inline.Text("X", 0), Inline.Text("😀", 2), Inline.Image("x", 0), Inline.BlankLine(3)), 0),
            Block.Html("", 4, Int.MAX_VALUE), Block.PageBreak(-1),
        ), 900, text)
        val violations = ReaderSemanticContract.validate(bad)
        assertTrue(violations.size >= 6)
        assertEquals(text, bad.semanticContent)
        assertTrue(ReaderSemanticContract.validateInlines(text, listOf(Inline.Text("😀", 1), Inline.Text("A", 0))).isNotEmpty())
    }

    @Test
    fun detectsOverlappingBlocksAndInlineOutsideItsOwnParagraph() {
        val source = ReaderChapterSource(0, "", listOf(
            Block.Text("AB", 0),
            Block.Paragraph(listOf(Inline.Text("A", 0)), 1),
        ), 3, "AB\n")
        assertTrue(ReaderSemanticContract.validate(source).size >= 2)
    }

    @Test
    fun legacyFallbackIsExplicitlyDifferentForRuby() {
        val paragraphs = listOf("<usehtml><ruby>漢<rt>かん</rt></ruby></usehtml>")
        val legacy = ReaderChapterSourceParser.parse(0, "", paragraphs, false, true)
        assertEquals("漢かん\n", legacy.semanticContent)
        // Android production callers inject the shared Jsoup resolver instead of this fallback.
    }
}
