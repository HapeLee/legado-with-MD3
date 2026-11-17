package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第五十二轮：替换净化列表项的「更多」菜单从 `dropdownContent` 插槽迁到声明式入口。
 *
 * 迁移动机：`SelectionItemCard.dropdownContent` 只能把弹层容器收编到 `AppAnchoredPanel`，
 * 菜单行仍是项目自绘的 `RoundDropdownMenuItem`，四引擎都拿不到原生 item。改走
 * `trailingAction` 槽自渲染 ⋮ + `AppActionMenu(anchor = Row)` 后，COUI / Miuix / Mochi
 * 分别由库渲染 `CouiMenuItem` / `DropdownItem` / `MochiItem`；Material 分支仍自绘，属已知边界。
 *
 * 本文件是**源码静态契约**：保证结构与稳定 ID 一致，不证明四引擎展开态外观、弹层落点与 TalkBack。
 */
class ReplaceRuleItemMenuContractTest {

    /** 列表项改走 `trailingAction` + `Row` 锚点；旧的任意内容插槽与自绘行必须归零。 */
    @Test
    fun listItemMenuGoesThroughDeclarativeRowAnchor() {
        val source = codeOf(SCREEN)
        assertEquals(
            "仍手画 RoundDropdownMenuItem（自绘行拿不到原生效果）",
            0,
            Regex("RoundDropdownMenuItem\\(").findAll(source).count(),
        )
        assertEquals(
            "仍在用 dropdownContent 插槽（该插槽只换容器、不换行）",
            0,
            Regex("dropdownContent\\s*=").findAll(source).count(),
        )
        assertTrue("⋮ 未放进 trailingAction 槽", source.contains("trailingAction = {"))
        assertTrue("缺少声明式命令入口", source.contains("AppActionMenu("))
        assertTrue("未声明 Row 锚点，Miuix 会退化成库图标按钮", source.contains("anchor = AppMenuAnchor.Row"))
        assertTrue("trigger 不是纯展示按钮", source.contains("SmallPlainButton("))
        assertTrue("trigger 仍自带点击节点，会与入口叠加", source.contains("onClick = null"))
        assertTrue("⋮ 图标丢失", source.contains("icon = AppIcons.MoreVert"))
        assertTrue(
            "逐条无障碍名称丢失",
            source.contains("moreContentDescription = stringResource("),
        )
        assertTrue(
            "Row 锚点未声明可访问名称",
            source.contains("title = moreContentDescription"),
        )
    }

    /** 三个命令都必须同时有常量声明与 `when(id)` 分派，避免拼错 ID 静默无动作。 */
    @Test
    fun everyActionHasDeclarationAndDispatchBranch() {
        val source = codeOf(SCREEN)
        ACTIONS.forEach { (id, constant) ->
            assertTrue("$id 缺少常量声明", source.contains("$constant = \"$id\""))
            assertTrue("$id 缺少分派分支", source.contains("$constant -> "))
            assertTrue("$id 未进入 groups", source.contains("id = $constant"))
        }
    }

    /** 删除项保持 `destructive` 落色通道，且仍先弹确认框、不直接删。 */
    @Test
    fun deleteStaysDestructiveAndKeepsConfirmDialog() {
        val source = codeOf(SCREEN)
        assertTrue(
            "删除项退化成普通命令（Material / COUI / Mochi 会丢危险色）",
            Regex("RULE_ACTION_DELETE.*destructive = true").containsMatchIn(source),
        )
        assertTrue(
            "列表项未回传待删规则",
            source.contains("onDelete = { showDeleteRuleDialog = ui.toEntity() }"),
        )
        assertTrue("确认框未再分派删除 intent", source.contains("ReplaceRuleIntent.DeleteRule(rule)"))
        assertFalse(
            "列表项直接删除了规则（跳过了确认框）",
            source.contains("onDelete = { onIntent(ReplaceRuleIntent.DeleteRule"),
        )
    }

    /** 公共插槽仍保留给其余调用方，本轮只迁这一处，不能顺手改组件 API。 */
    @Test
    fun cardSlotStaysForOtherCallers() {
        val card = codeOf(CARD)
        assertTrue(
            "卡片插槽被移除，会波及其余调用方",
            card.contains(
                "dropdownContent: @Composable (ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null"
            ),
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
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val SCREEN = "io/legado/app/ui/replace/ReplaceRuleScreen.kt"
        const val CARD = "io/legado/app/ui/widget/components/card/SelectionItemCard.kt"

        val ACTIONS = listOf(
            "to_top" to "RULE_ACTION_TO_TOP",
            "to_bottom" to "RULE_ACTION_TO_BOTTOM",
            "delete" to "RULE_ACTION_DELETE",
        )
    }
}
