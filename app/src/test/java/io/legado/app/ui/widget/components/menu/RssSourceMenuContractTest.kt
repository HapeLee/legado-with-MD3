package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第二十八轮：RSS 源页顶栏菜单迁到声明式入口，并把顶栏按钮壳统一到一处。
 *
 * 口径（产品确认）：
 * - 伪子菜单不再保留「点进去换一层」，导入三项**平铺到第一层**，与书源页
 *   （`BookSourceScreen`）的导入前四项同形；
 * - 过滤项与动态分组项用 `AppAction.selected` 标出**当前过滤**，因此状态里必须暴露过滤键
 *   （`RssSourceUiState.groupFilter`），展示名 `groupFilterName` 不能反推；
 * - 顶栏那颗 ⋮ 不再由 `AppActionMenu` 自己画裸 `IconButton`，改用 `TopBarActionSurface`，
 *   与同排 `TopBarActionButton` 共用尺寸、容器底色、液体玻璃与合并态。
 *
 * Miuix 的 `Icon*DropdownMenu` 必须由库自己渲染按钮，仍走库分支（非玻璃即
 * `MiuixIconButton`，玻璃走 `MiuixGlassActions`），本轮不动。
 */
class RssSourceMenuContractTest {

    @Test
    fun rssSourceMenuUsesDeclarativeGroupsOnly() {
        val source = codeOf(RSS_SOURCE)
        assertFalse("仍传 dropDownMenuContent", source.contains("dropDownMenuContent"))
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu"))
        assertFalse("仍手画 PillDivider", source.contains("PillDivider("))
        assertFalse("仍保留伪子菜单状态", source.contains("showImportMenu"))
        assertTrue("缺少 menuGroups", source.contains("menuGroups = persistentListOf("))
        assertTrue("缺少 onMenuAction 分派", source.contains("onMenuAction = { id ->"))
    }

    /** 导入三项是平铺命令，不能再出现在某个父项的 `children` 里。 */
    @Test
    fun importItemsAreFlatCommands() {
        val source = codeOf(RSS_SOURCE)
        listOf("import_on_line", "import_local", "import_default_rule").forEach { id ->
            assertEquals(
                "导入项 $id 应恰好有声明与分派各一次（平铺命令，无子菜单）",
                2,
                Regex("\"${Regex.escape(id)}\"").findAll(source).count(),
            )
        }
        assertFalse("菜单里仍有级联子项", source.contains("children ="))
    }

    /** 过滤与分组项要能标出当前选中，且「全部」用 null 表达。 */
    @Test
    fun filterAndGroupItemsShowCurrentSelection() {
        val source = codeOf(RSS_SOURCE)
        assertTrue(
            "「全部」未按 null 过滤键标选中",
            source.contains("selected = state.groupFilter == null"),
        )
        listOf("filter_enabled", "filter_disabled", "filter_need_login", "filter_no_group")
            .forEach { id ->
                assertTrue("$id 缺少分派", source.contains("id == \"$id\""))
            }
        assertTrue(
            "分组项未按 group: 前缀标选中",
            source.contains("selected = state.groupFilter == id"),
        )
        assertTrue(
            "分组过滤未回到 SetGroupFilter",
            source.contains("id.startsWith(RssSourceViewModel.PREFIX_GROUP)"),
        )
    }

    /** 过滤键必须从状态透出（基类只存了展示名）。 */
    @Test
    fun viewModelExposesFilterKeyToState() {
        val state = codeOf(RSS_VIEW_MODEL)
        assertTrue("状态缺少 groupFilter", state.contains("val groupFilter: String? = null"))
        assertTrue("未把过滤键并入 uiState", state.contains("groupFilter = filterKey.ifEmpty { null }"))
        assertTrue("未订阅过滤键", state.contains("combine(") && state.contains("groupFilterKey,"))
    }

    /** 顶栏按钮壳：三引擎走同一处 `TopBarActionSurface`，Material 不再落裸 `IconButton`。 */
    @Test
    fun topBarShellIsSharedAcrossEngines() {
        val entry = codeOf(APP_ACTION_MENU)
        assertEquals(
            "Mochi / COUI / Material 三个 Icon 锚点分支都该走 TopBarActionSurface",
            3,
            Regex("TopBarActionSurface\\(").findAll(entry).count(),
        )
        assertFalse(
            "入口仍在自绘裸 IconButton",
            Regex("(?<![A-Za-z])IconButton\\(").containsMatchIn(entry),
        )
        assertTrue(
            "按钮壳未在 TopBarButton.kt 落地",
            codeOf(TOP_BAR_BUTTON).contains("fun TopBarActionSurface("),
        )
        // Miuix 的按钮仍由库渲染，不能顺手换掉。
        assertTrue(
            "Miuix 库分支被误改",
            entry.contains("GlassIconDropdownMenu(") || entry.contains("OverlayIconDropdownMenu("),
        )
    }

    /** 去掉注释行：历史注释里提到组件名不算违规。 */
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
        const val RSS_SOURCE = "io/legado/app/ui/rss/source/manage/RssSourceScreen.kt"
        const val RSS_VIEW_MODEL = "io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt"
        const val APP_ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
        const val TOP_BAR_BUTTON = "io/legado/app/ui/widget/components/topbar/TopBarButton.kt"
    }
}
