package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RSS 排序页顶栏主菜单迁移契约。
 *
 * 旧实现是 `TopBarActionButton(onClick) + RoundDropdownMenu`（8 项，末尾「重定向策略」
 * 会再开一个 `RoundDropdownMenu` 二级菜单，且选择后要手动关主菜单）。迁移后改为
 * [TopBarActionMenu] + 单个 [AppActionGroup]，「重定向策略」降级为
 * [AppAction.children]（四个引擎原生支持级联；Material 平铺不丢 ID），
 * 选中态由 `AppAction.checked` 表达，收起交给入口。
 *
 * 底部分组切换菜单同样挂在 `SmallToggleButton` 上（库没有非可点的纯展示变换形态），
 * 第三十六轮把它也改成受控 [AppActionMenu]：按钮只负责写 `showGroupMenu`，弹层由
 * `embeddedExpanded` 驱动，当前分组用 `AppAction.selected` 表达。
 *
 * 静态契约只保证结构 / ID 一致，**不证明真机上二级菜单的展开与选中态**。
 */
class RssSortMenuContractTest {

    @Test
    fun mainMenuUsesDeclarativeEntrypoint() {
        val source = sourceOf(SCREEN)
        val topBar = source.substringAfter("actions = {").substringBefore("bottomContent = {")
        assertTrue("顶栏缺少 TopBarActionMenu", topBar.contains("TopBarActionMenu("))
        assertFalse("顶栏仍手画 RoundDropdownMenu", topBar.contains("RoundDropdownMenu("))
        assertFalse("仍自带主菜单开合状态", source.contains("showMainMenu"))
        assertFalse("仍自带二级菜单开合状态", source.contains("showRedirectMenu"))
    }

    @Test
    fun everyActionHasDeclarationAndDispatch() {
        val source = sourceOf(SCREEN)
        LEAF_IDS.forEach { id ->
            assertTrue("未声明 $id", source.contains("\"$id\""))
            assertTrue("缺少 $id 的分派", source.contains("id == \"$id\""))
        }
        // 父项本身不发命令，只负责展开 children。
        assertTrue("未声明重定向策略父项", source.contains("id = \"redirect_policy\""))
        // 重定向策略：子项 ID = 前缀 + enum name，分派走前缀判断。
        assertTrue("缺少重定向策略子项 ID 前缀", source.contains("REDIRECT_PREFIX"))
        assertTrue("子项前缀未参与分派", source.contains("id.startsWith(REDIRECT_PREFIX)"))
        assertTrue("子项未携带选中态", source.contains("checked = policy == redirectPolicy"))
    }

    /** 第三十六轮：分组切换菜单已迁，棘轮归零，但仍保留 `SmallToggleButton` 作为开合入口。 */
    @Test
    fun groupSwitcherIsControlledMenu() {
        val source = sourceOf(SCREEN)
        assertEquals(
            "页面应已无手画菜单",
            0,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
        assertTrue(
            "分组切换的自定义 toggle trigger 被误删",
            source.contains("SmallToggleButton("),
        )
        assertTrue("分组切换弹层未受控", source.contains("embeddedExpanded = showGroupMenu"))
        assertTrue(
            "分组切换缺少稳定 ID 前缀",
            source.contains("private const val GROUP_SWITCHER_PREFIX = \"rss_sort_group:\""),
        )
        assertTrue("分组选中态未用 selected", source.contains("selected = selectedTabIndex == index"))
        assertTrue("分组切换未按序号回解", source.contains("removePrefix(GROUP_SWITCHER_PREFIX)"))
        assertFalse("仍手画 Check 选中图标", source.contains("Icons.Default.Check"))
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
        const val SCREEN = "io/legado/app/ui/rss/article/RssSortScreen.kt"
        val LEAF_IDS = listOf(
            "login",
            "refresh_sort",
            "set_source_variable",
            "edit_source",
            "switch_layout",
            "read_record",
            "clear_articles",
        )
    }
}
