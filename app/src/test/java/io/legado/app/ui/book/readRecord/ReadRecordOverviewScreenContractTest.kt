package io.legado.app.ui.book.readRecord

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 阅读总览顶栏与正文的采样边界。
 *
 * Miuix 的 `AppScaffold` 把 `LocalMiuixBarBackdrop` 提供给整棵树，正文 Box 就是这份
 * layer backdrop 的录制源。玻璃胶囊（`AppTabRow`）一旦落进正文，采样源就成了自己的
 * 子节点，hwui prepareTree 无限递归直到 RenderThread 栈溢出，Java 层拿不到堆栈。
 * 正文若整体按顶栏高度 `.padding(...)`，顶栏后面又没有内容可采样，模糊消失。
 */
class ReadRecordOverviewScreenContractTest {

    @Test
    fun `period and date controls live in top bar bottomContent`() {
        val screen = screenSource()
        val topBar = screen.substringAfter("topBar = {", "")
            .substringBefore(") { padding ->", "")
        val bottomContent = topBar.substringAfter("bottomContent = {", "")

        assertTrue("顶栏必须提供 bottomContent 槽位", bottomContent.isNotEmpty())
        assertTrue("周期切换必须画在顶栏槽位（不在采样源子树内）", "PeriodSelector(" in bottomContent)
        assertTrue("日期切换必须画在顶栏槽位", "DateNavigator(" in bottomContent)
        assertFalse("正文不得再放周期切换", "PeriodSelector(" in contentOf(screen))
        assertFalse("正文不得再放日期切换", "DateNavigator(" in contentOf(screen))
    }

    @Test
    fun `content scrolls behind top bar and reports its scroll state`() {
        val screen = screenSource()
        val content = contentOf(screen)

        assertTrue(
            "顶栏渐进模糊需要列表上报滚动状态，参照 BookshelfScreen / SearchScreen",
            "isContentScrolled = listState.canScrollBackward" in screen
        )
        assertTrue(
            "顶栏高度要作为 LazyColumn 的 contentPadding，卡片才能滚到顶栏后面被采样",
            "contentPadding = adaptiveContentPaddingOnlyVertical(" in content
        )
        assertTrue("顶部间距必须来自 scaffold padding", "top = padding.calculateTopPadding()" in content)
    }

    private fun screenSource(): String {
        val screen = sourceOf("io/legado/app/ui/book/readRecord/ReadRecordOverviewScreen.kt")
            .substringAfter("fun ReadRecordOverviewScreen(", "")
            .substringBefore("\nfun PeriodSelector(", "")
        assertTrue("找不到 ReadRecordOverviewScreen 的函数体", screen.isNotEmpty())
        return screen
    }

    private fun contentOf(screen: String): String {
        val content = screen.substringAfter(") { padding ->", "")
        assertTrue("找不到 AppScaffold 的正文 lambda", content.isNotEmpty())
        return content
    }

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }
}
