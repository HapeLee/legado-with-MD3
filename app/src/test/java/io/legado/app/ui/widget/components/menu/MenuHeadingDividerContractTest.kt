package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第二十七轮：`PillHeaderDivider(title)` 的两种用途按产品口径拆开。
 *
 * - **菜单内当标题**（RSS 源名、发现的源名、目录卷管理的「快速跳转」）：标题文字直接去掉。
 *   原生菜单本来就不渲染分组标题文字，这些标题在四引擎上只是「一条线」；数据本身在页面上
 *   一直可见（源图下的源名），删掉不丢信息。
 * - **纯分割**：改由 `AppActionGroup` 分组表达（四引擎统一画分段线），不再手画标题组件。
 * - **页面级分节标题**（书架外观设置、「添加自定义模块」对话框）：这里标题文字是唯一的分节
 *   标识，不是菜单行，必须保留 `PillHeaderDivider`，不在本轮口径内。
 *
 * 本轮迁 3 处：`RssScreen` 源长按菜单、`ExploreScreen` 源长按菜单、`TocScreen` 卷管理菜单。
 * 静态契约只锁结构与 ID；四引擎外观（含 destructive 落色）仍需真机复核——
 * COUI 的 `CouiMenuItem` 只能染图标、文字保持默认色，Miuix 的 `DropdownItem` 没有危险色通道。
 */
class MenuHeadingDividerContractTest {

    /** 三处都已无手画菜单、无手画标题组件，且走受控弹层 + 有尺寸的锚点容器。 */
    @Test
    fun migratedSitesDropHeadingAndUseNativeDivider() {
        listOf(RSS_SCREEN, EXPLORE_SCREEN, TOC_SCREEN).forEach { path ->
            val source = codeOf(path)
            assertFalse("$path 仍手画 PillHeaderDivider", source.contains("PillHeaderDivider("))
            assertFalse("$path 仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
            assertFalse("$path 仍引用旧菜单项组件", source.contains("RoundDropdownMenuItem("))
            assertTrue("$path 未使用受控弹层", source.contains("embeddedExpanded = "))
            assertTrue("$path 受控模式仍渲染 trigger", source.contains("trigger = {}"))
            assertTrue("$path 缺少有尺寸的锚点容器", source.contains("modifier = Modifier.matchParentSize()"))
        }
    }

    /** 标题去掉了，但「这是哪个源 / 哪一卷」的信息必须留在页面上。 */
    @Test
    fun headingInformationStaysOutsideTheMenu() {
        assertTrue(
            "RSS 源名从页面消失了（只删菜单标题，不能删正文）",
            sourceOf(RSS_SCREEN).contains("text = source.sourceName"),
        )
        assertTrue(
            "发现页源名从条目正文消失了",
            sourceOf(EXPLORE_SCREEN).contains("text = item.bookSourceName"),
        )
        // 卷管理不再有「快速跳转」这四个字，卷项改由第二组承载。
        val toc = codeOf(TOC_SCREEN)
        assertFalse("卷管理仍保留「快速跳转」标题", toc.contains("R.string.quick_jump"))
        assertTrue("卷跳转项未单独成组", toc.contains("id = \"toc_volume_jump\""))
        assertTrue("卷跳转未按下标解码", toc.contains("removePrefix(VolumeJumpIdPrefix)"))
    }

    /** 两处长按菜单的删除项必须是 destructive，而不是把红字写死在调用点。 */
    @Test
    fun deleteItemsUseDestructiveFlag() {
        assertTrue(
            "RSS 删除项未标记 destructive",
            sourceOf(RSS_SCREEN).contains("destructive = action == RssSourceMenuAction.Delete"),
        )
        assertTrue(
            "发现页删除项未标记 destructive",
            sourceOf(EXPLORE_SCREEN).contains("destructive = action == ExploreSourceMenuAction.Delete"),
        )
    }

    /** 稳定 ID：声明与分派共用。 */
    @Test
    fun migratedMenusDeclareAndDispatchStableIds() {
        val rss = sourceOf(RSS_SCREEN)
        RSS_IDS.forEach { (constant, id) ->
            assertTrue("RSS 未声明 $id", rss.contains("$constant(\"$id\","))
            assertTrue("RSS 缺少 $constant 分派", rss.contains("$constant.id ->"))
        }
        val explore = sourceOf(EXPLORE_SCREEN)
        EXPLORE_IDS.forEach { (constant, id) ->
            assertTrue("发现页未声明 $id", explore.contains("$constant(\"$id\","))
            assertTrue("发现页缺少 $constant 分派", explore.contains("$constant.id ->"))
        }
        val toc = sourceOf(TOC_SCREEN)
        TOC_IDS.forEach { (constant, id) ->
            assertTrue("卷管理未声明 $id", toc.contains("$constant(\"$id\")"))
            assertTrue("卷管理缺少 $constant 分派", toc.contains("VolumeMenuAction.$constant.id"))
        }
    }

    /**
     * 页面级分节标题保持不动：它们的文字是唯一的分节标识，不属于「菜单内标题」口径。
     * 第三十轮把书架分组菜单迁完后，**菜单内**已不再有任何 `PillHeaderDivider`（含「全部 / 隐藏」）。
     */
    @Test
    fun pageLevelSectionHeadingsKeepTheirText() {
        assertEquals(
            "书架外观设置的分节标题被误删",
            5,
            Regex("PillHeaderDivider\\(").findAll(sourceOf(BOOKSHELF_CONFIG)).count(),
        )
        assertEquals(
            "「添加自定义模块」对话框的分节标题被误删",
            1,
            Regex("PillHeaderDivider\\(").findAll(sourceOf(ADD_MODULE_DIALOG)).count(),
        )
        assertEquals(
            "菜单内标题应已归零（书架分组菜单的「全部 / 隐藏」改为组间线）",
            0,
            Regex("PillHeaderDivider\\(").findAll(sourceOf(BOOKSHELF_SCREEN)).count(),
        )
    }

    /** 去掉注释行：历史注释里提到某个组件名不算违规，结构断言只应命中真实代码。 */
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
        const val RSS_SCREEN = "io/legado/app/ui/main/rss/RssScreen.kt"
        const val EXPLORE_SCREEN = "io/legado/app/ui/main/explore/ExploreScreen.kt"
        const val TOC_SCREEN = "io/legado/app/ui/book/toc/TocScreen.kt"
        const val BOOKSHELF_CONFIG = "io/legado/app/ui/main/bookshelf/BookshelfConfigSheet.kt"
        const val ADD_MODULE_DIALOG = "io/legado/app/ui/main/homepage/manage/AddCustomModuleDialog.kt"
        const val BOOKSHELF_SCREEN = "io/legado/app/ui/main/bookshelf/BookshelfScreen.kt"
        val RSS_IDS = listOf(
            "ToTop" to "to_top",
            "Edit" to "edit",
            "Login" to "login",
            "Disable" to "disable_source",
            "Delete" to "delete",
        )
        val EXPLORE_IDS = listOf(
            "ToTop" to "to_top",
            "Edit" to "edit",
            "Search" to "search",
            "Login" to "login",
            "Refresh" to "refresh",
            "Delete" to "delete",
        )
        val TOC_IDS = listOf(
            "ExpandAll" to "expand_all_volumes",
            "CollapseAll" to "collapse_all_volumes",
        )
    }
}
