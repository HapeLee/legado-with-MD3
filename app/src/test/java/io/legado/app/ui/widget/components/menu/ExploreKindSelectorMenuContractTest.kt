package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十七轮：探索页「分类多类型」条目的选择菜单迁成受控 [AppActionMenu]。
 *
 * 这处菜单由 `ExploreKindMultiTypeItem` 内部的 `showSelector` 驱动，选项来自 `kind.chars`，
 * 选中后要写回 `state.updateValue(...)` 并执行 `kind.action`（或调用方传入的 `onRunAction`）。
 * 旧实现没有任何选中态标记，迁移时保持这一行为——不额外引入 `selected`，避免改观感。
 */
class ExploreKindSelectorMenuContractTest {

    @Test
    fun selectorMenuIsSingleControlledAppActionMenu() {
        val source = codeOf(ITEM)
        assertEquals("仍手画菜单", 0, Regex("RoundDropdownMenu").findAll(source).count())
        assertEquals(
            "应只剩一个受控弹层",
            1,
            Regex("AppActionMenu\\(").findAll(source).count(),
        )
        assertTrue("弹层未受控", source.contains("embeddedExpanded = showSelector"))
        assertTrue("受控弹层不应渲染 trigger", source.contains("trigger = {}"))
        assertTrue("锚点丢失", source.contains("modifier = Modifier.matchParentSize()"))
    }

    /** 选项由字符列表生成，选中后仍写回值并执行动作。 */
    @Test
    fun optionsFollowCharsAndKeepSideEffects() {
        val source = codeOf(ITEM)
        assertTrue("选项未由 chars 生成", source.contains("chars.mapIndexed { index, option ->"))
        assertTrue("ID 未用序号前缀", source.contains("id = \"\$ExploreKindSelectorIdPrefix\$index\""))
        assertTrue(
            "ID 未回解成序号",
            source.contains("removePrefix(ExploreKindSelectorIdPrefix).toIntOrNull()"),
        )
        assertTrue("序号未做边界校验", source.contains("index in chars.indices"))
        assertTrue("值未写回", source.contains("state.updateValue(option, onValueChange)"))
        assertTrue(
            "动作未执行",
            source.contains("if (onRunAction != null) onRunAction() else state.executeAction(kind.action)"),
        )
        assertTrue("选中未去重", source.contains("if (selected != option)"))
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
        const val ITEM = "io/legado/app/ui/widget/components/explore/ExploreKindMultiTypeItem.kt"
    }
}
