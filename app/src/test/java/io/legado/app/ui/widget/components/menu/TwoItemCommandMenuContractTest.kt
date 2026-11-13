package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第二十六轮：两处「两项纯命令 + 调用方自持外观/点击语义」的 ⋮ 菜单迁到受控 [AppActionMenu]。
 *
 * - `ReaderBookSheet` 书签页：导出 JSON / Markdown，工具按钮用 `CompactToolIconBox(selected = ...)`
 *   表达展开态高亮。
 * - `NavIconManageSheet` 导航图标卡：替换 / 删除；卡片在**未设图标时点击是直接选图**，不是开菜单，
 *   所以点击语义必须留在调用方，入口只渲染弹层。
 *
 * 两处都没有分组标题、危险色、勾选态、子菜单或动态文案，因此是零外观损失的迁移；
 * 其余 27 处旧调用点各自带有其中一项，需先有外观基线。静态契约只锁结构与 ID，
 * 弹层落点与四引擎外观仍需真机复核（COUI 在 Robolectric 下退回 Material 分支）。
 */
class TwoItemCommandMenuContractTest {

    @Test
    fun readerBookmarkMenuIsControlledAndDispatchesIds() {
        val source = sourceOf(READER_BOOK_SHEET)
        assertFalse("书签页仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertTrue("未把开合交给页面", source.contains("embeddedExpanded = menuExpanded"))
        assertTrue("未接页面关闭回调", source.contains("onDismissRequest = { menuExpanded = false }"))
        assertTrue("受控模式下 trigger 应为空", source.contains("trigger = {}"))
        assertTrue(
            "工具按钮展开态高亮丢失",
            source.contains("selected = menuExpanded"),
        )
        BOOKMARK_IDS.forEach { (constant, id) ->
            assertTrue("未声明 $id", source.contains("$constant(\"$id\","))
            assertTrue("缺少 $constant 分派", source.contains("$constant.id ->"))
        }
        // 后缀由所选命令决定，迁移不能把两种格式并成同一份导出。
        assertTrue(
            "导出后缀未按命令区分",
            source.contains("""${'$'}{if (markdown) "md" else "json"}"""),
        )
    }

    @Test
    fun navIconMenuIsControlledAndKeepsCardClickSemantics() {
        val source = sourceOf(NAV_ICON)
        assertFalse("导航图标卡仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertTrue("未把开合交给页面", source.contains("embeddedExpanded = menuExpanded"))
        assertTrue("未接页面关闭回调", source.contains("onDismissRequest = { menuExpanded = false }"))
        assertTrue("受控模式下 trigger 应为空", source.contains("trigger = {}"))
        // 未设图标时点卡片是「直接选图」，迁移不能把这条分支并进开菜单。
        assertTrue(
            "卡片自身的点击语义被菜单开合吞掉",
            source.contains("if (path.isNotEmpty()) menuExpanded = true else onSelect()"),
        )
        NAV_ICON_IDS.forEach { (constant, id) ->
            assertTrue("未声明 $id", source.contains("$constant(\"$id\","))
            assertTrue("缺少 $constant 分派", source.contains("$constant.id ->"))
        }
    }

    /** 两处入口都必须在调用方容器上留出 `matchParentSize` 锚点矩形。 */
    @Test
    fun controlledSitesKeepSizedAnchorContainer() {
        listOf(READER_BOOK_SHEET, NAV_ICON).forEach { path ->
            assertTrue(
                "$path 的受控弹层缺少有尺寸的锚点容器",
                sourceOf(path).contains("modifier = Modifier.matchParentSize()"),
            )
        }
    }

    /** 棘轮：其余 27 处旧调用点本轮不动，出现新的手画菜单即失败。 */
    @Test
    fun thisRoundTouchesOnlyTheTwoTwoItemMenus() {
        listOf(READER_BOOK_SHEET, NAV_ICON).forEach { path ->
            assertEquals(
                "$path 应已无手画菜单",
                0,
                Regex("RoundDropdownMenu\\(").findAll(sourceOf(path)).count(),
            )
        }
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

    private companion object {
        const val READER_BOOK_SHEET = "io/legado/app/ui/book/read/sheet/ReaderBookSheet.kt"
        const val NAV_ICON = "io/legado/app/ui/config/themeConfig/NavIconManageSheet.kt"
        val BOOKMARK_IDS = listOf(
            "ExportJson" to "export_bookmarks_json",
            "ExportMarkdown" to "export_bookmarks_markdown",
        )
        val NAV_ICON_IDS = listOf(
            "Replace" to "replace_nav_icon",
            "Clear" to "clear_nav_icon",
        )
    }
}
