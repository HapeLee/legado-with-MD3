package io.legado.app.model.cache

class CacheDownloadQueue {

    private class IntRangeSet {
        private val ranges = mutableListOf<IntRange>()

        fun contains(value: Int): Boolean {
            return ranges.any { value in it }
        }

        fun add(value: Int) {
            addRange(value, value)
        }

        fun addRange(start: Int, end: Int) {
            if (end < start) return
            var newStart = start
            var newEnd = end
            var index = 0
            while (index < ranges.size) {
                val range = ranges[index]
                if (newEnd + 1 < range.first) break
                if (newStart > range.last + 1) {
                    index++
                    continue
                }
                newStart = minOf(newStart, range.first)
                newEnd = maxOf(newEnd, range.last)
                ranges.removeAt(index)
            }
            ranges.add(index, newStart..newEnd)
        }

        fun remove(value: Int) {
            val index = ranges.indexOfFirst { value in it }
            if (index < 0) return
            val range = ranges.removeAt(index)
            if (range.first < value) {
                ranges.add(index, range.first until value)
            }
            if (value < range.last) {
                ranges.add(index + if (range.first < value) 1 else 0, value + 1..range.last)
            }
        }

        fun removeRange(start: Int, end: Int) {
            if (end < start) return
            var index = 0
            while (index < ranges.size) {
                val range = ranges[index]
                if (range.last < start) {
                    index++
                    continue
                }
                if (range.first > end) break
                ranges.removeAt(index)
                if (range.first < start) {
                    ranges.add(index, range.first until start)
                    index++
                }
                if (end < range.last) {
                    ranges.add(index, end + 1..range.last)
                    break
                }
            }
        }

        fun clear() {
            ranges.clear()
        }

        fun countInRange(start: Int, end: Int, excluding: IntRangeSet? = null): Int {
            if (end < start) return 0
            var count = 0
            ranges.forEach { range ->
                val overlapStart = maxOf(start, range.first)
                val overlapEnd = minOf(end, range.last)
                if (overlapEnd >= overlapStart) {
                    count += overlapEnd - overlapStart + 1
                    if (excluding != null) {
                        count -= excluding.countInRange(overlapStart, overlapEnd)
                    }
                }
            }
            return count
        }
    }

    private data class RangeCursor(
        val start: Int,
        val end: Int,
        var next: Int = start,
    ) {
        fun contains(index: Int): Boolean = index in next..end
        fun remainingCount(
            emittedIndices: IntRangeSet,
            removedIndices: IntRangeSet,
        ): Int {
            if (next > end) return 0
            val rawCount = end - next + 1
            val emittedCount = emittedIndices.countInRange(next, end)
            val removedCount = removedIndices.countInRange(next, end, excluding = emittedIndices)
            val excludedCount = emittedCount + removedCount
            return rawCount - excludedCount
        }
    }

    private val ranges = ArrayDeque<RangeCursor>()
    private val indices = linkedSetOf<Int>()
    private val emittedIndices = IntRangeSet()
    private val removedIndices = IntRangeSet()

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
            if (index in runningIndices || removedIndices.contains(index)) continue
            emittedIndices.add(index)
            return CacheDownloadCandidate(bookUrl, index)
        }

        while (ranges.isNotEmpty()) {
            val cursor = ranges.first()
            while (cursor.next <= cursor.end) {
                val index = cursor.next++
                if (
                    removedIndices.contains(index) ||
                    emittedIndices.contains(index) ||
                    index in runningIndices
                ) {
                    continue
                }
                emittedIndices.add(index)
                return CacheDownloadCandidate(bookUrl, index)
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
        val indexCount = indices.count {
            !emittedIndices.contains(it) && !removedIndices.contains(it)
        }
        val rangeCount = ranges.sumOf { it.remainingCount(emittedIndices, removedIndices) }
        return indexCount + rangeCount
    }

    fun isWaiting(index: Int): Boolean {
        if (emittedIndices.contains(index) || removedIndices.contains(index)) return false
        return indices.contains(index) || ranges.any { it.contains(index) }
    }

    private fun addRange(start: Int, end: Int) {
        if (end < start) return
        emittedIndices.removeRange(start, end)
        removedIndices.removeRange(start, end)
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
