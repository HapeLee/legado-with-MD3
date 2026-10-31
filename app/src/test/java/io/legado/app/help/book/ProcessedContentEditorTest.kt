package io.legado.app.help.book

import io.legado.app.domain.model.BookContentProcessEngine
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import org.junit.Assert.*
import org.junit.Test

class ProcessedContentEditorTest {
    private val editor = ProcessedContentEditor("　　")
    private val content = "前文\n<usehtml><p>今天<strong>天气很好</strong></p></usehtml>\n后文"
    private fun anchor(text: String) = TextProcessAnchor(0, 5, text, normalizedTextHash = "")
    private fun semantic(text: String) = ReaderChapterSourceParser.parse(0, "", MixedContentSegments.paragraphs(text, "　　", true), false, true, AndroidReaderHtmlSemanticTextResolver).semanticContent

    @Test
    fun mixedChapterUsesBodySpaceForCrossNodeEdit() {
        val result = editor.edit(content, anchor("今天天气"), TextProcessAction.replace("昨天")) as BookContentProcessEngine.EditResult.Applied
        assertEquals("　　前文\n昨天很好\n\n　　后文\n", semantic(result.content))
        assertTrue(result.content.contains("<strong>很好</strong>"))
    }

    @Test
    fun plainParagraphInMixedChapterRemainsEditable() {
        val result = editor.edit(content, anchor("后文"), TextProcessAction.replace("末文")) as BookContentProcessEngine.EditResult.Applied
        assertEquals(semantic(content).replace("后文", "末文"), semantic(result.content))
    }

    @Test
    fun structuralSelectionsLeaveOriginalContentUnchanged() {
        val result = editor.edit(content, anchor("前文\n今天"), TextProcessAction.delete())
        assertTrue(result is BookContentProcessEngine.EditResult.Unsupported)
        assertTrue(content.contains("前文"))
    }

    @Test
    fun insertBeforeAndAfterUseSemanticSelectionBoundaries() {
        for (type in listOf(TextProcessAction.TYPE_INSERT_BEFORE, TextProcessAction.TYPE_INSERT_AFTER)) {
            val result = editor.edit(content, anchor("天气"), TextProcessAction(type, text = "新")) as BookContentProcessEngine.EditResult.Applied
            val expected = if (type == TextProcessAction.TYPE_INSERT_BEFORE) "新天气" else "天气新"
            assertEquals(semantic(content).replace("天气", expected), semantic(result.content))
        }
    }
}
