package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第二十一轮：首页模块管理页顶栏的分组筛选迁到 [AppChoiceDropdown] 的紧凑形态。
 *
 * 旧实现是 `MediumTonalButton(FilterList)` + `Box { … RoundDropdownMenu }`，选项是
 * 「全部 + 动态分组」，勾选态用 `trailingIcon` 自绘。迁到 `AppChoiceDropdown(compact = true)`
 * 后 trigger 仍是同一个 `MediumTonalButton`（`onClick = null` 纯展示），选中态改由
 * `selectedId` 表达，因此外观与点击区不变；`compact` 保证 Miuix 不落到设置行。
 *
 * `groupFilter` 是 `String?`，用空串代表「全部」，避免把 nullable 渗进 [AppChoiceOption.id]。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明弹层定位、真机外观与 TalkBack 行为**。
 */
class HomepageModuleFilterContractTest {

    @Test
    fun groupFilterUsesCompactChoiceDropdown() {
        val source = sourceOf(SCREEN)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertTrue("缺少 AppChoiceDropdown", source.contains("AppChoiceDropdown("))
        assertTrue("缺少 compact 形态", source.contains("compact = true"))
        assertTrue("trigger 未改为纯展示", source.contains("onClick = null"))
    }

    @Test
    fun nullGroupMapsToEmptyStableId() {
        val source = sourceOf(SCREEN)
        assertTrue("「全部」未用空串表达", source.contains("id = \"\""))
        assertTrue("选中值未映射空串", source.contains("selectedId = groupFilter.orEmpty()"))
        assertTrue("回调未还原 null", source.contains("groupFilter = it.ifBlank { null }"))
    }

    @Test
    fun everyGroupBecomesAnOption() {
        val source = sourceOf(SCREEN)
        assertTrue(
            "分组未映射成选项",
            source.contains("add(AppChoiceOption(id = group, label = group))"),
        )
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
