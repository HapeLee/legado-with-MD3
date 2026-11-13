package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十四轮：`RssReadRouteScreen` 顶栏「更多」菜单收成一个受控 [AppActionMenu]。
 *
 * 这里原来是两处手画菜单：主菜单 + 一个独立的重定向策略菜单（`showRedirectMenu`），后者
 * 由主菜单里的「重定向策略」项打开、选中后要同时关掉两层菜单、并用 `Check` 图标表示当前值。
 * 迁移后：
 * - 只剩一个受控弹层，开合由 `showMenu` 驱动，`trigger = {}`，锚点是「更多」按钮所在 `Box`；
 * - 重定向策略变成 `AppAction.children`，当前值用 `AppAction.selected` 表达（Material 无级联，
 *   会平铺成同级项，属已知降级）；
 * - 条目动作集中在 `onMenuAction(id)` 局部函数里，`buildList` / `map` 的 lambda 不是
 *   `@Composable`，文案必须在外层求值；
 * - 「朗读 / 停止朗读」的文案与图标仍随 `isSpeaking` 变化，但 ID 稳定。
 *
 * 静态契约不证明弹层定位、四引擎外观与 TalkBack。
 */
class RssReadMenuContractTest {

    @Test
    fun overflowMenuIsSingleControlledAppActionMenu() {
        val source = codeOf(SCREEN)
        assertEquals("仍手画菜单", 0, Regex("RoundDropdownMenu").findAll(source).count())
        assertEquals(
            "应只剩一个受控弹层",
            1,
            Regex("AppActionMenu\\(").findAll(source).count(),
        )
        assertTrue("弹层未受控", source.contains("embeddedExpanded = showMenu"))
        assertTrue("受控弹层不应渲染 trigger", source.contains("trigger = {}"))
        assertTrue("锚点丢失", source.contains("modifier = Modifier.matchParentSize()"))
        assertFalse("独立的策略菜单开合状态应已删除", source.contains("showRedirectMenu"))
    }

    /** 六个命令项仍指向原来的回调与 intent。 */
    @Test
    fun commandItemsKeepTheirCallbacks() {
        val source = codeOf(SCREEN)
        listOf(
            "context.share(",
            "viewModel.rssArticle?.let(onOpenVideoPlayer)",
            "viewModel.stopReadAloud()",
            "MainActivity.createSourceLoginIntent(",
            "viewModel.updateRssSourceRedirectPolicy(",
            "context.openUrl(it)",
        ).forEach { call ->
            assertTrue("缺少动作：$call", source.contains(call))
        }
        assertTrue("播放视频项丢了 type==2 条件", source.contains("viewModel.rssArticle?.type == 2"))
        assertTrue("登录项丢了条件", source.contains("rssSource?.loginUrl.isNullOrBlank()"))
        assertTrue("朗读文案未随状态切换", source.contains("R.string.aloud_stop"))
        assertTrue("朗读文案未随状态切换", source.contains("R.string.read_aloud"))
    }

    @Test
    fun redirectPolicyBecomesChildItemsWithSelection() {
        val source = codeOf(SCREEN)
        assertTrue("策略未改为子项", source.contains("children = redirectActions"))
        assertTrue("子项缺少稳定前缀", source.contains("private const val RssReadMenuRedirectPrefix = \"rss_read_menu_redirect:\""))
        assertTrue("选中态未用 selected", source.contains("selected = policy == redirectPolicy"))
        assertTrue("策略枚举丢失", source.contains("RedirectPolicy.entries"))
        assertTrue("ID 未回解成策略", source.contains("\"\$RssReadMenuRedirectPrefix\${it.name}\" == id"))
    }

    /** 旧实现用 `trailingIcon = Check` 表示当前策略，迁移后不应再手画勾选图标。 */
    @Test
    fun selectionNoLongerDrawnWithCheckIcon() {
        assertFalse(
            "仍手画 Check 选中图标",
            codeOf(SCREEN).contains("Icons.Default.Check"),
        )
    }

    private fun codeOf(path: String): String = sourceOf(path)
        .lineSequence()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
        }
        .joinToString("\n")

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val SCREEN = "io/legado/app/ui/rss/read/RssReadRouteScreen.kt"
    }
}
