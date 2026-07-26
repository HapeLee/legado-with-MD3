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
            // indices 为显式排队：即使 range 上有 remove 孔也要出队
            if (index in runningIndices || emittedIndices.contains(index)) continue
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
        val indexSet = indices.filterNot { emittedIndices.contains(it) }.toHashSet()
        val rangeCount = if (ranges.size <= 1) {
            ranges.sumOf { cursor ->
                if (cursor.next > cursor.end) 0
                else {
                    var count = 0
                    var i = cursor.next
                    while (i <= cursor.end) {
                        if (
                            !emittedIndices.contains(i) &&
                            !removedIndices.contains(i) &&
                            i !in indexSet
                        ) {
                            count++
                        }
                        i++
                    }
                    count
                }
            }
        } else {
            val seen = mutableSetOf<Int>()
            ranges.forEach { cursor ->
                var i = cursor.next
                while (i <= cursor.end) {
                    if (
                        !emittedIndices.contains(i) &&
                        !removedIndices.contains(i) &&
                        i !in indexSet
                    ) {
                        seen.add(i)
                    }
                    i++
                }
            }
            seen.size
        }
        return indexSet.size + rangeCount
    }

    fun isWaiting(index: Int): Boolean {
        if (emittedIndices.contains(index)) return false
        // 显式 indices 优先：即使 range 上打了 remove 孔，仍视为等待
        if (indices.contains(index)) return true
        if (removedIndices.contains(index)) return false
        return ranges.any { it.contains(index) }
    }

    /**
     * 当前仍等待下载的章节（indices 在前，其次为 range 剩余），不消费队列。
     */
    fun waitingIndices(): List<Int> {
        val result = linkedSetOf<Int>()
        indices.forEach { index ->
            if (!emittedIndices.contains(index)) {
                result.add(index)
            }
        }
        ranges.forEach { cursor ->
            var i = cursor.next
            while (i <= cursor.end) {
                if (
                    !emittedIndices.contains(i) &&
                    !removedIndices.contains(i) &&
                    i !in result
                ) {
                    result.add(i)
                }
                i++
            }
        }
        return result.toList()
    }

    /**
     * 将章节提到队首（indices 优先于 range），供「点某一章优先下载」使用。
     */
    fun prioritize(index: Int) {
        // 从 range 挖孔，避免与 indices 双重计数；显式 indices 不受 removed 影响
        if (isInRange(index)) {
            removedIndices.add(index)
        }
        emittedIndices.remove(index)
        indices.remove(index)
        val rest = indices.toList()
        indices.clear()
        indices.add(index)
        rest.forEach { indices.add(it) }
    }

    private fun isInRange(index: Int): Boolean {
        return ranges.any { it.contains(index) }
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
        // 若该章仍落在某个 range 游标内，保留 removed 孔以免 waitingCount 双计；
        // 仅当不在 range 内时清除 removed（失败重试等已从 range 消费过的场景）。
        if (!isInRange(index)) {
            removedIndices.remove(index)
        } else {
            removedIndices.add(index)
        }
        indices.add(index)
    }
}
