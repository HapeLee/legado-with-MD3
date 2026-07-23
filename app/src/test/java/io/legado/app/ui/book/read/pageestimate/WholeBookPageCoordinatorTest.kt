package io.legado.app.ui.book.read.pageestimate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeBookPageCoordinatorTest {

    private val config = PageEstimateConfig(
        readerType = 0,
        textSizePx = 32f,
        textHeightPx = 38.4f,
        lineSpacingPx = 8f,
        paragraphSpacingPx = 4f,
        titleTextSizePx = 40f,
        titleTextHeightPx = 48f,
        titleLineSpacingPx = 0f,
        titleTopSpacingPx = 20f,
        titleBottomSpacingPx = 20f,
        endPaddingPx = 20f,
        contentWidthPx = 1080,
        contentHeightPx = 1920,
    )

    @Test
    fun `pending exact correction is replayed after estimate`() = runBlocking {
        val changed = CompletableDeferred<Unit>()
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { 3f }) {
            changed.complete(Unit)
        }
        val loaderGate = CompletableDeferred<Unit>()
        val generation = coordinator.requestEstimate(config, BOOK_ID) {
            loaderGate.await()
            chapters(2)
        }

        coordinator.correctChapter(0, realPageCount = 7, layoutGeneration = generation)
        loaderGate.complete(Unit)
        withTimeout(2_000) { changed.await() }

        val state = requireNotNull(coordinator.getState(0, 0))
        assertEquals(10, state.totalPages)
        assertEquals(7, state.chapterPageCount)
    }

    @Test
    fun `older generation cannot publish or correct newer result`() = runBlocking {
        val oldLoader = CompletableDeferred<Unit>()
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { it.contentLength.toFloat() })
        val oldGeneration = coordinator.requestEstimate(config, BOOK_ID) {
            oldLoader.await()
            chapters(1, contentLength = 9)
        }
        val newGeneration = coordinator.requestEstimate(config, BOOK_ID) {
            chapters(1, contentLength = 4)
        }

        awaitState(coordinator)
        coordinator.correctChapter(0, 20, oldGeneration)
        oldLoader.complete(Unit)
        delay(50)

        assertEquals(4, coordinator.getState(0, 0)?.totalPages)
        coordinator.clear()
        assertNull(coordinator.getState(0, 0))
        assertEquals(newGeneration + 1, coordinator.generation)
    }

    @Test
    fun `book average length is fallback when every chapter length is unknown`() = runBlocking {
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { it.contentLength.toFloat() })
        coordinator.requestEstimate(config, BOOK_ID, fallbackContentLength = 5_000) {
            chapters(2, contentLength = null)
        }

        awaitState(coordinator)

        assertEquals(10_000, coordinator.getState(0, 0)?.totalPages)
    }

    @Test
    fun `known chapter median is preferred as unknown length fallback`() = runBlocking {
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { it.contentLength.toFloat() })
        coordinator.requestEstimate(config, BOOK_ID, fallbackContentLength = 9_000) {
            listOf(
                ChapterLengthInfo(0, "chapter-0", 10, contentLength = 100),
                ChapterLengthInfo(1, "chapter-1", 10, contentLength = null),
                ChapterLengthInfo(2, "chapter-2", 10, contentLength = 300),
            )
        }

        awaitState(coordinator)

        assertEquals(700, coordinator.getState(0, 0)?.totalPages)
    }

    @Test
    fun `loaded content length updates only that estimated chapter`() = runBlocking {
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { it.contentLength.toFloat() })
        val generation = coordinator.requestEstimate(config, BOOK_ID) { chapters(3, contentLength = 100) }
        awaitState(coordinator)

        coordinator.updateChapterLength(1, actualLength = 250, layoutGeneration = generation)

        assertEquals(450, coordinator.getState(0, 0)?.totalPages)
        assertEquals(250, coordinator.getState(1, 0)?.chapterPageCount)
        assertEquals(100, coordinator.getState(2, 0)?.chapterPageCount)
    }

    @Test
    fun `length update arriving before estimate is replayed`() = runBlocking {
        val loaderGate = CompletableDeferred<Unit>()
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { it.contentLength.toFloat() })
        val generation = coordinator.requestEstimate(config, BOOK_ID) {
            loaderGate.await()
            chapters(2, contentLength = null)
        }

        coordinator.updateChapterLength(0, actualLength = 400, layoutGeneration = generation)
        loaderGate.complete(Unit)
        awaitState(coordinator)

        assertEquals(2_400, coordinator.getState(0, 0)?.totalPages)
        assertEquals(400, coordinator.getState(0, 0)?.chapterPageCount)
    }

    @Test
    fun `length update cannot replace exact page count`() = runBlocking {
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { it.contentLength.toFloat() })
        val generation = coordinator.requestEstimate(config, BOOK_ID) { chapters(1, contentLength = 100) }
        awaitState(coordinator)
        coordinator.correctChapter(0, realPageCount = 7, layoutGeneration = generation)

        coordinator.updateChapterLength(0, actualLength = 500, layoutGeneration = generation)

        assertEquals(7, coordinator.getState(0, 0)?.totalPages)
        assertEquals(7, coordinator.getState(0, 0)?.chapterPageCount)
    }

    @Test
    fun `older generation length update is ignored`() = runBlocking {
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { it.contentLength.toFloat() })
        val oldGeneration = coordinator.requestEstimate(config, BOOK_ID) { chapters(1, contentLength = 100) }
        awaitState(coordinator)
        coordinator.requestEstimate(config, BOOK_ID) { chapters(1, contentLength = 200) }
        awaitTotalPages(coordinator, expected = 200)

        coordinator.updateChapterLength(0, actualLength = 500, layoutGeneration = oldGeneration)

        assertEquals(200, coordinator.getState(0, 0)?.totalPages)
    }

    @Test
    fun `exact result records metrics and both calibration buckets`() = runBlocking {
        val calibrationStore = FakeCalibrationStore()
        val recordedMetrics = mutableListOf<PageEstimateMetric>()
        val coordinator = WholeBookPageCoordinator(
            scope = this,
            estimator = ChapterPageEstimator { 10f },
            calibrationStore = calibrationStore,
            metrics = PageEstimateMetrics { recordedMetrics += it },
        )
        val generation = coordinator.requestEstimate(config, BOOK_ID) { chapters(1) }
        awaitState(coordinator)

        coordinator.correctChapter(0, realPageCount = 20, layoutGeneration = generation)

        val correction = recordedMetrics.filterIsInstance<PageEstimateMetric.ChapterCorrected>()
            .single()
        assertEquals(10f, correction.rawEstimate, 0.0001f)
        assertEquals(10, correction.estimatedPages)
        assertEquals(20, correction.realPages)
        assertEquals(0.5f, correction.relativeError, 0.0001f)
        // 逐书桶用于本书自己的拟合，排版桶用于下一本新书的冷启动先验，两边都要落。
        assertEquals(2, calibrationStore.recorded.size)
        assertEquals(setOf(10f to 20), calibrationStore.recorded.values.toSet())
    }

    /** 本书已有拟合时优先用它；样本不足才退回排版级先验。 */
    @Test
    fun `book scoped fit wins over the layout wide prior`() = runBlocking {
        val calibrationStore = FakeCalibrationStore()
        val coordinator = WholeBookPageCoordinator(
            scope = this,
            estimator = ChapterPageEstimator { 4f },
            calibrationStore = calibrationStore,
        )
        calibrationStore.fitEverything(slope = 3f, intercept = 0f)

        coordinator.requestEstimate(config, BOOK_ID) { chapters(2) }
        awaitTotalPages(coordinator, expected = 24)

        assertEquals(24, coordinator.getState(0, 0)?.totalPages)
    }

    /** 未拟合时必须逐位等价于引入回归之前的 ceil 行为。 */
    @Test
    fun `cold start still ceils the continuous estimate`() = runBlocking {
        val coordinator = WholeBookPageCoordinator(this, ChapterPageEstimator { 2.1f })

        coordinator.requestEstimate(config, BOOK_ID) { chapters(3) }
        awaitState(coordinator)

        assertEquals(9, coordinator.getState(0, 0)?.totalPages)
    }

    @Test
    fun `matching cached exact count overrides calibrated estimate without recording OLS`() = runBlocking {
        val calibrationStore = FakeCalibrationStore().apply {
            fitEverything(slope = 2f, intercept = 0f)
        }
        val exactStore = FakeExactPageCountStore().apply {
            values += exactCount(chapterIndex = 0, contentHash = 11L, pageCount = 7)
        }
        val coordinator = WholeBookPageCoordinator(
            scope = this,
            estimator = ChapterPageEstimator { 10f },
            calibrationStore = calibrationStore,
            exactPageCountStore = exactStore,
        )

        val generation = coordinator.requestEstimate(config, BOOK_ID) {
            listOf(
                ChapterLengthInfo(0, "chapter-0", 10, contentLength = 100, contentHash = 11L),
                ChapterLengthInfo(1, "chapter-1", 10, contentLength = 100, contentHash = 12L),
            )
        }
        awaitState(coordinator)

        assertEquals(27, coordinator.getState(0, 0)?.totalPages)
        assertTrue(coordinator.getState(0, 0)?.currentChapterExact == true)
        coordinator.correctChapter(0, realPageCount = 7, layoutGeneration = generation)
        assertTrue(calibrationStore.recorded.isEmpty())
        assertEquals(1, exactStore.values.size)
    }

    @Test
    fun `content hash mismatch drops stale exact count`() = runBlocking {
        val exactStore = FakeExactPageCountStore().apply {
            values += exactCount(chapterIndex = 0, contentHash = 11L, pageCount = 7)
        }
        val coordinator = WholeBookPageCoordinator(
            scope = this,
            estimator = ChapterPageEstimator { 10f },
            exactPageCountStore = exactStore,
        )

        coordinator.requestEstimate(config, BOOK_ID) {
            listOf(ChapterLengthInfo(0, "chapter-0", 10, contentLength = 100, contentHash = 99L))
        }
        awaitState(coordinator)

        assertEquals(10, coordinator.getState(0, 0)?.totalPages)
        assertFalse(coordinator.getState(0, 0)?.currentChapterExact == true)
        assertTrue(exactStore.values.isEmpty())
    }

    @Test
    fun `new exact result is persisted with strict layout identity`() = runBlocking {
        val exactStore = FakeExactPageCountStore()
        val coordinator = WholeBookPageCoordinator(
            scope = this,
            estimator = ChapterPageEstimator { 10f },
            exactPageCountStore = exactStore,
        )
        val generation = coordinator.requestEstimate(config, BOOK_ID) {
            listOf(ChapterLengthInfo(0, "chapter-0", 10, contentLength = 100, contentHash = 11L))
        }
        awaitState(coordinator)

        coordinator.correctChapter(0, realPageCount = 7, layoutGeneration = generation)
        withTimeout(2_000) {
            while (exactStore.values.isEmpty()) delay(10)
        }

        val saved = exactStore.values.single()
        assertEquals(config.layoutSignature, saved.layoutSignature)
        assertEquals(config.textEngineVersion, saved.engineVersion)
        assertEquals(11L, saved.contentHash)
        assertEquals(7, saved.pageCount)
    }

    private suspend fun awaitState(coordinator: WholeBookPageCoordinator) {
        withTimeout(2_000) {
            while (coordinator.getState(0, 0) == null) delay(10)
        }
    }

    private suspend fun awaitTotalPages(
        coordinator: WholeBookPageCoordinator,
        expected: Int,
    ) {
        withTimeout(2_000) {
            while (coordinator.getState(0, 0)?.totalPages != expected) delay(10)
        }
    }

    private companion object {
        const val BOOK_ID = "book://test"
    }

    private fun chapters(count: Int, contentLength: Int? = 1_000) =
        List(count) { index ->
            ChapterLengthInfo(index, "chapter-$index", 10, contentLength = contentLength)
        }

    private fun exactCount(
        chapterIndex: Int,
        contentHash: Long,
        pageCount: Int,
    ) = ExactChapterPageCount(
        bookId = BOOK_ID,
        chapterId = "chapter-$chapterIndex",
        chapterIndex = chapterIndex,
        contentHash = contentHash,
        layoutSignature = config.layoutSignature,
        engineVersion = config.textEngineVersion,
        pageCount = pageCount,
        updatedAt = 1L,
    )

    /** 只验协调器的接线（用哪个桶、往哪些桶写），最小二乘本身在 PageEstimateSamplesTest 里测。 */
    private class FakeCalibrationStore : PageEstimateCalibrationStore {
        private var fit: PageEstimateCalibration? = null
        val recorded = mutableMapOf<Long, Pair<Float, Int>>()

        override fun get(bucket: Long): PageEstimateCalibration =
            fit ?: PageEstimateCalibration()

        override fun record(
            bucket: Long,
            estimatedPages: Float,
            realPages: Int,
        ): PageEstimateCalibration {
            recorded[bucket] = estimatedPages to realPages
            return get(bucket)
        }

        fun fitEverything(slope: Float, intercept: Float) {
            fit = PageEstimateCalibration(
                slope = slope,
                intercept = intercept,
                sampleCount = PageEstimateCalibration.MIN_SAMPLES,
                fitted = true,
            )
        }
    }

    private class FakeExactPageCountStore : ExactChapterPageCountStore {
        val values = mutableListOf<ExactChapterPageCount>()

        override suspend fun load(
            bookId: String,
            layoutSignature: Long,
            engineVersion: Int,
        ) = values.filter {
            it.bookId == bookId && it.layoutSignature == layoutSignature &&
                it.engineVersion == engineVersion
        }

        override suspend fun save(value: ExactChapterPageCount) {
            values.removeAll {
                it.bookId == value.bookId && it.chapterId == value.chapterId &&
                    it.layoutSignature == value.layoutSignature
            }
            values += value
        }

        override suspend fun deleteChapter(bookId: String, chapterId: String) {
            values.removeAll { it.bookId == bookId && it.chapterId == chapterId }
        }
    }
}
