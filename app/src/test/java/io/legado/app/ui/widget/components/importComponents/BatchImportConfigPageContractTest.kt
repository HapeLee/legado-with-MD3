package io.legado.app.ui.widget.components.importComponents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 导入面板「配置页 / 列表页」两页切换契约。
 *
 * 背景：书源导入与 RSS 源导入原本在 `BatchImportDialog` 的 `topBarActions` 里塞了一个混合菜单——
 * 命令项点击后 `dismiss()`，3 个 keep 开关点击后**不** `dismiss()`（菜单保持展开以便连续勾选）。
 * 这种「部分项保持展开」的语义 `AppActionMenu` 无法表达，而为 2 个页面扩展入口能力不划算。
 *
 * 现改为：`BatchImportDialog` 提供可选 `configContent`，顶栏用「配置 ↔ 列表」切换按钮；
 * 配置页放常驻的 [SwitchSettingItem]（默认值直接回传，不再做 `!旧值` 翻转），
 * 列表页只保留全选与导入。混合菜单随之消失。
 *
 * 静态契约只保证结构与取值方向，**不证明布局、滚动与真机外观**。
 */
class BatchImportConfigPageContractTest {

    @Test
    fun batchImportDialogProvidesConfigPage() {
        val source = sourceOf(IMPORT_COMPONENTS)
        assertTrue(
            "缺少可选配置页参数",
            source.contains("configContent: (@Composable () -> Unit)? = null"),
        )
        assertTrue("缺少配置页状态", source.contains("val showConfigPage ="))
        assertTrue("缺少「配置 ↔ 列表」切换按钮", source.contains("Icons.Default.Settings"))
        assertTrue(
            "配置页应隐藏全选与导入按钮",
            source.contains("endAction = if (!isEditing && !showConfigPage && selectedCount > 0)"),
        )
        assertTrue(
            "配置页应可滚动，避免小屏被截断",
            source.contains(".verticalScroll(rememberScrollState())"),
        )
    }

    @Test
    fun bookSourceImportUsesConfigPageInsteadOfMixedMenu() {
        val source = sourceOf(BOOK_SOURCE)
        assertFalse("仍残留混合菜单开合状态", source.contains("showImportOptions"))
        assertTrue("缺少 configContent", source.contains("configContent = {"))
        assertEquals(
            "配置页应有三个常驻开关",
            3,
            Regex("SwitchSettingItem\\(").findAll(configBlockOf(source)).count(),
        )
        // 棘轮：另一处是「自定义分组」对话框里的「新增分组」开关，不属于配置页。
        assertEquals(
            "BookSourceScreen 应为配置页 3 处 + 分组对话框 1 处",
            4,
            Regex("SwitchSettingItem\\(").findAll(source).count(),
        )
        assertTrue("保留原名应直接回传开关新值", source.contains("SetImportKeepName(it)"))
        assertTrue("保留分组应直接回传开关新值", source.contains("SetImportKeepGroup(it)"))
        assertTrue("保留启用应直接回传开关新值", source.contains("SetImportKeepEnable(it)"))
        // 棘轮：行内 BookSourceItemMenu 本轮刻意不动；该处在第二十轮
        // `f648f9302` 补齐 Row 锚点后也已迁走，旧组件归零。
        assertEquals(
            "BookSourceScreen 不应再手画 RoundDropdownMenu",
            0,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
    }

    @Test
    fun rssSourceImportUsesConfigPageInsteadOfMixedMenu() {
        val source = sourceOf(RSS_IMPORT)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu"))
        assertFalse("仍残留 showMenu 开合状态", source.contains("showMenu"))
        assertFalse("ImportOption 包装应已删除", source.contains("ImportOption("))
        assertTrue("缺少 configContent", source.contains("configContent = {"))
        assertEquals(
            "配置页应有三个常驻开关",
            3,
            Regex("SwitchSettingItem\\(").findAll(configBlockOf(source)).count(),
        )
        // 棘轮：第 4 处在 `ImportGroupDialog`（新增分组开关），与配置页无关，保持原样。
        assertEquals(
            "RSS 导入对话框应为配置页 3 处 + 分组对话框 1 处",
            4,
            Regex("SwitchSettingItem\\(").findAll(source).count(),
        )
        assertTrue(source.contains("KeepName(it)"))
        assertTrue(source.contains("KeepGroup(it)"))
        assertTrue(source.contains("KeepEnable(it)"))
    }

    /**
     * 只取 `configContent` 那段 lambda，避免把分组对话框里的「新增分组」开关算进配置页。
     * 两处调用都在 `configContent` 之后紧跟 `itemTitle`。
     */
    private fun configBlockOf(source: String): String =
        source.substringAfter("configContent = {").substringBefore("itemTitle = {")

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
        const val IMPORT_COMPONENTS =
            "io/legado/app/ui/widget/components/importComponents/ImportComponents.kt"
        const val BOOK_SOURCE = "io/legado/app/ui/book/source/manage/BookSourceScreen.kt"
        const val RSS_IMPORT = "io/legado/app/feature/rss/source/RssSourceImportDialog.kt"
    }
}
