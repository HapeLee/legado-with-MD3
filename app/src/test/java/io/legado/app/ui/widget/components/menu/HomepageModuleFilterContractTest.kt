package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 首页模块管理页顶栏的分组筛选现在是 `AppModalBottomSheet` 的声明式头部操作。
 *
 * 演进路径：`MediumTonalButton(FilterList)` + `Box { … RoundDropdownMenu }` →
 * `AppChoiceDropdown(compact = true)`（trigger 是纯展示按钮 + `selectedId`）→
 * `endSheetAction = SheetAction(menu = …)`：trigger、弹层与勾选态都由面板头部按引擎渲染，
 * 调用点只提供数据和回调。
 *
 * `groupFilter` 是 `String?`，菜单里用保留 ID `HOMEPAGE_GROUP_FILTER_ALL` 代表「全部」
 * （分组名可能重复），选中态由 `checked` 表达。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明弹层定位、真机外观与 TalkBack 行为**。
 */
class HomepageModuleFilterContractTest {

    @Test
    fun groupFilterUsesDeclarativeSheetMenu() {
        val source = sourceOf(SCREEN)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertFalse("仍用 AppChoiceDropdown", source.contains("AppChoiceDropdown("))
        assertFalse("仍手画 compact 触发按钮", source.contains("compact = true"))
        assertTrue(
            "筛选项未改成面板头部的声明式菜单",
            source.contains("endSheetAction = when {") && source.contains("menu = buildList {"),
        )
        assertTrue("缺少 SheetMenuAction 菜单项", source.contains("SheetMenuAction("))
    }

    @Test
    fun allGroupsEntryKeepsStableId() {
        val source = sourceOf(SCREEN)
        assertTrue("「全部」未用保留 ID", source.contains("id = HOMEPAGE_GROUP_FILTER_ALL"))
        assertTrue(
            "「全部」选中态未映射空值",
            source.contains("checked = groupFilter.isNullOrEmpty()"),
        )
        assertTrue("「全部」回调未还原 null", source.contains("onClick = { groupFilter = null }"))
    }

    @Test
    fun everyGroupBecomesAMenuEntry() {
        val source = sourceOf(SCREEN)
        assertTrue(
            "分组未映射成菜单项",
            source.contains("id = group,") && source.contains("label = group,"),
        )
        assertTrue("分组选中态未按当前筛选值判断", source.contains("checked = groupFilter == group"))
        assertTrue("分组点击未写回筛选值", source.contains("onClick = { groupFilter = group }"))
        assertEquals(
            "除筛选外不应引入新的手画菜单",
            0,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
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
        const val SCREEN = "io/legado/app/ui/main/homepage/HomepageModuleManageSheet.kt"
    }
}
