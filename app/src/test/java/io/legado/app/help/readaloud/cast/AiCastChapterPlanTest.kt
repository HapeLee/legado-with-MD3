package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AI 分配「跑哪几章」的计划（[castChapterPlan]）。
 *
 * 这里锁死三件事，因为它们直接决定悬浮窗两个新控件是不是真开关：
 * 1. 「指定范围」给的起止章（0 基、含两端）就是实际跑的章，越界只夹到目录末尾；
 * 2. 「追加章节数」模式仍是「当前章 + N」，与改动前逐字一致；
 * 3. 重试清单只跑点名的章（失败的那几章），不会把整段范围再来一遍，也不跑目录外的章。
 */
class AiCastChapterPlanTest {

    @Test
    fun `explicit range is used inclusive and clamped to the toc`() {
        assertEquals(listOf(4, 5, 6), castChapterPlan(100, 4, 1, 6, emptyList()))
        // 终点填过书尾：夹回最后一章，不报错也不静默少跑
        assertEquals(listOf(98, 99), castChapterPlan(100, 98, 1, 500, emptyList()))
    }

    @Test
    fun `extra chapters mode keeps casting current chapter plus n`() {
        assertEquals(listOf(7), castChapterPlan(100, 7, 1, -1, emptyList()))
        assertEquals(listOf(7, 8, 9), castChapterPlan(100, 7, 3, -1, emptyList()))
    }

    @Test
    fun `retry list wins over the range and drops chapters outside the toc`() {
        val plan = castChapterPlan(100, 0, 50, 99, listOf(9, 3, 3, 250))
        assertEquals(listOf(3, 9), plan)
    }

    @Test
    fun `empty toc has nothing to cast`() {
        assertEquals(emptyList<Int>(), castChapterPlan(0, 0, 5, 4, emptyList()))
    }

    @Test
    fun `a failure without a message still tells a reason`() {
        // 断网/超时之外的异常常常没有 message，只写 ${it.message} 给用户的就是「null」
        assertEquals("SocketTimeoutException", failureReason(java.net.SocketTimeoutException()))
        assertEquals("连不上", failureReason(IllegalStateException("连不上")))
    }
}
