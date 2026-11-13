package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十五轮：`FontSelectSheet` 的系统字体菜单迁成受控 [AppActionMenu]。
 *
 * 这处菜单挂在 sheet 的 `startAction` 槽里，条目由传入的 `Array<String>` 动态生成，
 * 选中后既要关菜单也要关整个 sheet。迁移后：
 * - 只有一个受控弹层，锚点是字体按钮所在的 `Box`；
 * - 条目 ID 用序号（字体名可能重复，同名不同文件），回解后按 `typefaces.indices` 校验；
 * - 选中仍先调 `onSelectSystemTypeface(index)`，再 `onDismissRequest()` 关 sheet。
 *
 * **已知差异**：旧实现用 `ProvideAppDensity` 包住菜单项，避免系统大字号/高密度把菜单项撑大；
 * 受控弹层的内容由组件内部渲染，这个包裹用不上了。若真机上出现字号异常，需要回头在
 * `AppActionMenu` 侧提供密度钩子，而不是在这里补。
 */
class FontTypefaceMenuContractTest {

    @Test
    fun typefaceMenuIsSingleControlledAppActionMenu() {
        val source = codeOf(SHEET)
        assertEquals("仍手画菜单", 0, Regex("RoundDropdownMenu").findAll(source).count())
        assertEquals(
            "应只剩一个受控弹层",
            1,
            Regex("AppActionMenu\\(").findAll(source).count(),
        )
        assertTrue("弹层未受控", source.contains("embeddedExpanded = showTypefaceMenu"))
        assertTrue("受控弹层不应渲染 trigger", source.contains("trigger = {}"))
        assertTrue("锚点丢失", source.contains("modifier = Modifier.matchParentSize()"))
    }

    /** 条目由字体数组驱动，选中后按序号回到原回调。 */
    @Test
    fun typefaceItemsFollowSystemListAndKeepSelection() {
        val source = codeOf(SHEET)
        assertTrue("条目未由字体列表生成", source.contains("typefaces.mapIndexed { index, name ->"))
        assertTrue(
            "ID 未用序号前缀",
            source.contains("id = \"\$FontTypefaceIdPrefix\$index\""),
        )
        assertTrue(
            "ID 未回解成序号",
            source.contains("removePrefix(FontTypefaceIdPrefix).toIntOrNull()"),
        )
        assertTrue("序号未做边界校验", source.contains("index in typefaces.indices"))
        assertTrue("选中回调丢失", source.contains("selectTypeface(index)"))
        assertTrue("选完未关闭整个 sheet", source.contains("onDismissRequest()"))
    }

    /** 记录已知差异：不再有 `ProvideAppDensity` 包裹。 */
    @Test
    fun typefaceMenuNoLongerForcesAppDensity() {
        assertFalse(
            "仍在期待 ProvideAppDensity 包裹菜单项",
            codeOf(SHEET).contains("ProvideAppDensity"),
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
        const val SHEET = "io/legado/app/ui/widget/components/FontSelectSheet.kt"
    }
}
