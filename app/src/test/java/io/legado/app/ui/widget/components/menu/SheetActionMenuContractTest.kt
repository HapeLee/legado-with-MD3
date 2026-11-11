package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sheet `startAction` / `endAction` 命令菜单的迁移契约（第六轮）。
 *
 * 这四处与第五轮的规则编辑剪贴板菜单同构：原本都是
 * `Box { MediumTonalButton(onClick = showMenu); RoundDropdownMenu { ... } }`，
 * 迁移后由 [AppActionMenu] 统一渲染按钮与开合，业务只保留「稳定 ID → 副作用」分派。
 *
 * [CloudTtsScreen] 同文件还有形态不同的下拉（第十七轮后只剩区域混合菜单），
 * 因此该文件的断言只覆盖 `speak_engine` 编辑器这一处 `startAction` 区域，
 * 并单独用棘轮断言锁住「没有顺手改掉其它菜单」。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明各命令的副作用正确**，也不覆盖真机外观。
 */
class SheetActionMenuContractTest {

    @Test
    fun migratedActionMenusUseDeclarativeEntrypoint() {
        TARGETS.forEach { target ->
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
            assertTrue("${target.path} 缺少 AppActionMenu", slice.contains("AppActionMenu("))
            assertTrue("${target.path} 缺少纯展示 trigger", slice.contains("AppIcon("))
        }
    }

    @Test
    fun everyActionHasDispatchBranch() {
        ACTIONS.forEach { (path, ids) ->
            val source = sourceOf(path)
            ids.forEach { id ->
                assertTrue("$path 未声明 $id", source.contains("\"$id\""))
                assertTrue("$path 缺少 $id 的分派分支", source.contains("\"$id\" ->"))
            }
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

        val TARGETS = listOf(
            Target("io/legado/app/ui/login/SourceLoginSheets.kt"),
            Target("io/legado/app/ui/main/bookshelf/GroupManageSheet.kt"),
            Target("io/legado/app/ui/book/read/sheet/HighlightRuleConfigSheet.kt"),
            Target(
                CLOUD_TTS,
                // `speak_engine` 标题在同文件另一个 Sheet 也出现，用编辑器独有的 dismiss intent 定位。
                regionStart = "onDismissRequest = { onIntent(CloudTtsIntent.DismissHttpTtsEditor) }",
                regionEnd = "endAction = {",
            ),
        )

        val ACTIONS = mapOf(
            "io/legado/app/ui/login/SourceLoginSheets.kt" to
                listOf("show_login_header", "delete_login_header", "show_log"),
            "io/legado/app/ui/main/bookshelf/GroupManageSheet.kt" to
                listOf("group_add", "ai_auto_group", "tag_group_add_rule", "tag_group_sync"),
            "io/legado/app/ui/book/read/sheet/HighlightRuleConfigSheet.kt" to
                listOf("highlight_rule_presets", "import_action", "export", "copy_url"),
            CLOUD_TTS to listOf("copy_text", "paste_source"),
        )
    }
}
