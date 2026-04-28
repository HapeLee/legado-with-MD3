package io.legado.app.model.cache

class CacheDownloadQueue {

    private data class RangeCursor(
        val start: Int,
        val end: Int,
        var next: Int = start,
    ) {
        fun contains(index: Int): Boolean = index in next..end
        fun remainingCount(
            emittedIndices: Set<Int>,
            removedIndices: Set<Int>,
        ): Int {
            if (next > end) return 0
            val rawCount = end - next + 1
            val emittedCount = emittedIndices.count { it in next..end }
            val removedCount = removedIndices.count { it in next..end && it !in emittedIndices }
            val excludedCount = emittedCount + removedCount
            return rawCount - excludedCount
        }
    }

    private val ranges = ArrayDeque<RangeCursor>()
    private val indices = linkedSetOf<Int>()
    private val emittedIndices = hashSetOf<Int>()
    private val removedIndices = hashSetOf<Int>()

    fun enqueue(request: CacheDownloadRequest) {
        enqueue(request.selection)
    }

    fun enqueue(selection: ChapterSelection) {
        when (selection) {
            is ChapterSelection.Range -> addRange(selection.start, selection.end)
            is ChapterSelection.Indices -> addIndices(selection.values)
            is ChapterSelection.Single -> addIndex(selection.index)
        }
    }

    fun next(bookUrl: String, runningIndices: Set<Int>): CacheDownloadCandidate? {
        while (indices.isNotEmpty()) {
            val index = indices.first()
            indices.remove(index)
            if (index in runningIndices || index in removedIndices) continue
            emittedIndices.add(index)
            return CacheDownloadCandidate(bookUrl, index)
        }

        while (ranges.isNotEmpty()) {
            val cursor = ranges.first()
            while (cursor.next <= cursor.end) {
                val index = cursor.next++
                if (index in removedIndices || index in runningIndices) continue
                if (emittedIndices.add(index)) {
                    return CacheDownloadCandidate(bookUrl, index)
                }
            }
            ranges.removeFirst()
        }
        return null
    }

    fun removeChapter(index: Int): Boolean {
        val removed = indices.remove(index) || isWaiting(index)
        removedIndices.add(index)
        return removed
    }

    fun clear() {
        ranges.clear()
        indices.clear()
        emittedIndices.clear()
        removedIndices.clear()
    }

    fun snapshot(): CacheDownloadQueueSnapshot {
        return CacheDownloadQueueSnapshot(waitingCount = waitingCount())
    }

    fun waitingCount(): Int {
        val indexCount = indices.count { it !in emittedIndices && it !in removedIndices }
        val rangeCount = ranges.sumOf { it.remainingCount(emittedIndices, removedIndices) }
        return indexCount + rangeCount
    }

    fun isWaiting(index: Int): Boolean {
        if (index in emittedIndices || index in removedIndices) return false
        return indices.contains(index) || ranges.any { it.contains(index) }
    }

    fun waitingIndices(): Set<Int> {
        return buildSet {
            indices.filterTo(this) { it !in emittedIndices && it !in removedIndices }
            ranges.forEach { cursor ->
                for (index in cursor.next..cursor.end) {
                    if (index !in emittedIndices && index !in removedIndices) add(index)
                }
            }
        }
    }

    private fun addRange(start: Int, end: Int) {
        if (end < start) return
        removedIndices.removeAll { it in start..end }
        ranges.add(RangeCursor(start, end))
    }

    private fun addIndices(values: Iterable<Int>) {
        values.forEach { addIndex(it) }
    }

    private fun addIndex(index: Int) {
        emittedIndices.remove(index)
        removedIndices.remove(index)
        indices.add(index)
    }
}
