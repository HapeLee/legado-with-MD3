package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第八轮：`ui/book/knowledge/` 三处纯单选菜单的迁移契约。
 *
 * 旧实现逐行同构：`Box { GlassCard(onClick = showDropdown); RoundDropdownMenu { ... } }`，
 * 选项是常量表 / 参数列表，选中值显示在卡片上。迁移到
 * [AppChoiceDropdown] 的 `compact = true` 形态：trigger 去掉 `onClick` 保持纯展示，
 * 由入口包装成唯一可点节点，四套引擎各自渲染原生单选弹层。
 *
 * [AppChoiceDropdown] 用 `selectedId` 表达单选高亮，与上一轮 `AppAction.checked`
 * 的「独立开关」不同；`BookCharacterDetailScreen` 的角色选择器还带
 * 「再次点选已选项则清空」语义，必须在 `onSelect` 里保留。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明弹层定位、真机外观或 TalkBack 行为**。
 */
class KnowledgeChoiceMenuContractTest {

    @Test
    fun migratedSelectorsUseAppChoiceDropdown() {
        EXPECTED.forEach { (path, count) ->
            val source = sourceOf(path)
            assertFalse("$path 仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
            assertFalse("$path 仍自带 showDropdown 开合状态", source.contains("showDropdown"))
            assertTrue("$path 未用 compact = true", source.contains("compact = true"))
            assertTrue("$path 缺少 AppChoiceOption", source.contains("AppChoiceOption("))
            assertEquals(
                "$path 的 AppChoiceDropdown 数量",
                count,
                Regex("AppChoiceDropdown\\(").findAll(source).count(),
            )
            assertEquals(
                "$path 的纯展示 trigger modifier 数量",
                count,
                Regex("modifier = this\\.modifier").findAll(source).count(),
            )
        }
    }

    @Test
    fun everySelectorHasSelectionContract() {
        EXPECTED.forEach { (path, count) ->
            val source = sourceOf(path)
            assertEquals(
                "$path 的 selectedId 数量",
                count,
                Regex("selectedId =").findAll(source).count(),
            )
            assertEquals(
                "$path 的 onSelect 数量",
                count,
                Regex("onSelect =").findAll(source).count(),
            )
        }
    }

    @Test
    fun roleSelectorKeepsDeselectOnReselect() {
        assertTrue(
            "ProfileRoleDropdown 的「再次点选已选项则清空」语义丢失",
            sourceOf(CHARACTER).contains("if (id == selectedRole) \"\" else id"),
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
        const val KNOWLEDGE = "io/legado/app/ui/book/knowledge/BookKnowledgeDetailScreen.kt"
        const val CHARACTER = "io/legado/app/ui/book/knowledge/BookCharacterDetailScreen.kt"

        val EXPECTED = mapOf(KNOWLEDGE to 1, CHARACTER to 2)
    }
}
