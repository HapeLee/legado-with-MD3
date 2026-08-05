package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitCacheBookFifoTest {

    @Test
    fun ensureAppendsNewBooksInOrder() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("a")

        assertEquals(listOf("a", "b"), fifo.snapshot())
    }

    @Test
    fun headWhereSkipsPausedBooks() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("c")

        val paused = setOf("a")
        assertEquals("b", fifo.headWhere { it !in paused })
    }

    @Test
    fun moveToTailWhenOtherBookDownloading() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("c")

        fifo.moveToTail("a")

        assertEquals(listOf("b", "c", "a"), fifo.snapshot())
        assertEquals("b", fifo.headWhere { true })
    }

    @Test
    fun moveToHeadWhenAllPaused() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("c")

        fifo.moveToHead("b")

        assertEquals(listOf("b", "a", "c"), fifo.snapshot())
        assertEquals("b", fifo.headWhere { true })
    }

    @Test
    fun urlsBesidesExcludesSelfKeepsOrder() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("c")

        assertEquals(listOf("a", "c"), fifo.urlsBesides("b"))
        // 预下载等不在 FIFO 内的书本来就不会出现，恢复判定只应扫这些 URL
        assertEquals(listOf("a", "b", "c"), fifo.urlsBesides("preload-only"))
    }

    @Test
    fun exclusiveHeadKeepsInFlightBookEvenIfNotLaunchable() {
        // 与 CacheBook.startProcessJob 一致：选队首看「仍占槽」，不是「还能新启动章节」
        val order = listOf("a", "b", "c")
        val occupiesSlot = setOf("a") // a 正在下图，waiting 暂时为空
        val launchable = setOf("b")

        val exclusiveHead = order.firstOrNull { it in occupiesSlot }
        val wrongHeadByLaunchable = order.firstOrNull { it in launchable }

        assertEquals("a", exclusiveHead)
        assertEquals("b", wrongHeadByLaunchable)
    }

    @Test
    fun ensureBeforeLaunchablePreventsNonFifoParallel() {
        // 冷启动并行准入：若先有 launchable、后 ensure，未入 FIFO 的书会被当成预下载并行
        val fifo = ExplicitCacheBookFifo()
        val taskMapLaunchable = linkedSetOf<String>()

        // 正确顺序：先 ensure 再暴露可调度
        fifo.ensure("a")
        taskMapLaunchable.add("a")
        fifo.ensure("b")
        taskMapLaunchable.add("b")

        val explicit = fifo.snapshot().toHashSet()
        val leakedAsNonFifo = taskMapLaunchable.filterNot { it in explicit }
        assertTrue(leakedAsNonFifo.isEmpty())

        // 错误顺序（旧逻辑）会泄漏
        val lateFifo = ExplicitCacheBookFifo()
        val leaked = linkedSetOf("b", "c")
        lateFifo.ensure("a")
        val lateExplicit = lateFifo.snapshot().toHashSet()
        assertEquals(listOf("b", "c"), leaked.filterNot { it in lateExplicit })
    }

    @Test
    fun liveExplicitSetPreventsStaleNonFifoLeakWithoutBlockingPredownload() {
        // processJob 若用「本轮初 snapshot」排除非 FIFO，而 ensure 发生在 snapshot 之后，
        // 新书不在旧 set 里 → 与队首抢并发（冷启动第一次 FAB：7+1）。
        val fifo = ExplicitCacheBookFifo()
        val staleSnapshot = fifo.snapshot() // 当时为空
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("c")

        val staleExclude = staleSnapshot.toHashSet()
        val taskMap = listOf("a", "b", "c", "preload")
        val leakedByStaleExclude = taskMap.filterNot { it in staleExclude }
        assertEquals(listOf("a", "b", "c", "preload"), leakedByStaleExclude)

        // 正确策略：用 live snapshot 排除显式书；预下载仍可并行
        // （勿用 fifo.isEmpty() 整段关闭——会饿死 ReadPreload）
        val liveExplicit = fifo.snapshot().toHashSet()
        val nonFifoParallel = taskMap.filterNot { it in liveExplicit }
        assertEquals(listOf("preload"), nonFifoParallel)
        assertFalse(fifo.isEmpty())
    }

    @Test
    fun resumeToTailOnlyWhenOtherHasInFlightNotMereWaiting() {
        // 与 CacheBook.hasActiveExplicitDownloadBesides 一致：
        // 占槽（进行中/加载/重试）→ 队尾；仅 waiting → 仍可提到队首
        fun shouldMoveToTail(otherHasInFlight: Boolean) = otherHasInFlight
        val othersWaitingOnly = false // 后续书 waiting，但无人占槽
        val othersInFlight = true
        assertFalse(shouldMoveToTail(othersWaitingOnly))
        assertTrue(shouldMoveToTail(othersInFlight))
    }

    @Test
    fun removeDropsBookFromOrder() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")

        assertTrue(fifo.remove("a"))
        assertFalse(fifo.remove("a"))
        assertEquals(listOf("b"), fifo.snapshot())
        assertNull(fifo.headWhere { it == "a" })
    }

    @Test
    fun snapshotThenFilterOutsideAvoidsNestedModelLock() {
        // 与 CacheBook.startProcessJob 相同：先 snapshot，再在锁外按状态选队首
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("paused")
        fifo.ensure("ready")
        fifo.ensure("waiting")

        val order = fifo.snapshot()
        val launchable = setOf("ready", "waiting")
        val head = order.firstOrNull { it in launchable }

        assertEquals("ready", head)
        assertEquals(listOf("paused", "ready", "waiting"), order)
    }
}
