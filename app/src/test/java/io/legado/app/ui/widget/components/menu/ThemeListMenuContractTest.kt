package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十八轮：阅读主题列表顶行 ⋮ 菜单的「原地翻页」行为 + 收编后的结构契约。
 *
 * 这处菜单与其余受控 `AppActionMenu` 不同：点「网格列数」**不收起菜单**，同一个弹层就地
 * 换成 2..6 的列数列表；一级菜单量到的自然宽度锁给两级共用，翻页时不跳宽。
 * 三个命令项与五个列数项都先 dismiss 再执行动作（列数项是先写回 intent 再 dismiss）。
 *
 * 迁移做法是 `AppAnchoredPanel(externalTrigger = true)` + 自定义 content：入口只接管弹层分派，
 * content 自己决定是否 dismiss，因此「不关菜单」不需要给 `AppActionMenu` 补 keepOpen；
 * ⋮ 按钮仍由本页用 `SmallTonalButton` 渲染，外观与点击语义不变。
 *
 * 静态断言不证明四引擎弹层落点、外观与 TalkBack。
 */
class ThemeListMenuContractTest {

    /** 单弹层、页面自持开合、宽度锁定；手画容器已归零。 */
    @Test
    fun menuIsSingleAnchoredPanelWithFrozenWidth() {
        val source = codeOf(THEME_LIST)
        assertEquals(
            "ThemeListContent 仍手画 RoundDropdownMenu",
            0,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
        assertEquals(
            "应只有一个统一入口弹层",
            1,
            Regex("AppAnchoredPanel\\(").findAll(source).count(),
        )
        assertTrue("开合未由 menuExpanded 驱动", source.contains("expanded = menuExpanded"))
        assertTrue("未保留既有按钮外观", source.contains("externalTrigger = true"))
        assertTrue("未接页面开合回调", source.contains("onExpandedChange = { isOpen ->"))
        assertTrue("一二级菜单未共用同一状态", source.contains("if (!themeMenuShowsColumns) {"))
        assertTrue("宽度锁定丢失", source.contains("Modifier.width(themeMenuWidth)"))
        assertTrue("未测量自然宽度", source.contains("with(menuDensity) { it.width.toDp() }"))
        assertTrue("测量前未判定", source.contains("if (themeMenuWidth == 0.dp)"))
    }

    /** ⋮ 按钮仍由本页渲染，点击先回到一级菜单再交给入口展开。 */
    @Test
    fun existingTriggerStillOpensPanelAtFirstPage() {
        val source = codeOf(THEME_LIST)
        val trigger = source
            .substringAfter("trigger = {")
            .substringBefore(") { dismiss ->")
        assertTrue("⋮ 按钮被换成别的控件", trigger.contains("SmallTonalButton("))
        assertTrue("⋮ 图标丢失", trigger.contains("icon = AppIcons.MoreVert"))
        assertTrue("无障碍名称丢失", trigger.contains("contentDescription = stringResource(R.string.more)"))
        assertTrue(
            "打开时未回到一级菜单",
            Regex("themeMenuShowsColumns = false\\s+open\\(\\)").containsMatchIn(trigger),
        )
        assertFalse("入口不该被换成 AppActionMenu", source.contains("AppActionMenu("))
        assertFalse("不该为「不关菜单」补 keepOpen", source.contains("keepOpen"))
    }

    /** 三条命令项：每条都先关菜单，再发对应的 intent / 进入管理态。 */
    @Test
    fun threeCommandItemsDismissBeforeIntent() {
        val source = codeOf(THEME_LIST)
        listOf(
            "R.string.theme_list_import_as_new" to "ReadBookIntent.OpenReadStyleImportAsNew",
            "R.string.theme_list_add_theme" to "ReadBookIntent.AddReadStyleConfig",
        ).forEach { (label, intent) ->
            assertTrue("缺少命令项文案 $label", source.contains(label))
            assertTrue(
                "$intent 未在 dismiss 之后执行",
                Regex("dismiss\\(\\)\\s+onIntent\\($intent\\)").containsMatchIn(source),
            )
        }
        assertTrue("管理项文案丢失", source.contains("R.string.theme_list_manage"))
        assertTrue(
            "管理项未先关菜单再置位",
            Regex("dismiss\\(\\)\\s+manageMode = true").containsMatchIn(source),
        )
    }

    /** 「网格列数」只在网格布局出现，点击只翻页、不 dismiss。 */
    @Test
    fun columnsEntrySwitchesPageWithoutDismissing() {
        val source = codeOf(THEME_LIST)
        assertTrue("列数入口丢了网格模式门控", source.contains("if (!useFlowLayout) {"))
        assertTrue("列数入口文案丢失", source.contains("R.string.a11y_grid_columns"))
        val switchLine = source.lineSequence().first {
            it.contains("themeMenuShowsColumns = true")
        }
        assertTrue("翻页动作丢失", switchLine.contains("onClick = { themeMenuShowsColumns = true }"))
        assertFalse("翻页项不应关菜单", switchLine.contains("dismiss"))
    }

    /** 列数页：2..6 全集、当前值选中、写回配置后关闭。 */
    @Test
    fun columnOptionsApplyAndDismiss() {
        val source = codeOf(THEME_LIST)
        assertTrue("列数可选项被改", source.contains("GridColumnOptions = listOf(2, 3, 4, 5, 6)"))
        assertTrue("当前列数未做脏数据兜底", source.contains("styleConfig.gridColumns.coerceIn(2, 6)"))
        assertTrue("选中态丢失", source.contains("isSelected = columns == currentColumns"))
        assertTrue(
            "未在写回 intent 后关闭",
            Regex(
                "onIntent\\(ReadBookIntent\\.SetThemeGridColumns\\(columns\\)\\)\\s+dismiss\\(\\)"
            ).containsMatchIn(source),
        )
    }

    /** 关闭/外部 dismiss 时，翻页位置与量到的宽度都要作废，下次重开重新量。 */
    @Test
    fun closingMenuResetsPageAndMeasuredWidth() {
        val source = codeOf(THEME_LIST)
        val closeBlock = source
            .substringAfter("onExpandedChange = { isOpen ->")
            .substringBefore("externalTrigger = true")
        assertTrue("关闭未收起弹层", closeBlock.contains("menuExpanded = isOpen"))
        assertTrue("只应在关闭分支重置", closeBlock.contains("if (!isOpen) {"))
        assertTrue("关闭未回到一级菜单", closeBlock.contains("themeMenuShowsColumns = false"))
        assertTrue("关闭未作废冻结宽度", closeBlock.contains("themeMenuWidth = 0.dp"))
    }

    private fun codeOf(path: String): String = sourceOf(path)
        .lineSequence()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
        }
        .joinToString("\n")

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
        const val THEME_LIST = "io/legado/app/ui/book/read/sheet/ThemeListContent.kt"
    }
}
