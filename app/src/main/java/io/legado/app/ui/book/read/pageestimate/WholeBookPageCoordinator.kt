package io.legado.app.ui.book.read.pageestimate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil

class WholeBookPageCoordinator(
    private val scope: CoroutineScope,
    /** 仅用于测试，生产路径使用 [HeuristicPageEstimator]。 */
    private val estimator: ChapterPageEstimator? = null,
    private val calibrationStore: PageEstimateCalibrationStore = PageEstimateCalibrationStore.None,
    private val exactPageCountStore: ExactChapterPageCountStore = ExactChapterPageCountStore.None,
    private val metrics: PageEstimateMetrics = PageEstimateMetrics.None,
    private val onStateChanged: () -> Unit = {},
) {
    private val lock = Any()
    private var pageIndex: WholeBookPageIndex? = null
    private val pendingCorrections = mutableMapOf<Int, PendingCorrection>()
    private val pendingLengthUpdates = mutableMapOf<Int, PendingLengthUpdate>()
    private var estimateJob: Job? = null
    private var activeEstimate: ActiveEstimate? = null

    @Volatile
    var generation: Long = 0L
        private set

    fun requestEstimate(
        config: PageEstimateConfig,
        bookId: String,
        fallbackContentLength: Int? = null,
        chapterLoader: suspend () -> List<ChapterLengthInfo>,
    ): Long {
        val taskGeneration: Long
        val requestStartedAt = System.nanoTime()
        val layoutSignature = config.layoutSignature
        val layoutBucket = config.calibrationBucket
        val bookBucket = bookCalibrationBucket(bookId, layoutBucket)
        // 段落密度是逐书的属性（对话型 / 叙述型差 40%），所以优先用本书自己的拟合，
        // 样本不够时才退回排版级的聚合值做冷启动先验。
        val calibration = calibrationStore.get(bookBucket)
            .takeIf(PageEstimateCalibration::fitted)
            ?: calibrationStore.get(layoutBucket)
        val chapterEstimator = estimator ?: HeuristicPageEstimator()
        synchronized(lock) {
            generation++
            taskGeneration = generation
            estimateJob?.cancel()
            pageIndex = null
            activeEstimate = null
            pendingCorrections.clear()
            pendingLengthUpdates.clear()
        }
        estimateJob = scope.launch(Dispatchers.Default) {
            val chapters = withContext(Dispatchers.IO) { chapterLoader() }
                .sortedBy(ChapterLengthInfo::chapterIndex)
            ensureActive()
            if (chapters.isEmpty()) return@launch

            val knownLengths = chapters.mapNotNull(ChapterLengthInfo::contentLength)
                .filter { it > 0 }
                .sorted()
            val fallbackLength = knownLengths.getOrNull(knownLengths.size / 2)
                ?: fallbackContentLength?.takeIf { it > 0 }
                ?: DEFAULT_CHAPTER_LENGTH
            // 保留未校准的连续估算值：校正到来时它就是最小二乘的 x。
            val rawEstimates = FloatArray(chapters.size) { index ->
                ensureActive()
                chapterEstimator.estimate(chapters[index].toInput(config, fallbackLength))
            }
            val pageCounts = IntArray(chapters.size) { index ->
                calibration.apply(rawEstimates[index])
            }
            val cachedExactCounts = withContext(Dispatchers.IO) {
                runCatching {
                    exactPageCountStore.load(
                        bookId = bookId,
                        layoutSignature = layoutSignature,
                        engineVersion = config.textEngineVersion,
                    )
                }.getOrDefault(emptyList())
            }
            ensureActive()
            val exactChapters = mutableSetOf<Int>()
            val staleChapterIds = mutableSetOf<String>()
            val chapterIndicesById = chapters
                .mapIndexed { index, chapter -> chapter.chapterId to index }
                .toMap()
            cachedExactCounts.forEach { cached ->
                val chapterIndex = chapterIndicesById[cached.chapterId]
                val chapter = chapterIndex?.let(chapters::getOrNull)
                if (chapterIndex != null &&
                    chapter?.contentHash != null &&
                    chapter.contentHash == cached.contentHash
                ) {
                    pageCounts[chapterIndex] = cached.pageCount.coerceAtLeast(1)
                    exactChapters += chapterIndex
                } else {
                    staleChapterIds += cached.chapterId
                }
            }
            if (staleChapterIds.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    staleChapterIds.forEach { chapterId ->
                        runCatching { exactPageCountStore.deleteChapter(bookId, chapterId) }
                    }
                }
            }

            val changed = synchronized(lock) {
                if (generation != taskGeneration) return@synchronized false
                val newIndex = WholeBookPageIndex(chapters.size)
                newIndex.initialize(pageCounts, exactChapters)
                pageIndex = newIndex
                activeEstimate = ActiveEstimate(
                    generation = taskGeneration,
                    bookId = bookId,
                    layoutSignature = layoutSignature,
                    bookBucket = bookBucket,
                    layoutBucket = layoutBucket,
                    calibration = calibration,
                    config = config,
                    estimator = chapterEstimator,
                    fallbackContentLength = fallbackLength,
                    chapters = chapters.toMutableList(),
                    rawEstimates = rawEstimates,
                )
                pendingLengthUpdates.values
                    .filter { it.generation == taskGeneration }
                    .forEach { update ->
                        applyLengthUpdateLocked(
                            pageIndex = newIndex,
                            estimate = checkNotNull(activeEstimate),
                            chapterIndex = update.chapterIndex,
                            actualLength = update.actualLength,
                            contentHash = update.contentHash,
                        )
                    }
                pendingLengthUpdates.clear()
                pendingCorrections.values
                    .filter { it.generation == taskGeneration }
                    .forEach { correction ->
                        applyCorrectionLocked(
                            pageIndex = newIndex,
                            estimate = checkNotNull(activeEstimate),
                            chapterIndex = correction.chapterIndex,
                            realPageCount = correction.realPageCount,
                        )
                    }
                pendingCorrections.clear()
                true
            }
            if (changed) {
                metrics.record(
                    PageEstimateMetric.EstimateCompleted(
                        generation = taskGeneration,
                        layoutSignature = layoutSignature,
                        calibration = calibration,
                        chapterCount = chapters.size,
                        knownLengthCount = knownLengths.size,
                        totalPages = pageCounts.sum(),
                        durationMillis = (System.nanoTime() - requestStartedAt) / 1_000_000L,
                    )
                )
                onStateChanged()
            }
        }
        return taskGeneration
    }

    fun correctChapter(chapterIndex: Int, realPageCount: Int, layoutGeneration: Long) {
        val changed = synchronized(lock) {
            if (layoutGeneration != generation) return@synchronized false
            val index = pageIndex
            if (index == null || !index.isInitialized) {
                pendingCorrections[chapterIndex] = PendingCorrection(
                    chapterIndex = chapterIndex,
                    realPageCount = realPageCount.coerceAtLeast(1),
                    generation = layoutGeneration,
                )
                return@synchronized false
            }
            val estimate = activeEstimate ?: return@synchronized false
            applyCorrectionLocked(index, estimate, chapterIndex, realPageCount)
        }
        if (changed) onStateChanged()
    }

    fun updateChapterLength(chapterIndex: Int, actualLength: Int, layoutGeneration: Long) {
        updateChapterContent(
            chapterIndex = chapterIndex,
            actualLength = actualLength,
            contentHash = null,
            layoutGeneration = layoutGeneration,
        )
    }

    fun updateChapterContent(
        chapterIndex: Int,
        actualLength: Int,
        contentHash: Long?,
        layoutGeneration: Long,
    ) {
        if (actualLength < 0) return
        val changed = synchronized(lock) {
            if (layoutGeneration != generation) return@synchronized false
            val index = pageIndex
            if (index == null || !index.isInitialized) {
                pendingLengthUpdates[chapterIndex] = PendingLengthUpdate(
                    chapterIndex = chapterIndex,
                    actualLength = actualLength,
                    contentHash = contentHash,
                    generation = layoutGeneration,
                )
                return@synchronized false
            }
            val estimate = activeEstimate ?: return@synchronized false
            applyLengthUpdateLocked(index, estimate, chapterIndex, actualLength, contentHash)
        }
        if (changed) onStateChanged()
    }

    fun getState(chapterIndex: Int, localPageIndex: Int): WholeBookPageState? =
        synchronized(lock) {
            pageIndex?.getState(chapterIndex, localPageIndex)
        }

    fun clear() {
        synchronized(lock) {
            generation++
            estimateJob?.cancel()
            estimateJob = null
            pageIndex = null
            activeEstimate = null
            pendingCorrections.clear()
            pendingLengthUpdates.clear()
        }
    }

    private data class PendingCorrection(
        val chapterIndex: Int,
        val realPageCount: Int,
        val generation: Long,
    )

    private data class PendingLengthUpdate(
        val chapterIndex: Int,
        val actualLength: Int,
        val contentHash: Long?,
        val generation: Long,
    )

    private class ActiveEstimate(
        val generation: Long,
        val bookId: String,
        val layoutSignature: Long,
        val bookBucket: Long,
        val layoutBucket: Long,
        val calibration: PageEstimateCalibration,
        val config: PageEstimateConfig,
        val estimator: ChapterPageEstimator,
        val fallbackContentLength: Int,
        val chapters: MutableList<ChapterLengthInfo>,
        val rawEstimates: FloatArray,
    )

    private fun applyLengthUpdateLocked(
        pageIndex: WholeBookPageIndex,
        estimate: ActiveEstimate,
        chapterIndex: Int,
        actualLength: Int,
        contentHash: Long? = null,
    ): Boolean {
        val chapter = estimate.chapters.getOrNull(chapterIndex) ?: return false
        if (chapter.contentLength == actualLength &&
            (contentHash == null || chapter.contentHash == contentHash)
        ) return false

        val updatedChapter = chapter.copy(
            contentLength = actualLength,
            contentHash = contentHash ?: chapter.contentHash,
        )
        estimate.chapters[chapterIndex] = updatedChapter
        val rawEstimate = estimate.estimator.estimate(
            updatedChapter.toInput(estimate.config, estimate.fallbackContentLength)
        )
        estimate.rawEstimates[chapterIndex] = rawEstimate
        if (pageIndex.isExact(chapterIndex)) {
            if (contentHash == null || contentHash == chapter.contentHash) return false
            scope.launch(Dispatchers.IO) {
                runCatching {
                    exactPageCountStore.deleteChapter(estimate.bookId, chapter.chapterId)
                }
            }
            return pageIndex.invalidateExact(
                chapterIndex,
                estimate.calibration.apply(rawEstimate),
            )
        }
        return pageIndex.updateEstimatedPageCount(
            chapterIndex,
            estimate.calibration.apply(rawEstimate),
        )
    }

    private fun applyCorrectionLocked(
        pageIndex: WholeBookPageIndex,
        estimate: ActiveEstimate,
        chapterIndex: Int,
        realPageCount: Int,
    ): Boolean {
        val estimatedPages = pageIndex.getChapterPageCount(chapterIndex) ?: return false
        val firstExactResult = !pageIndex.isExact(chapterIndex)
        val correctedPages = realPageCount.coerceAtLeast(1)
        val changed = pageIndex.correct(chapterIndex, correctedPages)
        if (!firstExactResult) return changed

        val chapter = estimate.chapters.getOrNull(chapterIndex) ?: return changed
        val rawEstimate = estimate.rawEstimates.getOrNull(chapterIndex) ?: return changed
        val calibrationBefore = calibrationStore.get(estimate.bookBucket)
        // 逐书桶用来估这本书自己的段落密度，排版级桶用来给下一本新书做冷启动先验。
        val updatedCalibration = calibrationStore.record(
            bucket = estimate.bookBucket,
            estimatedPages = rawEstimate,
            realPages = correctedPages,
        )
        calibrationStore.record(
            bucket = estimate.layoutBucket,
            estimatedPages = rawEstimate,
            realPages = correctedPages,
        )
        val absoluteError = abs(correctedPages - estimatedPages)
        metrics.record(
            PageEstimateMetric.ChapterCorrected(
                generation = estimate.generation,
                layoutSignature = estimate.layoutSignature,
                chapterId = chapter.chapterId,
                chapterIndex = chapterIndex,
                contentLength = chapter.contentLength ?: -1,
                rawEstimate = rawEstimate,
                estimatedPages = estimatedPages,
                realPages = correctedPages,
                absoluteError = absoluteError,
                relativeError = absoluteError.toFloat() / correctedPages,
                calibrationBefore = calibrationBefore,
                calibrationAfter = updatedCalibration,
            )
        )
        chapter.contentHash?.let { contentHash ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    exactPageCountStore.save(
                        ExactChapterPageCount(
                            bookId = estimate.bookId,
                            chapterId = chapter.chapterId,
                            chapterIndex = chapterIndex,
                            contentHash = contentHash,
                            layoutSignature = estimate.layoutSignature,
                            engineVersion = estimate.config.textEngineVersion,
                            pageCount = correctedPages,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }
        return changed
    }

    private companion object {
        const val DEFAULT_CHAPTER_LENGTH = 2_000

        /** 逐书桶 = 书 × 排版，改字号后本书的旧拟合自然失效。 */
        fun bookCalibrationBucket(bookId: String, layoutBucket: Long): Long =
            layoutBucket * 1099511628211L xor bookId.hashCode().toLong()
    }
}
