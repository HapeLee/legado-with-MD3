package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十轮：书架分组菜单的两处入口（顶栏分组按钮、编辑态分组胶囊）收成一个受控 [AppActionMenu]。
 *
 * 这处此前卡在 `expanded` 跨 Route / Menu 两层透传与两套独立 state；实际已经统一在
 * `BookshelfOverlay.GroupMenu` 上，所以本轮的活是：
 * - 两处共用 `BookshelfGroupMenu`，只渲染弹层，锚点由各自外层的 `Box` 提供；
 * - 当前分组用 `AppAction.selected` 表达，替代原来的 `trailingIcon = Check`；
 * - 搜索态「全部 / 隐藏」的菜单内标题按口径降级为组间分段线（第二组存在时才有线）；
 * - 两处点击语义本来就不同（编辑态还要退回复层、改分组），继续由各自的 `onSelect` 承担。
 *
 * 静态契约只锁结构与 ID；顶栏按钮与菜单落点仍需真机复核。
 */
class BookshelfGroupMenuContractTest {

    @Test
    fun bothEntriesShareOneControlledMenu() {
        val source = codeOf(BOOKSHELF)
        assertEquals("仍手画 RoundDropdownMenu", 0, Regex("RoundDropdownMenu").findAll(source).count())
        assertTrue("缺少共用组件", source.contains("private fun BookshelfGroupMenu("))
        assertEquals(
            "应有声明 + 两处调用",
            3,
            Regex("BookshelfGroupMenu\\(").findAll(source).count(),
        )
        assertTrue("未把开合交给页面", source.contains("embeddedExpanded = expanded"))
        assertTrue("未接页面关闭回调", source.contains("onDismissRequest = onDismiss"))
        assertTrue("受控模式下 trigger 应为空", source.contains("trigger = {}"))
    }

    /** 两处都必须留一个有尺寸的锚点容器，否则受控弹层没有落点。 */
    @Test
    fun bothSitesProvideSizedAnchorContainer() {
        assertEquals(
            "两处入口应各有一个 matchParentSize 锚点容器",
            2,
            Regex("modifier = Modifier\\.matchParentSize\\(\\)").findAll(codeOf(BOOKSHELF)).count(),
        )
    }

    @Test
    fun selectionUsesSelectedAndStableGroupIds() {
        val source = codeOf(BOOKSHELF)
        assertEquals(
            "当前分组应经 selected 表达（两段分组各一处）",
            2,
            Regex("selected = currentGroupId == group\\.groupId").findAll(source).count(),
        )
        assertTrue(
            "分组 ID 未用稳定前缀",
            source.contains("private const val BookshelfGroupMenuIdPrefix = \"group:\""),
        )
        assertTrue("ID 未回解成分组", source.contains("?.let(onSelect)"))
    }

    /** 「全部 / 隐藏」的菜单内标题已按口径去掉，第二组靠组间线区分。 */
    @Test
    fun hiddenSectionHeadingBecomesNativeDivider() {
        val source = codeOf(BOOKSHELF)
        assertFalse("菜单里仍有 PillHeaderDivider", source.contains("PillHeaderDivider"))
        assertFalse("仍在拼「全部 / 隐藏」标题", source.contains("R.string.hide"))
        assertTrue(
            "隐藏分组组未独立成组",
            source.contains("id = \"bookshelf_hidden_groups\""),
        )
    }

    /** 搜索态第二组 = 全部 + 已隐藏分组。 */
    @Test
    fun searchExtrasKeepAllAndHiddenGroups() {
        val source = codeOf(BOOKSHELF)
        assertTrue(
            "未取「全部」分组",
            source.contains("allGroups.firstOrNull { it.groupId == BookGroup.IdAll }"),
        )
        assertTrue(
            "未筛出隐藏分组",
            source.contains("!it.show && it.groupId != BookGroup.IdAll"),
        )
        assertTrue(
            "搜索态未挂第二组",
            source.contains("extraGroups = if (uiState.isSearch)"),
        )
    }

    /** 两处点击语义保留：编辑态仍要退回复层。 */
    @Test
    fun perSiteClickSemanticsStayWithCaller() {
        val source = codeOf(BOOKSHELF)
        assertTrue("编辑态丢失「退回复层」", source.contains("if (bookGroupStyle == 2)"))
        assertTrue(
            "分组切换语义丢失",
            source.contains("BookshelfIntent.ChangeGroup(group.groupId)"),
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
        const val BOOKSHELF = "io/legado/app/ui/main/bookshelf/BookshelfScreen.kt"
    }
}
