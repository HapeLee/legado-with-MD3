package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ChapterSpeechGateway
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ChapterSpeechAnalysis
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.SpeechAnalysisStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeChapterSpeechUseCaseTest {

    @Test
    fun `persists stable rule segments and reuses cache`() = runBlocking {
        val gateway = MemoryChapterSpeechGateway()
        val useCase = AnalyzeChapterSpeechUseCase(gateway)
        val paragraphs = listOf(
            CanonicalSpeechParagraph(0, "旁白。“你好！”", 0),
        )

        val first = useCase("book", 3, paragraphs, now = 100)
        val second = useCase("book", 3, paragraphs, now = 200)

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(SpeechAnalysisStatus.Partial, first.analysis.status)
        assertEquals(first.analysis.id, second.analysis.id)
        assertEquals(first.segments.map { it.id }, second.segments.map { it.id })
        assertEquals(paragraphs.single().text, first.segments.joinToString("") { it.text })
        assertEquals(1, gateway.saveCount)
    }

    @Test
    fun `content change invalidates segmentation cache`() = runBlocking {
        val gateway = MemoryChapterSpeechGateway()
        val useCase = AnalyzeChapterSpeechUseCase(gateway)
        val firstParagraphs = listOf(CanonicalSpeechParagraph(0, "只有旁白。", 0))
        val secondParagraphs = listOf(CanonicalSpeechParagraph(0, "旁白改变了。", 0))

        val first = useCase("book", 0, firstParagraphs, now = 100)
        val second = useCase("book", 0, secondParagraphs, now = 200)

        assertFalse(second.fromCache)
        assertFalse(first.analysis.id == second.analysis.id)
        assertFalse(first.segments.map { it.id } == second.segments.map { it.id })
        assertEquals(2, gateway.saveCount)
    }
}

private class MemoryChapterSpeechGateway : ChapterSpeechGateway {
    private var analysis: ChapterSpeechAnalysis? = null
    private var segments: List<ChapterSpeechSegment> = emptyList()
    var saveCount: Int = 0

    override suspend fun getAnalysis(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        resolverVersion: String,
    ): ChapterSpeechAnalysis? = analysis?.takeIf {
        it.bookUrl == bookUrl &&
            it.chapterIndex == chapterIndex &&
            it.contentHash == contentHash &&
            it.resolverVersion == resolverVersion
    }

    override suspend fun upsertAnalysis(analysis: ChapterSpeechAnalysis) {
        this.analysis = analysis
    }

    override suspend fun saveAnalysis(
        analysis: ChapterSpeechAnalysis,
        segments: List<ChapterSpeechSegment>,
    ) {
        this.analysis = analysis
        this.segments = segments
        saveCount++
    }

    override suspend fun getSegments(analysisId: String): List<ChapterSpeechSegment> =
        segments.filter { it.analysisId == analysisId }

    override suspend fun replaceSegments(
        analysisId: String,
        segments: List<ChapterSpeechSegment>,
    ) {
        this.segments = segments
    }

    override suspend fun deleteChapter(bookUrl: String, chapterIndex: Int) {
        analysis = null
        segments = emptyList()
    }
}
