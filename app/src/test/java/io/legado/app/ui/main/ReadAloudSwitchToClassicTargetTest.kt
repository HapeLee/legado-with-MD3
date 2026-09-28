package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 听书播放弹层「经典控制」按钮的目标判定。
 *
 * 规则：栈顶是阅读界面 → 把请求交给它并打开经典朗读控制；否则 → 打开阅读界面。
 * 播放弹层是全局浮层、不在导航栈上，所以判据是「当前顶层」而不是「上一站」。
 */
class ReadAloudSwitchToClassicTargetTest {

    @Test
    fun `hands request to existing reader when reader is on top`() {
        val backStack: List<NavKey> = listOf(
            MainRouteHome,
            MainRouteReadBook(bookUrl = "book://a"),
        )

        assertTrue(isReaderOnTop(backStack))
    }

    @Test
    fun `opens reader when home is on top`() {
        val backStack: List<NavKey> = listOf(MainRouteHome)

        assertFalse(isReaderOnTop(backStack))
    }

    @Test
    fun `opens reader when stack is empty`() {
        val backStack: List<NavKey> = emptyList()

        assertFalse(isReaderOnTop(backStack))
    }

    @Test
    fun `opens reader when reader is not the top entry`() {
        val backStack: List<NavKey> = listOf(
            MainRouteHome,
            MainRouteReadBook(bookUrl = "book://a"),
            MainRouteBookInfo(name = null, author = null, bookUrl = "book://a"),
        )

        assertFalse(isReaderOnTop(backStack))
    }
}
