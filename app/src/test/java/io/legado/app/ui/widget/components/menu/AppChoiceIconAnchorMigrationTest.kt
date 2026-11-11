package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第十九轮：顶栏图标单选迁到 [AppChoiceDropdown] 的 `Icon` 锚点。
 *
 * 这两页原本都是 `TopBarActionButton(Filter)` + `RoundDropdownMenu`，选项是动态分组、
 * 单选高亮用 `isSelected`。迁移后 trigger 是纯展示 `AppIcon`，由入口套各自的原生图标按钮壳。
 *
 * 分组 id 是 `Long` 位掩码，而 [AppChoiceOption.id] 是字符串稳定 ID，转换必须留在页面层，
 * 不能把 `Long` 渗进菜单契约。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明弹层定位、真机外观与 TalkBack 行为**。
 */
class AppChoiceIconAnchorMigrationTest {

    @Test
    fun cacheManageGroupFilterUsesIconAnchor() {
        val source = sourceOf(CACHE)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertFalse("仍自带分组菜单开合状态", source.contains("showGroupMenu"))
        assertTrue("缺少 AppChoiceDropdown", source.contains("AppChoiceDropdown("))
        assertTrue("未使用 Icon 锚点", source.contains("anchor = AppMenuAnchor.Icon"))
        assertTrue("分组 id 转换丢失", source.contains("AppChoiceOption(id = id.toString()"))
        assertTrue("选中态丢失", source.contains("selectedId = selectedGroupId.toString()"))
    }

    @Test
    fun bookshelfManageGroupFilterUsesIconAnchor() {
        val source = sourceOf(MANAGE)
        assertFalse("仍自带分组菜单开合状态", source.contains("showGroupMenu"))
        assertTrue("缺少 AppChoiceDropdown", source.contains("AppChoiceDropdown("))
        assertTrue("未使用 Icon 锚点", source.contains("anchor = AppMenuAnchor.Icon"))
        assertTrue(
            "分组 id 转换丢失",
            source.contains("AppChoiceOption(id = it.groupId.toString()"),
        )
        assertTrue("ChangeGroup 未接回调", source.contains("ChangeGroup(id.toLong())"))
    }

    /** 同文件那处列表项「更多」菜单已在第二十轮迁到 `Row` 锚点，此处棘轮收尾。 */
    @Test
    fun bookshelfManageKeepsOtherMenuRatchet() {
        assertEquals(
            "BookshelfManageScreen 应不再有手画 RoundDropdownMenu",
            0,
            Regex("RoundDropdownMenu\\(").findAll(sourceOf(MANAGE)).count(),
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
        const val CACHE = "io/legado/app/ui/book/cache/manage/BookCacheManageScreen.kt"
        const val MANAGE = "io/legado/app/ui/book/manage/BookshelfManageScreen.kt"
    }
}
