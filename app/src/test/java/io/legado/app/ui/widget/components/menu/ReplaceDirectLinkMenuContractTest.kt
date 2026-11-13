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
 * **第四十四轮下调棘轮**：`ReplaceEditScreen.GroupSelector` 的 exposed 组合框已迁走
 * （见 `ExposedDropdownMenuBaselineContractTest`），整文件手画 `RoundDropdownMenu` 归零；
 * 但可编辑文本框本身保留，建议列表改由输入框旁的纯展示按钮 + [AppChoiceDropdown] 承载。
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
     * 第四十四轮下调：手画弹层归零、exposed 锚点移除；但**页面内自由输入**必须保留，
     * 防止后续为了「覆盖率」把输入框换成纯选择器。按第四十一轮用户口径，不验
     * 「退出后重进仍保留分组」。
     */
    @Test
    fun groupSelectorKeepsFreeTextInputWithChoiceTrigger() {
        val replace = sourceOf(REPLACE_EDIT)
        assertEquals(
            "ReplaceEditScreen 不应再有手画 RoundDropdownMenu",
            0,
            Regex("RoundDropdownMenu\\(").findAll(replace).count(),
        )
        assertFalse("仍保留 exposed 可编辑锚点", replace.contains("PrimaryEditable"))
        assertTrue("自由输入值丢失", replace.contains("value = currentGroup"))
        assertTrue("自由输入回调丢失", replace.contains("onValueChange = onGroupChange"))
        assertFalse("分组文本框被设成只读", replace.contains("readOnly = true"))
        assertTrue("建议列表未走 AppChoiceDropdown", replace.contains("AppChoiceDropdown("))
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
