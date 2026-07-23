package io.legado.app.ui.book.read.pageestimate

class WholeBookPageIndex(
    private val chapterCount: Int,
) {
    private var cumulativePages: IntArray? = null
    private val exactFlags = BooleanArray(chapterCount)
    private var exactChapterCount = 0
    private var firstInexactIndex = 0

    val isInitialized: Boolean
        get() = cumulativePages != null

    fun initialize(pageCounts: IntArray, exactChapters: Set<Int> = emptySet()) {
        require(pageCounts.size == chapterCount)
        var total = 0
        cumulativePages = IntArray(chapterCount) { index ->
            total += pageCounts[index].coerceAtLeast(1)
            total
        }
        exactFlags.fill(false)
        exactChapterCount = 0
        exactChapters.forEach { index ->
            if (index in exactFlags.indices && !exactFlags[index]) {
                exactFlags[index] = true
                exactChapterCount++
            }
        }
        firstInexactIndex = 0
        advanceFirstInexactIndex()
    }

    fun correct(chapterIndex: Int, realPageCount: Int): Boolean {
        val pages = cumulativePages ?: return false
        if (chapterIndex !in pages.indices) return false

        val pageOffset = if (chapterIndex == 0) 0 else pages[chapterIndex - 1]
        val oldPageCount = pages[chapterIndex] - pageOffset
        val correctedPageCount = realPageCount.coerceAtLeast(1)
        val delta = correctedPageCount - oldPageCount
        val becameExact = !exactFlags[chapterIndex]
        if (becameExact) {
            exactFlags[chapterIndex] = true
            exactChapterCount++
            if (chapterIndex == firstInexactIndex) advanceFirstInexactIndex()
        }
        if (delta != 0) {
            for (index in chapterIndex until pages.size) {
                pages[index] += delta
            }
        }
        return becameExact || delta != 0
    }

    fun updateEstimatedPageCount(chapterIndex: Int, estimatedPageCount: Int): Boolean {
        val pages = cumulativePages ?: return false
        if (chapterIndex !in pages.indices || exactFlags[chapterIndex]) return false

        val pageOffset = if (chapterIndex == 0) 0 else pages[chapterIndex - 1]
        val oldPageCount = pages[chapterIndex] - pageOffset
        val delta = estimatedPageCount.coerceAtLeast(1) - oldPageCount
        if (delta == 0) return false

        for (index in chapterIndex until pages.size) {
            pages[index] += delta
        }
        return true
    }

    fun invalidateExact(chapterIndex: Int, estimatedPageCount: Int): Boolean {
        val pages = cumulativePages ?: return false
        if (chapterIndex !in pages.indices || !exactFlags[chapterIndex]) return false

        exactFlags[chapterIndex] = false
        exactChapterCount--
        firstInexactIndex = minOf(firstInexactIndex, chapterIndex)

        val pageOffset = if (chapterIndex == 0) 0 else pages[chapterIndex - 1]
        val oldPageCount = pages[chapterIndex] - pageOffset
        val delta = estimatedPageCount.coerceAtLeast(1) - oldPageCount
        if (delta != 0) {
            for (index in chapterIndex until pages.size) {
                pages[index] += delta
            }
        }
        return true
    }

    fun getChapterPageCount(chapterIndex: Int): Int? {
        val pages = cumulativePages ?: return null
        if (chapterIndex !in pages.indices) return null
        val pageOffset = if (chapterIndex == 0) 0 else pages[chapterIndex - 1]
        return pages[chapterIndex] - pageOffset
    }

    fun isExact(chapterIndex: Int): Boolean =
        chapterIndex in exactFlags.indices && exactFlags[chapterIndex]

    fun getState(chapterIndex: Int, localPageIndex: Int): WholeBookPageState? {
        val pages = cumulativePages ?: return null
        if (chapterIndex !in pages.indices) return null
        val pageOffset = if (chapterIndex == 0) 0 else pages[chapterIndex - 1]
        val chapterPageCount = pages[chapterIndex] - pageOffset
        val totalPages = pages.lastOrNull() ?: return null
        return WholeBookPageState(
            currentPage = (pageOffset + localPageIndex.coerceAtLeast(0) + 1)
                .coerceAtMost(totalPages),
            totalPages = totalPages,
            chapterPage = localPageIndex.coerceAtLeast(0) + 1,
            chapterPageCount = chapterPageCount,
            currentChapterExact = exactFlags[chapterIndex],
            allPreviousChaptersExact = chapterIndex <= firstInexactIndex,
            estimated = exactChapterCount < chapterCount,
        )
    }

    private fun advanceFirstInexactIndex() {
        while (firstInexactIndex < chapterCount && exactFlags[firstInexactIndex]) {
            firstInexactIndex++
        }
    }
}

data class WholeBookPageState(
    val currentPage: Int,
    val totalPages: Int,
    val chapterPage: Int,
    val chapterPageCount: Int,
    val currentChapterExact: Boolean,
    val allPreviousChaptersExact: Boolean,
    val estimated: Boolean,
)
