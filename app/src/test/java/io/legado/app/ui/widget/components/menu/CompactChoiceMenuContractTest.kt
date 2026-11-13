package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第九轮：最后两处 `AppChoiceDropdown(compact = true)` 纯单选的迁移契约。
 *
 * - `GlobalThemePage` 的翻页动画：trigger 是 `TinySettingItem` 设置行，包在
 *   `Row` 的 `Modifier.weight(1f)` 里；同文件翻页速度已是 `compact = true` 现成模板。
 * - `MangaReaderSheets` 的九宫格点击区域：trigger 是 `NormalCard` 方块。
 *
 * 两者都把 `onClick` 从 trigger 上摘掉，改由入口包装唯一可点节点，
 * `modifier = this.modifier` 承接锚点与外层尺寸（weight / fillMaxSize）。
 *
 * `MangaReaderSheets` 同文件 :183 的 `MangaSourceActionSquare` 菜单共用
 * `ReaderMenuActionSquare` 的 expanded、形态不同，第九轮刻意没动；第三十二轮已迁为受控
 * `AppActionMenu`，对应棘轮归零。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明弹层定位、真机外观或 TalkBack 行为**。
 */
class CompactChoiceMenuContractTest {

    @Test
    fun migratedCompactChoicesUseAppChoiceDropdown() {
        val theme = sourceOf(GLOBAL_THEME)
        assertFalse("GlobalThemePage 仍手画 RoundDropdownMenu", theme.contains("RoundDropdownMenu("))
        assertFalse("GlobalThemePage 仍自带 showPageAnimMenu", theme.contains("showPageAnimMenu"))
        assertTrue("GlobalThemePage 缺少 pageAnimChoices", theme.contains("pageAnimChoices"))
        assertEquals(
            "GlobalThemePage 的 AppChoiceDropdown 数量（原有速度 + 新增动画）",
            2,
            Regex("AppChoiceDropdown\\(").findAll(theme).count(),
        )
        assertEquals(2, Regex("selectedId =").findAll(theme).count())
        assertEquals(2, Regex("compact = true").findAll(theme).count())
        assertEquals(2, Regex("modifier = this\\.modifier").findAll(theme).count())

        val manga = sourceOf(MANGA)
        val cell = manga.substringAfter("private fun MangaClickAreaCell(")
        assertFalse("MangaClickAreaCell 仍手画 RoundDropdownMenu", cell.contains("RoundDropdownMenu("))
        assertFalse("MangaClickAreaCell 仍自带 expanded 状态", cell.contains("var expanded"))
        assertTrue("MangaClickAreaCell 缺少 AppChoiceDropdown", cell.contains("AppChoiceDropdown("))
        assertTrue("MangaClickAreaCell 未用 compact = true", cell.contains("compact = true"))
        assertTrue("MangaClickAreaCell 的 trigger 未挂锚点", cell.contains("modifier = this.modifier"))
        assertEquals(1, Regex("AppChoiceDropdown\\(").findAll(manga).count())
        assertEquals(1, Regex("selectedId =").findAll(manga).count())
    }

    /** 第三十二轮：`MangaSourceActionSquare` 的长按菜单已改为受控 `AppActionMenu`，棘轮归零。 */
    @Test
    fun mangaSourceActionSquareUsesControlledMenu() {
        val manga = sourceOf(MANGA)
        assertEquals(
            "MangaReaderSheets 应已无手画菜单",
            0,
            Regex("RoundDropdownMenu\\(").findAll(manga).count(),
        )
        val square = manga
            .substringAfter("private fun MangaSourceActionSquare(")
            .substringBefore("\n@Composable")
        assertTrue("MangaSourceActionSquare 的菜单被误删", square.contains("AppActionMenu("))
        assertTrue("MangaSourceActionSquare 未受控", square.contains("embeddedExpanded = expanded"))
        assertTrue("MangaSourceActionSquare 锚点丢失", square.contains("Modifier.matchParentSize()"))
    }

    @Test
    fun choicesKeepLocalizedAccessibleTitle() {
        assertEquals(
            "翻页动画应有稳定名称（TinySettingItem + AppChoiceDropdown 各一处）",
            2,
            Regex("title = stringResource\\(R\\.string\\.page_anim\\)")
                .findAll(sourceOf(GLOBAL_THEME)).count(),
        )
        assertTrue(
            "九宫格点击区域应有本地化的稳定无障碍名称",
            sourceOf(MANGA).contains(
                "title = stringResource(R.string.manga_reader_click_areas)",
            ),
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
        const val GLOBAL_THEME = "io/legado/app/ui/book/read/sheet/GlobalThemePage.kt"
        const val MANGA = "io/legado/app/ui/book/manga/MangaReaderSheets.kt"
    }
}
