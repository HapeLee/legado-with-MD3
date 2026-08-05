package io.legado.app.ui.book.read.pageestimate

sealed interface PageEstimateMetric {
    val generation: Long
    val layoutSignature: Long

    data class EstimateCompleted(
        override val generation: Long,
        override val layoutSignature: Long,
        val calibration: PageEstimateCalibration,
        val chapterCount: Int,
        val knownLengthCount: Int,
        val totalPages: Int,
        val durationMillis: Long,
    ) : PageEstimateMetric

    data class ChapterCorrected(
        override val generation: Long,
        override val layoutSignature: Long,
        val chapterId: String,
        val chapterIndex: Int,
        val contentLength: Int,
        /** 未校准的连续估算值，也就是最小二乘的 x。 */
        val rawEstimate: Float,
        val estimatedPages: Int,
        val realPages: Int,
        val absoluteError: Int,
        val relativeError: Float,
        val calibrationBefore: PageEstimateCalibration,
        val calibrationAfter: PageEstimateCalibration,
    ) : PageEstimateMetric
}

fun interface PageEstimateMetrics {
    fun record(metric: PageEstimateMetric)

    companion object {
        val None = PageEstimateMetrics {}
    }
}

/**
 * 仅在当前进程内保留最近的诊断记录，供用户主动导出；不落盘、不上传。
 */
object LocalPageEstimateMetrics : PageEstimateMetrics {
    private const val MAX_ENTRIES = 200
    private val entries = ArrayDeque<TimedPageEstimateMetric>()

    @Synchronized
    override fun record(metric: PageEstimateMetric) {
        if (entries.size == MAX_ENTRIES) entries.removeFirst()
        entries.addLast(TimedPageEstimateMetric(System.currentTimeMillis(), metric))
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun export(): String = buildString {
        appendLine("Legado whole-book page diagnostics")
        appendLine("localOnly=true\tentryCount=${entries.size}")
        appendLine("time\ttype\tgeneration\testimator\tlayoutSignature\tdetails")
        entries.forEach { entry ->
            val metric = entry.metric
            append(entry.recordedAt)
            append('\t')
            append(if (metric is PageEstimateMetric.EstimateCompleted) "estimate" else "correction")
            append('\t').append(metric.generation)
            append('\t').append(metric.layoutSignature.toULong().toString(16))
            append('\t').append(metric.details())
            appendLine()
        }
    }

    @Synchronized
    internal fun clear() {
        entries.clear()
    }
}

private data class TimedPageEstimateMetric(
    val recordedAt: Long,
    val metric: PageEstimateMetric,
)

private fun PageEstimateMetric.details(): String = when (this) {
    is PageEstimateMetric.EstimateCompleted -> buildString {
        append("chapters=").append(chapterCount)
        append(",knownLengths=").append(knownLengthCount)
        append(",totalPages=").append(totalPages)
        append(",durationMs=").append(durationMillis)
        append(",slope=").append(calibration.slope)
        append(",intercept=").append(calibration.intercept)
        append(",samples=").append(calibration.sampleCount)
        append(",fitted=").append(calibration.fitted)
    }

    is PageEstimateMetric.ChapterCorrected -> buildString {
        append("chapterIndex=").append(chapterIndex)
        append(",contentLength=").append(contentLength)
        append(",rawEstimate=").append(rawEstimate)
        append(",estimatedPages=").append(estimatedPages)
        append(",realPages=").append(realPages)
        append(",absoluteError=").append(absoluteError)
        append(",relativeError=").append(relativeError)
        append(",slopeBefore=").append(calibrationBefore.slope)
        append(",slopeAfter=").append(calibrationAfter.slope)
        append(",samplesAfter=").append(calibrationAfter.sampleCount)
    }
}
