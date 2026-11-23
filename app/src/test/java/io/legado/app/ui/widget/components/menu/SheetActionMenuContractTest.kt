package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sheet 顶部命令菜单的契约。
 *
 * 这些菜单原本是 `Box { MediumTonalButton(onClick = showMenu); RoundDropdownMenu { ... } }`，
 * 先迁到受控 [AppActionMenu]，再收进 sheet 的声明式操作槽：菜单是 `SheetAction.menu`，
 * 条目自带 `onClick`，按钮由各引擎头部渲染（COUI 下是库按钮）。
 *
 * [GroupManageSheet] 的右侧槽在非编辑态是菜单、编辑态是「重置封面」，两态现在都是
 * [SheetAction]，菜单态走 `menu`，所以整条头部可以交给引擎（COUI 下即库工具栏）。
 *
 * 静态契约只保证结构与副作用接线一致，**不证明各命令的副作用正确**，也不覆盖真机外观。
 */
class SheetActionMenuContractTest {

    @Test
    fun migratedActionMenusUseDeclarativeEntrypoint() {
        DECLARATIVE.forEach { target ->
            val source = sourceOf(target.path)
            val slice = if (target.regionStart == null) {
                source
            } else {
                source.substringAfter(target.regionStart).substringBefore(target.regionEnd!!)
            }
            assertFalse(
                "${target.path} 仍手画 RoundDropdownMenu",
                slice.contains("RoundDropdownMenu("),
            )
            assertFalse(
                "${target.path} 仍自带菜单开合状态",
                listOf("showMenu", "menuExpanded", "expanded").any { slice.contains(it) },
            )
            assertFalse("${target.path} 仍自己搭菜单入口", slice.contains("AppActionMenu("))
            assertTrue("${target.path} 未走声明式操作槽", slice.contains("SheetAction("))
            assertTrue("${target.path} 的菜单未挂在按钮上", slice.contains("menu = persistentListOf("))
        }
    }

    @Test
    fun everyActionHasDispatchBranch() {
        DECLARATIVE_ACTIONS.forEach { (path, ids) ->
            val source = sourceOf(path)
            ids.forEach { id -> assertDeclarativeItem(source, path, id) }
        }
    }

    /**
     * [CloudTtsScreen] 的其余下拉是不同形态（动态单选 / 混合命令项），迁移其它簇时下调此棘轮。
     * 第十三轮迁掉顶栏命令菜单后 7 → 6；第十七轮迁掉 5 处纯单选后 6 → 1（只剩区域混合菜单）。
     */
    @Test
    fun cloudTtsKeepsOtherMenusUntouched() {
        val source = sourceOf(CLOUD_TTS)
        assertEquals(
            "CloudTtsScreen 的手画菜单应已归零",
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

    private data class Target(
        val path: String,
        val regionStart: String? = null,
        val regionEnd: String? = null,
    )

    private companion object {
        const val CLOUD_TTS = "io/legado/app/ui/book/readaloud/cloudtts/CloudTtsScreen.kt"
        const val GROUP_MANAGE = "io/legado/app/ui/main/bookshelf/GroupManageSheet.kt"

        val DECLARATIVE = listOf(
            Target(GROUP_MANAGE),
            Target("io/legado/app/ui/login/SourceLoginSheets.kt"),
            Target("io/legado/app/ui/book/read/sheet/HighlightRuleConfigSheet.kt"),
            Target(
                CLOUD_TTS,
                // `speak_engine` 标题在同文件另一个 Sheet 也出现，用编辑器独有的 dismiss intent 定位。
                regionStart = "onDismissRequest = { onIntent(CloudTtsIntent.DismissHttpTtsEditor) }",
                regionEnd = "endSheetAction = ",
            ),
        )

        val DECLARATIVE_ACTIONS = mapOf(
            "io/legado/app/ui/login/SourceLoginSheets.kt" to
                listOf("show_login_header", "delete_login_header", "show_log"),
            "io/legado/app/ui/book/read/sheet/HighlightRuleConfigSheet.kt" to
                listOf("highlight_rule_presets", "import_action", "export", "copy_url"),
            CLOUD_TTS to listOf("copy_text", "paste_source"),
            GROUP_MANAGE to
                listOf("group_add", "ai_auto_group", "tag_group_add_rule", "tag_group_sync"),
        )
    }
}
