package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `FontSelectSheet` 系统字体菜单的契约。
 *
 * 菜单挂在 sheet 的左侧操作槽里，条目由传入的 `Array<String>` 动态生成，选中后既要关菜单
 * 也要关整个 sheet。当前形态：左侧操作走声明式 `startSheetActions`，字体菜单是
 * `SheetAction.menu`，按钮由各引擎自己的头部渲染（COUI 下是库按钮）——
 * 因此业务侧不再需要 `AppActionMenu` 或局部开合状态。
 *
 * - 调用方按钮与字体菜单同侧共存：`buildList { startSheetAction?.let(::add) }` 合并；
 * - 条目 ID 用序号（字体名可能重复，同名不同文件），点击直接带下标，不再回解字符串；
 * - 选中仍先调 `onSelectSystemTypeface(index)`，再 `onDismissRequest()` 关 sheet。
 *
 * **已知差异**：旧实现用 `ProvideAppDensity` 包住菜单项，避免系统大字号/高密度把菜单项撑大；
 * 现在条目由 `SheetAction.menu` 经 `AppActionMenu` 渲染，这个包裹用不上了。若真机上出现
 * 字号异常，需要回头在 `AppActionMenu` 侧提供密度钩子，而不是在这里补。
 */
class FontTypefaceMenuContractTest {

    @Test
    fun typefaceMenuIsDeclarativeMenuOnAButton() {
        val source = codeOf(SHEET)
        assertEquals("仍手画菜单", 0, Regex("RoundDropdownMenu").findAll(source).count())
        assertEquals(
            "业务侧不应再自己搭菜单入口",
            0,
            Regex("AppActionMenu\\(").findAll(source).count(),
        )
        assertEquals(
            "开合状态应交给 SheetAction",
            0,
            Regex("showTypefaceMenu").findAll(source).count(),
        )
        assertTrue("左侧操作未走声明式槽", source.contains("startSheetActions = buildList {"))
        assertTrue(
            "调用方按钮与字体菜单未合并",
            source.contains("startSheetAction?.let(::add)"),
        )
        assertTrue(
            "字体菜单未挂在按钮上",
            source.contains("menu = typefaces.mapIndexed { index, name ->"),
        )
    }

    /** 条目由字体数组驱动，选中后按序号回到原回调。 */
    @Test
    fun typefaceItemsFollowSystemListAndKeepSelection() {
        val source = codeOf(SHEET)
        assertTrue(
            "ID 未用序号前缀",
            source.contains("id = \"\$FontTypefaceIdPrefix\$index\""),
        )
        assertTrue("条目未声明成菜单项", source.contains("SheetMenuAction("))
        assertTrue("选中回调丢失", source.contains("selectTypeface(index)"))
        assertTrue("选完未关闭整个 sheet", source.contains("onDismissRequest()"))
        assertFalse(
            "仍在按字符串回解下标",
            source.contains("removePrefix(FontTypefaceIdPrefix).toIntOrNull()"),
        )
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
