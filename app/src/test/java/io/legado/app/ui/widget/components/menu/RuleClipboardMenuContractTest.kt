package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 规则编辑 Sheet 的复制 / 粘贴菜单迁移契约：按钮与开合统一交给 [AppActionMenu] 的
 * 四引擎分支，业务侧只保留「稳定 ID → 副作用」分派。
 *
 * 这三处旧实现是逐行同构的 `Box { MediumTonalButton; RoundDropdownMenu }`，
 * 迁移后 trigger 由入口渲染（Mochi 下即用户调好的 `MochiBarIconButton`）。
 * 静态契约只保证结构与 ID 一致性，**不证明复制 / 粘贴副作用正确**。
 */
class RuleClipboardMenuContractTest {
    @Test
    fun migratedSheetsUseDeclarativeMenuOnly() {
        FILES.forEach { path ->
            val source = sourceOf(path)
            assertFalse("$path 仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
            assertFalse("$path 仍自带 showMenu 开合状态", source.contains("showMenu"))
            // startAction 的关闭按钮仍是 MediumTonalButton，不能用「文件里不含它」判定迁移。
            assertFalse(
                "$path 的 endAction 仍自带按钮 trigger，会与入口按钮叠加",
                source.substringAfter("endAction = {").contains("MediumTonalButton("),
            )
            assertTrue("$path 缺少 AppActionMenu", source.contains("AppActionMenu("))
            assertTrue("$path 缺少纯展示 trigger", source.contains("AppIcons.MoreVert"))
        }
    }

    @Test
    fun everyClipboardActionHasDispatchBranch() {
        FILES.forEach { path ->
            val source = sourceOf(path)
            IDS.forEach { id ->
                assertTrue("$path 未声明 $id", source.contains("\"$id\""))
                assertTrue("$path 缺少 $id 的分派分支", source.contains("\"$id\" ->"))
            }
        }
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
        val FILES = listOf(
            "io/legado/app/ui/widget/components/rules/RuleEditSheet.kt",
            "io/legado/app/ui/tagGroupRule/TagGroupRuleEditSheet.kt",
            "io/legado/app/ui/highlightTagRule/HighlightTagRuleEditSheet.kt",
        )
        val IDS = listOf("copy_rule", "paste_rule")
    }
}
