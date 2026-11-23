package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 规则编辑 Sheet 的复制 / 粘贴菜单契约。
 *
 * 三处旧实现是逐行同构的 `Box { MediumTonalButton; RoundDropdownMenu }`，先迁成受控
 * [AppActionMenu]，再收进 sheet 的声明式操作槽：现在菜单是 `SheetAction.menu`，
 * 每个条目自带 `onClick`，业务侧不再需要「稳定 ID → 分派」的 `when`。
 *
 * 静态契约只保证结构与副作用接线一致，**不证明复制 / 粘贴行为正确**。
 */
class RuleClipboardMenuContractTest {
    @Test
    fun migratedSheetsUseDeclarativeMenuOnly() {
        FILES.forEach { path ->
            val source = sourceOf(path)
            assertFalse("$path 仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
            assertFalse("$path 仍自带 showMenu 开合状态", source.contains("showMenu"))
            assertFalse("$path 仍自己搭菜单入口", source.contains("AppActionMenu("))
            assertFalse(
                "$path 仍自带按钮 trigger，会与声明式按钮叠加",
                source.contains("MediumTonalButton("),
            )
            assertTrue("$path 未走声明式操作槽", source.contains("endSheetAction = SheetAction("))
            assertTrue("$path 的菜单未挂在按钮上", source.contains("menu = persistentListOf("))
        }
    }

    @Test
    fun everyClipboardActionHasDispatchBranch() {
        FILES.forEach { path ->
            val source = sourceOf(path)
            IDS.forEach { id ->
                assertDeclarativeItem(source, path, id)
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

/**
 * 断言某个 ID 已声明成菜单项，并且带上了副作用。
 *
 * 声明式菜单没有集中的 `when (id)` 分派，条目漏接 `onClick` 会静默变成点了没反应的死项，
 * 所以这里检查 `id = "..."` 到下一个条目之间的片段必须含 `onClick =`。
 */
internal fun assertDeclarativeItem(source: String, path: String, id: String) {
    val start = source.indexOf("id = \"$id\"")
    assertTrue("$path 未声明 $id", start >= 0)
    val nextItem = source.indexOf("SheetMenuAction(", start)
    val slice = if (nextItem > start) source.substring(start, nextItem) else source.substring(start)
    assertTrue("$path 的 $id 缺少 onClick 副作用", slice.contains("onClick ="))
}
