package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第四十轮建立的行为基线；第四十四轮阶段 C 收尾，两处 `ExposedDropdownMenuBox` 均已迁走：
 *
 * - `BookInfoEditScreen.BookTypeDropdown` → `SettingItem` 单选行（第四十三轮，
 *   见 `BookTypeSettingItemContractTest`）。
 * - `ReplaceEditScreen.GroupSelector` → 可编辑输入框保留，下拉触发按钮移到输入框旁，
 *   用 Medium 系列纯展示按钮交给 `AppChoiceDropdown` 承载建议列表（本轮）。
 *
 * 本文件现在钉住「阶段 C 归零」与 `GroupSelector` 的硬约束：**页面内自由输入**必须保留
 * （第四十一轮用户口径：不验「退出后重进仍保留分组」），分组管理按钮仍在输入框右侧。
 * 静态断言不证明四引擎弹层落点、软键盘与 TalkBack。
 */
class ExposedDropdownMenuBaselineContractTest {

    /** 阶段 C 收尾：两处都不再手画弹层，也不再有 exposed 锚点。 */
    @Test
    fun stageCHasNoHandDrawnDropdownLeft() {
        listOf(BOOK_INFO_EDIT, REPLACE_EDIT).forEach { path ->
            val source = codeOf(path)
            assertEquals(
                "$path 仍有手画 RoundDropdownMenu",
                0,
                Regex("RoundDropdownMenu\\(").findAll(source).count(),
            )
            assertFalse("$path 仍保留 ExposedDropdownMenuBox", source.contains("ExposedDropdownMenuBox("))
            assertFalse("$path 仍保留 PrimaryEditable 锚点", source.contains("PrimaryEditable"))
            assertFalse("$path 仍保留展开态箭头", source.contains("ExposedDropdownMenuDefaults"))
        }
    }

    /** 分组：文本框必须可自由输入，建议列表只做「选中即回填」。 */
    @Test
    fun groupSelectorKeepsFreeTextInput() {
        val source = codeOf(REPLACE_EDIT)
        assertTrue("分组文本框被换成只读", source.contains("value = currentGroup"))
        assertTrue("自由输入回调丢失", source.contains("onValueChange = onGroupChange"))
        assertFalse("分组文本框不应声明 readOnly", source.contains("readOnly = true"))
        assertTrue("分组标签丢失", source.contains("label = stringResource(R.string.group)"))
        assertTrue("默认占位提示丢失", source.contains("placeholder = { AppText(\"默认\") }"))
        assertTrue("组合框权重丢失", source.contains("modifier = Modifier.weight(1f)"))
        assertTrue("分组背景色丢失", source.contains("backgroundColor = backgroundColor"))
    }

    /** 下拉按钮在输入框旁：Medium 纯展示 trigger，由入口包唯一可点节点。 */
    @Test
    fun groupSelectorUsesPureDisplayMediumTrigger() {
        val source = codeOf(REPLACE_EDIT)
        assertEquals(
            "应只有一个建议列表入口",
            1,
            Regex("AppChoiceDropdown\\(").findAll(source).count(),
        )
        assertTrue("未启用紧凑 trigger 形态", source.contains("compact = true"))
        assertTrue("入口缺少稳定无障碍名称", source.contains("title = stringResource(R.string.group)"))
        assertTrue("下拉按钮不是 Medium 系列", source.contains("MediumPlainButton("))
        assertTrue("trigger 不应自带点击节点", source.contains("onClick = null"))
        assertTrue(
            "COUI 触点锚点未透传 scope.modifier",
            source.contains("modifier = this.modifier"),
        )
        assertTrue("下拉图标丢失", source.contains("icon = AppIcons.ExpandMore"))
        assertTrue(
            "按钮缺少可本地化的无障碍名称",
            source.contains("contentDescription = stringResource(R.string.expand)"),
        )
    }

    /** 稳定 ID 前缀 + 边界校验：分组名可重复、可含任意字符，不能拿名字当 ID。 */
    @Test
    fun suggestionIdsAreStableAndBoundsChecked() {
        val source = codeOf(REPLACE_EDIT)
        assertTrue("缺少稳定 ID 前缀", source.contains("private const val GROUP_OPTION_ID_PREFIX"))
        assertTrue("选项未按序号生成", source.contains("allGroups.mapIndexed { index, group ->"))
        assertTrue(
            "选项 ID 不是带前缀的序号",
            source.contains("""AppChoiceOption(id = "${'$'}GROUP_OPTION_ID_PREFIX${'$'}index", label = group)"""),
        )
        assertTrue("未转成 ImmutableList", source.contains(".toImmutableList()"))
        assertTrue(
            "当前值不在建议列表时未让 selectedId 落空",
            source.contains("allGroups.indexOfFirst { it == currentGroup }"),
        )
        assertTrue("缺少落空判定", source.contains(".takeIf { it >= 0 }"))
        assertTrue("回解未去前缀", source.contains("id.removePrefix(GROUP_OPTION_ID_PREFIX).toIntOrNull()"))
        assertTrue("回解未做越界校验", source.contains("?.let { allGroups.getOrNull(it) }"))
        assertTrue("未回填分组", source.contains("?.let(onGroupChange)"))
    }

    /** 分组管理按钮仍在输入框右侧，回调不变。 */
    @Test
    fun manageButtonRemainsBesideTheField() {
        val source = codeOf(REPLACE_EDIT)
        assertTrue("分组管理回调丢失", source.contains("onClick = onManageClick"))
        assertTrue("分组管理图标丢失", source.contains("icon = Icons.Default.Settings"))
        assertTrue(
            "分组管理无障碍名称丢失",
            source.contains("contentDescription = stringResource(R.string.group_management)"),
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
        const val BOOK_INFO_EDIT = "io/legado/app/ui/book/info/edit/BookInfoEditScreen.kt"
        const val REPLACE_EDIT = "io/legado/app/ui/replace/edit/ReplaceEditScreen.kt"
    }
}
