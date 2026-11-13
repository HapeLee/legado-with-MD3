package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第二十九轮：气泡包列表项（`BubbleConfigScreen.BubbleEntryRow`）的 ⋮ 菜单迁到受控 [AppActionMenu]。
 *
 * 这处此前无法迁，卡的是 `destructive` 没有落色通道——迁移会让「删除」丢掉红字。
 * 第二十七轮补齐入口的 `destructive` 之后（Material 落文字色 + 图标、COUI 落图标、Mochi 原生），
 * 这里把调用点写死的 `color = LegadoTheme.colorScheme.error` 换成 `AppAction.destructive`。
 *
 * 内置包只有「应用」一项，这条门控必须留在分组构建里，不能并成「全都显示、点了没反应」。
 * ⋮ 用 SmallButton 系列的 `SmallPlainButton`（32dp / 18dp 图标），与列表项 trailing 按钮同几何。
 * 静态契约只锁结构与 ID；COUI 上删除项没有图标可染、文字保持默认色，这点仍需真机确认。
 */
class BubbleEntryMenuContractTest {

    @Test
    fun bubbleEntryMenuIsControlledAndUsesStableIds() {
        val source = codeOf(BUBBLE_CONFIG)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu"))
        assertTrue("未把开合交给页面", source.contains("embeddedExpanded = menuExpanded"))
        assertTrue("未接页面关闭回调", source.contains("onDismissRequest = { menuExpanded = false }"))
        assertTrue("受控模式下 trigger 应为空", source.contains("trigger = {}"))
        assertTrue("缺少有尺寸的锚点容器", source.contains("modifier = Modifier.matchParentSize()"))
        IDS.forEach { (constant, id) ->
            assertTrue("未声明 $id", source.contains("$constant(\"$id\","))
            assertTrue("缺少 $constant 分派", source.contains("$constant.id ->"))
        }
    }

    /**
     * ⋮ 走 SmallButton 系列（`SmallPlainButton` → `SeriesButton`：32dp 容器 / 18dp 图标），
     * 与列表项 trailing 图标按钮同一套几何；`AppIconButton` 是 M3/Miuix 的 `IconButton` 直通，
     * 不属于 series，会在这类列表里偏大且换引擎时形状不一致。
     */
    @Test
    fun moreButtonUsesSmallButtonSeries() {
        val source = codeOf(BUBBLE_CONFIG)
        assertTrue("⋮ 未使用 SmallPlainButton", source.contains("SmallPlainButton("))
        assertFalse("⋮ 仍用 AppIconButton", source.contains("AppIconButton("))
        assertTrue(
            "⋮ 缺少无障碍名称",
            source.contains("contentDescription = stringResource(R.string.bubble_more)"),
        )
    }

    /** 「应用」对内置包可用，编辑 / 导出 / 删除只对本地包出现。 */
    @Test
    fun builtinPackageKeepsApplyOnlyGate() {
        assertTrue(
            "内置包门控丢失（会出现点了没反应的项）",
            codeOf(BUBBLE_CONFIG).contains(
                "if (isBuiltin && action != BubbleEntryMenuAction.Apply) return@mapNotNull null",
            ),
        )
    }

    /** 删除项改走 destructive，调用点不再写死 error 色。 */
    @Test
    fun deleteItemUsesDestructiveFlag() {
        val source = codeOf(BUBBLE_CONFIG)
        assertTrue(
            "删除项未标记 destructive",
            source.contains("destructive = action == BubbleEntryMenuAction.Delete"),
        )
        assertFalse(
            "调用点仍在写死 error 色",
            source.contains("color = LegadoTheme.colorScheme.error"),
        )
    }

    /** 入口的 destructive 落色能力是本轮前提，退化会静默让删除项变回普通色。 */
    @Test
    fun entryStillPaintsDestructiveOnMaterialAndCoui() {
        val entry = codeOf(APP_ACTION_MENU)
        assertTrue(
            "Material 分支未落 destructive 文字色",
            entry.contains("color = if (action.destructive) LegadoTheme.colorScheme.error"),
        )
        assertTrue(
            "COUI 分支未落 destructive 图标色",
            entry.contains("iconTint = if (action.destructive) destructiveTint else null"),
        )
    }

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
        const val BUBBLE_CONFIG = "io/legado/app/ui/config/bubble/BubbleConfigScreen.kt"
        const val APP_ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
        val IDS = listOf(
            "Apply" to "bubble_apply",
            "Edit" to "bubble_edit",
            "ExportZip" to "bubble_export_zip",
            "Delete" to "bubble_delete",
        )
    }
}
