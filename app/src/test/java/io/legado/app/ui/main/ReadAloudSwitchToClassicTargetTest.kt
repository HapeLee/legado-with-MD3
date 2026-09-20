package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 听书播放页「经典控制」按钮的目标判定。
 *
 * 规则：上一站是阅读界面 → 回到已有阅读界面并打开经典朗读控制；否则 → 打开阅读界面。
 * 之前该按钮发出的 `ReturnToClassic` 效果没有任何收集方，点了没反应。
 */
class ReadAloudSwitchToClassicTargetTest {

    @Test
    fun `returns to existing reader when immediate parent is read book`() {
        val backStack: List<NavKey> = listOf(
            MainRouteHome,
            MainRouteReadBook(bookUrl = "book://a"),
            MainRouteReadAloudPlayer,
        )

        assertTrue(hasReadBookParent(backStack))
    }

    @Test
    fun `opens reader when parent is home`() {
        val backStack: List<NavKey> = listOf(MainRouteHome, MainRouteReadAloudPlayer)

        assertFalse(hasReadBookParent(backStack))
    }

    @Test
    fun `opens reader when player is the only entry`() {
        val backStack: List<NavKey> = listOf(MainRouteReadAloudPlayer)

        assertFalse(hasReadBookParent(backStack))
    }

    @Test
    fun `opens reader when read book is not the immediate parent`() {
        val backStack: List<NavKey> = listOf(
            MainRouteHome,
            MainRouteReadBook(bookUrl = "book://a"),
            MainRouteBookInfo(name = null, author = null, bookUrl = "book://a"),
            MainRouteReadAloudPlayer,
        )

        assertFalse(hasReadBookParent(backStack))
    }
}
