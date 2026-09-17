package io.legado.app.feature.reader.core.transition

enum class ReaderAutoPageVisualMode {
    PROGRESSIVE,
    DISCRETE,
}

/** Keeps e-ink auto paging free of continuous frame updates, matching the legacy reader. */
object ReaderAutoPagePolicy {
    fun visualMode(isEInkMode: Boolean): ReaderAutoPageVisualMode =
        if (isEInkMode) ReaderAutoPageVisualMode.DISCRETE else ReaderAutoPageVisualMode.PROGRESSIVE

    fun pageDurationMillis(speedSeconds: Int): Long =
        speedSeconds.coerceIn(1, 120) * 1_000L

    /** Top edge for the legacy one-pixel reveal indicator. */
    fun indicatorTopPx(progressPx: Float, viewportHeightPx: Float): Float =
        (progressPx.coerceIn(0f, viewportHeightPx.coerceAtLeast(0f)) - 1f).coerceAtLeast(0f)
}
