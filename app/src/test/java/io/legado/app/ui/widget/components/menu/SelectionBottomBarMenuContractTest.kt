package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 多选悬浮栏「更多」菜单的原生化契约。
 *
 * 旧形态是两处同构的 `Box { IconButton; RoundDropdownMenu { 自绘行 } }`：Miuix 下即使
 * 命中玻璃也走无 morph 的 `GlassDropdownPopup`，行仍是项目自绘。现两分支统一交给
 * `AppActionMenu(anchor = Row)`，由四引擎各自渲染原生行 / morph。
 *
 * 静态契约只保证入口与结构，不证明真机上弹层定位、morph 起点或按钮观感。
 */
class SelectionBottomBarMenuContractTest {

    @Test
    fun bothBranchesUseNativeActionMenu() {
        val source = sourceOf(BAR)
        assertFalse("多选栏仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertFalse("仍自持 showMenu 展开状态", source.contains("showMenu"))
        assertEquals(
            "Miuix 与其它引擎两个分支都要走原生菜单",
            2,
            Regex("SelectionMoreMenu\\(secondaryActions, moreActionsDescription\\)")
                .findAll(source).count(),
        )
        assertTrue(
            "必须用 Row 锚点（Icon 锚点在底栏拿不到 Miuix 玻璃 morph）",
            source.contains("anchor = AppMenuAnchor.Row"),
        )
        assertTrue("必须用 AppActionMenu 入口", source.contains("AppActionMenu("))
    }

    @Test
    fun actionItemApiStaysStableForAllCallers() {
        val source = sourceOf(BAR)
        assertTrue(
            "ActionItem 的 onClick API 不能改，否则十几处复用页面都要动",
            source.contains("val onClick: () -> Unit"),
        )
        assertTrue(
            "secondaryActions 仍是 List<ActionItem>",
            source.contains("secondaryActions: List<ActionItem>,"),
        )
        assertTrue(
            "按 index 生成稳定 ID 并回查 onClick，菜单项不带占位图标",
            source.contains("AppAction(id = index.toString(), label = action.text)") &&
                source.contains("secondaryActions.getOrNull("),
        )
    }

    @Test
    fun displayOnlyTriggerForwardsAnchorModifier() {
        val source = sourceOf(BAR)
        assertTrue(
            "trigger 必须是纯展示按钮，不能形成两层可点节点",
            source.contains("MediumPlainButton(") && source.contains("onClick = null,"),
        )
        assertTrue(
            "trigger 必须透传 scope.modifier，否则 COUI 锚点节点未注册",
            source.contains("modifier = this.modifier,"),
        )
        assertTrue(
            "触发裁剪必须与 MediumPlainButton 的默认形状同源",
            source.contains("triggerShape = IconButtonDefaults.extraSmallRoundShape"),
        )
    }

    /** 栏上所有非主按钮（全选 / 反选 / 前导动作 / 更多）统一到 medium 档位。 */
    @Test
    fun allSecondaryButtonsUseMediumSeries() {
        val source = sourceOf(BAR)
        assertEquals(
            "Miuix 3 处 + 其它引擎 3 处 + 菜单 trigger 1 处",
            7,
            Regex("MediumPlainButton\\(").findAll(source).count(),
        )
        assertFalse("仍留着 small 档位按钮", source.contains("SmallPlainButton("))
        assertFalse(
            "M3 IconButton 已全部换成 medium 系列",
            source.lines().any { it.trim() == "import androidx.compose.material3.IconButton" },
        )
        assertTrue("主操作仍保留 FilledIconButton", source.contains("FilledIconButton("))
        assertTrue("主操作仍保留 MiuixIconButton", source.contains("MiuixIconButton("))
    }

    /**
     * 采样源不能包含消费者自身：COUI 的玻璃材质会把源图层画进自己的 patch，源图层一旦是
     * 多选栏的祖先，hwui 会在 prepareTree 里无限递归直到 RenderThread 栈溢出（native 闪退，
     * Java 层拦不到）。因此多选栏只能渲染在 `AppScaffold` 的浮层槽位里。
     */
    @Test
    fun selectionBarRendersOutsideThePageSamplingSource() {
        listOf(LIST_SCAFFOLD, TOC_SCREEN).forEach { path ->
            val source = sourceOf(path)
            val overlayIndex = source.indexOf("overlay = {")
            val barIndex = source.indexOf("SelectionBottomBar(")
            assertTrue("$path 缺少 AppScaffold 浮层槽位", overlayIndex >= 0)
            assertTrue(
                "$path 的多选栏没有渲染在浮层槽位里（正文采样源会包含它自身）",
                barIndex > overlayIndex,
            )
        }

        val scaffold = sourceOf(APP_SCAFFOLD)
        assertTrue(
            "AppScaffold 的浮层槽位必须绘制在正文采样源之外",
            scaffold.contains("Box(modifier = Modifier.fillMaxSize(), content = overlay)") ||
                scaffold.contains("CompositionLocalProvider(LocalCouiPageBackdrop provides couiBackdrop) {"),
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
        const val BAR = "io/legado/app/ui/widget/components/SelectionBottomBar.kt"
        const val APP_SCAFFOLD = "io/legado/app/ui/widget/components/AppScaffold.kt"
        const val LIST_SCAFFOLD = "io/legado/app/ui/widget/components/list/ListScaffold.kt"
        const val TOC_SCREEN = "io/legado/app/ui/book/toc/TocScreen.kt"
    }
}
