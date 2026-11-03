package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [CacheDownloadQueue] 的等待数/可启动判定与「逐章遍历」参考实现对拍。
 *
 * 原实现的等待数按剩余章节逐章扫：整书缓存时每章成功都要重算好几次，退化成 O(N²)。
 * 改成按区间算之后，这里拿一个直白的参考实现（每次真的把所有剩余位置数一遍）随机对拍，
 * 保证等待数、可启动判定、出队顺序三者与旧语义完全一致。
 */
class CacheDownloadQueueParityTest {

    private val universe = 0..39

    @Test
    fun randomOperationsKeepQueueEquivalentToReference() {
        val random = Random(20261104)
        val queue = CacheDownloadQueue()
        val reference = ReferenceQueue()

        repeat(120) {
            repeat(40) {
                val running = randomRunningSet(random)
                when (random.nextInt(10)) {
                    0, 1 -> {
                        val start = random.nextInt(40)
                        val end = start + random.nextInt(8)
                        queue.enqueue(ChapterSelection.Range(start, end))
                        reference.enqueueRange(start, end)
                    }

                    2 -> {
                        val values = List(random.nextInt(6)) { random.nextInt(40) }.toSet()
                        queue.enqueue(ChapterSelection.Indices(values))
                        values.forEach(reference::enqueueIndex)
                    }

                    3 -> {
                        val index = random.nextInt(40)
                        queue.enqueue(ChapterSelection.Single(index))
                        reference.enqueueIndex(index)
                    }

                    4, 5 -> {
                        val actual = queue.next("book", running)?.chapterIndex
                        val expected = reference.next(running)
                        assertEquals("next() 出队顺序不一致", expected, actual)
                    }

                    6 -> {
                        val index = random.nextInt(40)
                        assertEquals(
                            "removeChapter($index) 返回值不一致",
                            reference.removeChapter(index),
                            queue.removeChapter(index),
                        )
                    }

                    7 -> {
                        val index = random.nextInt(40)
                        queue.prioritize(index)
                        reference.prioritize(index)
                    }

                    8 -> {
                        assertEquals("waitingCount 不一致", reference.waitingCount(), queue.waitingCount())
                        assertEquals(
                            "hasLaunchableChapter 不一致",
                            reference.hasLaunchable(running),
                            queue.hasLaunchableChapter(running),
                        )
                    }

                    else -> {
                        if (random.nextInt(15) == 0) {
                            queue.clear()
                            reference.clear()
                        }
                    }
                }

                assertEquals(
                    "waitingCount 不一致（running=$running）",
                    reference.waitingCount(),
                    queue.waitingCount(),
                )
                assertEquals(
                    "hasLaunchableChapter 不一致（running=$running）",
                    reference.hasLaunchable(running),
                    queue.hasLaunchableChapter(running),
                )
                assertEquals(
                    "waitingIndices 不一致",
                    reference.waitingIndices(),
                    queue.waitingIndices().toSet(),
                )
                if (random.nextInt(4) == 0) {
                    universe.forEach { index ->
                        assertEquals(
                            "isWaiting($index) 不一致",
                            reference.isWaiting(index),
                            queue.isWaiting(index),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun waitingCountScalesWithRangesNotChapters() {
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 299_999))
        assertEquals(300_000, queue.waitingCount())

        // 旧实现这里是 300_000 × 10_000 次逐章判断；区间实现应在毫秒级完成。
        val startNanos = System.nanoTime()
        repeat(10_000) { queue.waitingCount() }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        assertTrue("30 万章区间算 1 万次等待数耗时 ${elapsedMs}ms，说明又退回逐章扫描", elapsedMs < 2_000)
    }

    @Test
    fun hasLaunchableChapterIgnoresHolesAndEmitted() {
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 9))
        repeat(10) { queue.next("book", emptySet()) }
        assertTrue(!queue.hasLaunchableChapter(emptySet()))
        assertEquals(0, queue.waitingCount())

        queue.removeChapter(5)
        assertEquals(0, queue.waitingCount())
        assertTrue(!queue.hasLaunchableChapter(emptySet()))
    }

    @Test
    fun runningChaptersInsideRangeStillCountAsWaiting() {
        // 再次请求正在下载的章会被塞回 indices 等复核，不能被当成「已下发」而从等待数里消失。
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 4))
        assertEquals(0, queue.next("book", emptySet())?.chapterIndex)
        assertEquals(4, queue.waitingCount())
        assertTrue(queue.hasLaunchableChapter(emptySet()))
    }

    private fun randomRunningSet(random: Random): Set<Int> {
        val size = random.nextInt(4)
        return List(size) { random.nextInt(40) }.toSet()
    }

    /**
     * 参考实现：状态迁移照抄队列，但等待数/可启动判定老老实实逐章遍历。
     */
    private class ReferenceQueue {
        private class Cursor(val start: Int, val end: Int) {
            var next: Int = start
        }

        private val indices = LinkedHashSet<Int>()
        private val ranges = ArrayDeque<Cursor>()
        private val emitted = HashSet<Int>()
        private val removed = HashSet<Int>()

        fun enqueueRange(start: Int, end: Int) {
            if (end < start) return
            emitted.removeAll(start..end)
            removed.removeAll(start..end)
            ranges.add(Cursor(start, end))
        }

        fun enqueueIndex(index: Int) {
            emitted.remove(index)
            if (ranges.none { index >= it.next && index <= it.end }) removed.remove(index)
            else removed.add(index)
            indices.add(index)
        }

        fun isWaiting(index: Int): Boolean {
            if (index in emitted) return false
            if (index in indices) return true
            if (index in removed) return false
            return ranges.any { index >= it.next && index <= it.end }
        }

        fun removeChapter(index: Int): Boolean {
            val removedFlag = indices.remove(index) || isWaiting(index)
            removed.add(index)
            return removedFlag
        }

        fun prioritize(index: Int) {
            if (ranges.any { index >= it.next && index <= it.end }) removed.add(index)
            emitted.remove(index)
            indices.remove(index)
            val rest = indices.toList()
            indices.clear()
            indices.add(index)
            rest.forEach { indices.add(it) }
        }

        fun waitingCount(): Int {
            val indexSet = indices.filterNot { it in emitted }.toSet()
            val pending = HashSet<Int>()
            ranges.forEach { cursor ->
                for (index in cursor.next..cursor.end) {
                    if (index !in emitted && index !in removed && index !in indexSet) {
                        pending.add(index)
                    }
                }
            }
            return indexSet.size + pending.size
        }

        fun waitingIndices(): Set<Int> {
            val result = linkedSetOf<Int>()
            indices.forEach { if (it !in emitted) result.add(it) }
            ranges.forEach { cursor ->
                for (index in cursor.next..cursor.end) {
                    if (index !in emitted && index !in removed) result.add(index)
                }
            }
            return result
        }

        fun hasLaunchable(running: Set<Int>): Boolean =
            indices.any { it !in running && it !in emitted } ||
                ranges.any { cursor ->
                    (cursor.next..cursor.end).any {
                        it !in running && it !in removed && it !in emitted
                    }
                }

        fun next(running: Set<Int>): Int? {
            while (indices.isNotEmpty()) {
                val index = indices.firstOrNull { it !in running } ?: break
                indices.remove(index)
                if (index in emitted) continue
                emitted.add(index)
                return index
            }
            while (ranges.isNotEmpty()) {
                val cursor = ranges.first()
                while (cursor.next <= cursor.end) {
                    val index = cursor.next++
                    if (index in removed || index in emitted) continue
                    if (index in running) {
                        indices.add(index)
                        continue
                    }
                    emitted.add(index)
                    return index
                }
                ranges.removeFirst()
            }
            return null
        }

        fun clear() {
            indices.clear()
            ranges.clear()
            emitted.clear()
            removed.clear()
        }
    }
}
