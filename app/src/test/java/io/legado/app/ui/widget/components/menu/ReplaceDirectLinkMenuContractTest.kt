package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 替换规则编辑页顶栏命令菜单 + 直链上传配置 Sheet 规则菜单迁移契约。
 *
 * - `ReplaceEditScreen` 顶栏 MoreVert 原是 `TopBarActionButton(onClick) + RoundDropdownMenu`
 *   （复制 / 粘贴规则两项），迁移后按钮与开合交给 [AppActionMenu] 的 `Icon` 分支。
 * - `DirectLinkUploadBottomSheet` 的 `endAction` 原是
 *   `Box { MediumTonalButton; RoundDropdownMenu }`（导入默认规则 / 复制 / 粘贴），
 *   三项都带 `leadingIcon`，迁移后改 `AppAction.icon`。
 *
 * **刻意保留**：`ReplaceEditScreen` 的 `GroupSelector` 是「可编辑文本框 + 分组建议」的
 * `ExposedDropdownMenuBox` 组合框（`PrimaryEditable` 锚点），不是纯选择器；
 * [AppChoiceDropdown] 只能选不能输入，会丢掉自由填分组的能力，所以整文件保留 1 处
 * `RoundDropdownMenu`，用棘轮锁住。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明剪贴板、预设选择或 Intent 副作用正确**。
 */
class ReplaceDirectLinkMenuContractTest {

    @Test
    fun migratedMenusUseDeclarativeEntrypoint() {
        val replace = sourceOf(REPLACE_EDIT)
        val topBar = replace.substringAfter("actions = {").substringBefore("scrollBehavior =")
        assertFalse("顶栏仍手画 RoundDropdownMenu", topBar.contains("RoundDropdownMenu("))
        assertFalse("仍自带 showMenu 开合状态", replace.contains("showMenu"))
        assertTrue("顶栏缺少 AppActionMenu", topBar.contains("AppActionMenu("))
        assertTrue("顶栏缺少纯展示 trigger", topBar.contains("AppIcons.MoreVert"))

        val direct = sourceOf(DIRECT_LINK)
        assertFalse("直链上传 Sheet 仍手画 RoundDropdownMenu", direct.contains("RoundDropdownMenu("))
        assertFalse("直链上传 Sheet 仍自带 showMenu 开合状态", direct.contains("showMenu"))
        assertTrue("直链上传 Sheet 缺少 AppActionMenu", direct.contains("AppActionMenu("))
        val endAction = direct.substringAfter("endAction = {").substringBefore("onDismissRequest")
        assertFalse(
            "endAction 仍自带按钮 trigger，会与入口按钮叠加",
            endAction.contains("MediumTonalButton("),
        )
        assertTrue("endAction 缺少纯展示 trigger", endAction.contains("AppIcon("))
    }

    @Test
    fun everyActionHasDeclarationAndDispatchBranch() {
        ACTIONS.forEach { (path, ids) ->
            val source = sourceOf(path)
            ids.forEach { id ->
                assertTrue("$path 未声明 $id", source.contains("\"$id\""))
                assertTrue("$path 缺少 $id 的分派分支", source.contains("\"$id\" ->"))
            }
        }
    }

    /**
     * 反向棘轮：`GroupSelector` 的可编辑组合框必须留着，防止后续为了「覆盖率」
     * 把它换成纯选择器而丢掉自由输入。
     */
    @Test
    fun groupSelectorKeepsEditableComboBox() {
        val replace = sourceOf(REPLACE_EDIT)
        assertEquals(
            "ReplaceEditScreen 应只剩 GroupSelector 那 1 处可编辑组合框",
            1,
            Regex("RoundDropdownMenu\\(").findAll(replace).count(),
        )
        assertTrue(
            "GroupSelector 的可编辑锚点不该被移除",
            replace.contains("ExposedDropdownMenuAnchorType.PrimaryEditable"),
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
        const val REPLACE_EDIT = "io/legado/app/ui/replace/edit/ReplaceEditScreen.kt"
        const val DIRECT_LINK = "io/legado/app/ui/config/otherConfig/DirectLinkUploadBottomSheet.kt"

        val ACTIONS = mapOf(
            REPLACE_EDIT to listOf("copy_rule", "paste_rule"),
            DIRECT_LINK to
                listOf("import_default_rule", "copy_direct_link_rule", "paste_direct_link_rule"),
        )
    }
}
