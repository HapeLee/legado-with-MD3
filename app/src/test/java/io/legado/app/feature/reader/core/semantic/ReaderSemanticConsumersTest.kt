package io.legado.app.feature.reader.core.semantic

import android.app.Application
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.domain.model.BookContentProcessEngine
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.domain.usecase.RelocateMarkingTargetUseCase
import io.legado.app.feature.reader.core.readaloud.ReaderReadAloudChapter
import io.legado.app.feature.reader.core.selection.ReaderSearchMatcher
import io.legado.app.feature.reader.core.selection.ReaderSearchRequest
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.legacy.LegacyReaderStyleRangeMapper
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import io.legado.app.utils.GSON
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReaderSemanticConsumersTest {
    @Before fun setup() { RuntimeEnvironment.getApplication().injectAsAppCtx() }

    @Test
    fun searchMarkingsRelocationAndSpeechConsumeTheSameEntityDecodedBody() {
        val fragments = listOf("<p>A&amp;<strong>😀B</strong></p>", "<p><span>A&amp;😀</span><em>B</em></p>")
        val anchor = TextProcessAnchor(0, 999, "A&😀B", normalizedTextHash = "")
        val process = BookContentProcess(id = "mark:1", bookUrl = "book", chapterIndex = 0,
            kind = BookContentProcess.KIND_USER_HIGHLIGHT, anchorJson = GSON.toJson(anchor),
            actionJson = GSON.toJson(TextProcessAction(TextProcessAction.TYPE_MARK)),
            styleJson = GSON.toJson(TextProcessStyle(bgColor = 123)))
        fragments.forEach { html ->
            val raw = "<usehtml>$html</usehtml>"
            val processed = BookContentProcessEngine.apply(raw, listOf(process))
            val source = ReaderChapterSourceParser.parse(0, "A&😀B", listOf(processed.text), true, true, AndroidReaderHtmlSemanticTextResolver)
            assertEquals("A&😀B\n\n", source.semanticContent)
            assertTrue(ReaderSemanticContract.validate(source).isEmpty())
            val ranges = LegacyReaderStyleRangeMapper.map(source, emptyList(), processed.effectiveProcesses)
            assertEquals(0, ranges.single().start)
            assertEquals(5, ranges.single().endExclusive)
            val missing = process.copy(anchorJson = GSON.toJson(anchor.copy(selectedText = "missing")))
            assertTrue(LegacyReaderStyleRangeMapper.map(source, emptyList(), listOf(missing)).isEmpty())
            val search = ReaderSearchMatcher.find(source.semanticContent, "A&😀B", ReaderSearchRequest(6, 5, 1, false), "A&😀B")!!
            assertFalse(search.isTitle)
            assertEquals(0, search.start)
            val title = ReaderSearchMatcher.find(source.semanticContent, "A&😀B", ReaderSearchRequest(0, 5, 0, false), "A&😀B")!!
            assertTrue(title.isTitle)
            val target = RelocateMarkingTargetUseCase().locate(anchor,
                listOf(RelocateMarkingTargetUseCase.Candidate(0, source.semanticContent)))!!
            assertEquals(0, target.chapterPosition)
            val speech = ReaderReadAloudChapter.create(0, "title", source.semanticContent, listOf(0))
            // TTS omits trailing separators while retaining absolute body offsets.
            assertEquals(5, speech.chapterLength)
            speech.paragraphs.forEach { paragraph ->
                assertEquals(paragraph.text, source.semanticContent.substring(paragraph.chapterPosition, paragraph.endPosition))
            }
            assertTrue(speech.paragraphs.any { it.text.contains("A&😀B") && it.chapterPosition == 0 })
        }
    }
}
