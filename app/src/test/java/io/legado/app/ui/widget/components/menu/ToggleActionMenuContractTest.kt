package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第七轮：「命令 + 独立开关」菜单迁移契约。
 *
 * 三处旧实现都是
 * `Box { MediumTonalButton(MoreVert); RoundDropdownMenu { ... isSelected = 开关态 ... } }`，
 * 开关项点击取反后 `dismiss()`，对应 [AppAction.checked]（独立勾选），
 * **不是** [AppChoiceDropdown] 的 `selectedId` 单选。
 *
 * 迁移前发现 `AppActionMenu.LegacyActions`（Material）只读 `action.selected`、
 * 丢弃 `checked`；Mochi / COUI / Miuix 三支都已映射。本轮一并补齐并用契约锁住，
 * 否则 Material 主题下勾选态会静默消失。
 *
 * 本轮刻意不迁的是 `BookSourceScreen` / `RssSourceImportDialog` 的两处混合菜单——开关项
 * 点击后**不 dismiss**（菜单保持打开以便连续切换），与入口「先 dismiss 再 onAction」冲突。
 * 该冲突已在第十轮用「导入面板配置页」解决（见 [BatchImportConfigPageContractTest]），
 * 入口本身**没有也不需要**「动作后保持展开」能力。三个迁移文件里列表项
 * `dropdownContent` 的 `RoundDropdownMenuItem` 属于另一套菜单，仍不在本轮范围。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明副作用、弹层定位或真机外观**。
 */
class ToggleActionMenuContractTest {

    @Test
    fun materialLegacyActionsMapCheckedToSelection() {
        val legacy = sourceOf(APP_ACTION_MENU)
            .substringAfter("private fun LegacyActions(")
            .substringBefore("\n@Composable")
        assertTrue(
            "Material 分支未把 checked 映射到 isSelected，开关勾选态会丢失",
            legacy.contains("isSelected = action.selected || action.checked == true"),
        )
        assertTrue(
            "Material 分支未把 checked 映射到 semantics.selected",
            legacy.contains("selected = action.selected || action.checked == true"),
        )
        assertTrue(
            "Material 分支的子项未把 checked 映射到 isSelected",
            legacy.contains("isSelected = child.selected || child.checked == true"),
        )
        assertTrue(
            "Material 分支的子项未把 checked 映射到 semantics.selected",
            legacy.contains("selected = child.selected || child.checked == true"),
        )
    }

    @Test
    fun migratedToggleMenusUseDeclarativeEntrypoint() {
        TARGETS.forEach { target ->
            val source = sourceOf(target.path)
            val slice = source.substringAfter("startAction = {").substringBefore("endAction = {")
            assertFalse(
                "${target.path} 仍手画 RoundDropdownMenu",
                slice.contains("RoundDropdownMenu("),
            )
            assertFalse(
                "${target.path} 仍自带菜单开合状态",
                listOf("showOptionsMenu", "menuExpanded", "expanded =").any { slice.contains(it) },
            )
            assertTrue("${target.path} 缺少 AppActionMenu", slice.contains("AppActionMenu("))
            assertTrue(
                "${target.path} 未用 AppAction.checked 表达独立开关",
                slice.contains("checked = "),
            )
            assertTrue("${target.path} 缺少纯展示 trigger", slice.contains("AppIcon("))
            assertFalse(
                "${target.path} 仍残留局部开合状态 ${target.localState}",
                source.contains(target.localState),
            )
        }
    }

    @Test
    fun everyToggleActionHasDispatchBranch() {
        ACTIONS.forEach { (path, ids) ->
            val source = sourceOf(path)
            ids.forEach { id ->
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

    private data class Target(val path: String, val localState: String)

    private companion object {
        const val APP_ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
        const val CONTENT_EDIT = "io/legado/app/ui/book/read/sheet/ContentEditSheet.kt"
        const val CHANGE_CHAPTER_SOURCE =
            "io/legado/app/ui/book/read/sheet/ChangeChapterSourceSheet.kt"
        const val CHANGE_SOURCE =
            "io/legado/app/ui/widget/components/changeSource/ChangeSourceSheet.kt"

        val TARGETS = listOf(
            Target(CONTENT_EDIT, "menuExpanded"),
            Target(CHANGE_CHAPTER_SOURCE, "showOptionsMenu"),
            Target(CHANGE_SOURCE, "showOptionsMenu"),
        )

        val ACTIONS = mapOf(
            CONTENT_EDIT to listOf("reset_content", "body_only"),
            CHANGE_CHAPTER_SOURCE to
                listOf("check_author", "load_info", "load_toc", "load_word_count"),
            CHANGE_SOURCE to
                listOf("check_author", "load_info", "load_toc", "load_word_count", "open_source_manage"),
        )
    }
}
