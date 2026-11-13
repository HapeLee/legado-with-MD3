package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十一轮：`MainScreen` 的书架 Rail 分组菜单收成一个受控 [AppActionMenu]。
 *
 * 这处此前记作「`expanded` 跨 Route / Menu 两层透传 + 两处独立 state 与门控」。实测两处
 * `showGroupMenu` 分属互斥布局分支（Miuix 底栏 / 宽 `WideNavigationRail`），各自局部 `remember`
 * 即可，不应合并进 `BookshelfOverlay.GroupMenu`；真正的冗余是那层恒真的 `expanded`——
 * 两处调用点都已由 `if (… && showGroupMenu)` 门控组合，再往下传只会一路透传到
 * `embeddedExpanded`。因此本轮只做减法：
 * - `BookshelfRailGroupMenuRoute` / `BookshelfRailGroupMenu` 不再接收 `expanded`；
 * - 弹层恒以 `embeddedExpanded = true` 渲染，开合仍由调用方的 `showGroupMenu` + `onDismissRequest` 收口；
 * - 锚点、稳定 ID（`group:<groupId>`）、`selected` 表达与两处点击语义保持不变。
 *
 * 静态契约只锁结构；两个 Rail 分支的弹层落点与长按命中仍需真机复核。
 */
class MainScreenRailGroupMenuContractTest {

    @Test
    fun railSitesShareOneControlledMenu() {
        val source = codeOf(MAIN_SCREEN)
        assertEquals("仍手画 RoundDropdownMenu", 0, Regex("RoundDropdownMenu").findAll(source).count())
        assertTrue("缺少共用组件", source.contains("private fun BookshelfRailGroupMenu("))
        assertEquals(
            "应有 Route 声明 + 两处调用",
            3,
            Regex("BookshelfRailGroupMenuRoute\\(").findAll(source).count(),
        )
        assertTrue("受控模式下 trigger 应为空", source.contains("trigger = {}"))
    }

    /** 开合由调用方的 `showGroupMenu` 门控，不再沿 Route / Menu 两层传 `expanded`。 */
    @Test
    fun expandedIsGatedByCallerInsteadOfPassedDown() {
        val source = codeOf(MAIN_SCREEN)
        assertFalse("仍在透传 expanded 参数", source.contains("expanded: Boolean"))
        assertFalse("调用点仍传 expanded", source.contains("expanded = showGroupMenu"))
        assertTrue("受控弹层应恒展开", source.contains("embeddedExpanded = true"))
        assertEquals(
            "两处入口都应保留 showGroupMenu 门控",
            2,
            Regex("destination == MainDestination\\.Bookshelf && showGroupMenu")
                .findAll(source).count(),
        )
    }

    /** 两处都必须留一个有尺寸的锚点容器，否则受控弹层没有落点。 */
    @Test
    fun bothSitesProvideSizedAnchorContainer() {
        assertEquals(
            "两处入口应各有一个 matchParentSize 锚点容器",
            2,
            Regex("modifier = Modifier\\.matchParentSize\\(\\)").findAll(codeOf(MAIN_SCREEN)).count(),
        )
    }

    @Test
    fun selectionUsesSelectedAndStableGroupIds() {
        val source = codeOf(MAIN_SCREEN)
        assertTrue(
            "当前分组应经 selected 表达",
            source.contains("selected = state.selectedGroupId == group.groupId"),
        )
        assertTrue(
            "分组 ID 未用稳定前缀",
            source.contains("private const val BookshelfRailGroupMenuIdPrefix = \"group:\""),
        )
        assertTrue(
            "ID 未回解成分组",
            source.contains("removePrefix(BookshelfRailGroupMenuIdPrefix).toLongOrNull()"),
        )
    }

    /** 关闭回到调用方：两处 `showGroupMenu` 都要能由弹层自己收口。 */
    @Test
    fun dismissReturnsToCaller() {
        val source = codeOf(MAIN_SCREEN)
        assertEquals(
            "两处入口都应接关闭回调",
            2,
            Regex("onDismissRequest = \\{ showGroupMenu = false \\}").findAll(source).count(),
        )
        assertEquals(
            "长按应写入两处各自的局部状态",
            2,
            Regex("showGroupMenu = true").findAll(source).count(),
        )
    }

    /** 点击语义仍留在各自调用点：先切 pager，再按 ID 改分组。 */
    @Test
    fun clickSemanticsStayWithCaller() {
        val source = codeOf(MAIN_SCREEN)
        assertTrue("丢失「先切到书架页」", source.contains("onBeforeSelectGroup()"))
        assertTrue(
            "分组切换语义丢失",
            source.contains("BookshelfIntent.ChangeGroup(groupId)"),
        )
    }

    /**
     * 受控弹层先 `onDismissRequest` 再 `onAction`，组件随后被移出组合；选中回调若挂在
     * `rememberCoroutineScope()` 上，会在 `onBeforeSelectGroup()` 挂起时被取消，分组白点。
     */
    @Test
    fun selectionRunsOnCallerScope() {
        val source = codeOf(MAIN_SCREEN)
        assertEquals(
            "页面只应保留导航栏宿主那一份 rememberCoroutineScope",
            1,
            Regex("rememberCoroutineScope\\(\\)").findAll(source).count(),
        )
        assertEquals(
            "两处入口都应把页面 scope 传进菜单",
            2,
            Regex("scope = coroutineScope").findAll(source).count(),
        )
        assertTrue("Route 未接收外部 scope", source.contains("scope: CoroutineScope,"))
        assertTrue(
            "选中回调未跑在外部 scope 上",
            source.contains("scope.launch { onSelectGroup(groupId) }"),
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
        var directory = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val MAIN_SCREEN = "io/legado/app/ui/main/MainScreen.kt"
    }
}
