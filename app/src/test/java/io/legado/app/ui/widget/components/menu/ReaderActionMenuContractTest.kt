package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十二轮：阅读器「动作格」菜单（`ReaderMoreActionsSheet` 5 处 / `MangaReaderSheets` 1 处）
 * 迁移前的行为基线。
 *
 * 这一簇属于高行为风险区：菜单由动作格的长按 /「更多」通道打开，条目直接映射
 * `ReadBookIntent` / `MangaReaderIntent`，选中态还带 `Book.imgStyle*`、`PageAnim.*` 与
 * `ReadBook.book?.setPageAnim()` 副作用。移动组件前先用静态契约把「有哪些条目、点了发什么、
 * 选中态怎么判」固定下来，迁移后只允许结构变化、不允许语义变化。
 *
 * 顶栏 / 侧栏的 overflow 菜单不在本轮范围：`ReadBookMenuBarTitle` 的 2 处已在第三十三轮由
 * [ReadMenuDropdownContractTest] 接管；只剩 `ReadBookMenuBarFloating` 的巨型菜单与它内嵌的
 * 图片样式子菜单。
 */
class ReaderActionMenuContractTest {

    /** 三个命令分支：条目 → intent 必须一一保留。 */
    @Test
    fun moreActionCommandMenusKeepPayload() {
        val source = codeOf(MORE_ACTIONS)
        assertTrue(
            "自定义按钮长按动作丢失",
            source.contains("dispatch(ReadBookIntent.SourceCustomButton(true))"),
        )
        assertTrue("换源动作丢失", source.contains("dispatch(ReadBookIntent.MenuBookChangeSource)"))
        assertTrue(
            "换章节源动作丢失",
            source.contains("dispatch(ReadBookIntent.MenuChapterChangeSource)"),
        )
        listOf(
            "R.string.menu_refresh_dur",
            "R.string.menu_refresh_after",
            "R.string.menu_refresh_all",
        ).forEach { entry ->
            assertTrue("刷新子项文案丢失：$entry", source.contains(entry))
        }
        listOf(
            "ReadBookIntent.MenuRefreshDur",
            "ReadBookIntent.MenuRefreshAfter",
            "ReadBookIntent.MenuRefreshAll",
        ).forEach { intent ->
            assertTrue("刷新子项意图丢失：$intent", source.contains(intent))
        }
    }

    /** 图片样式四项 + 当前值高亮。 */
    @Test
    fun imageStyleOptionsKeepFourChoicesWithSelection() {
        val source = codeOf(MORE_ACTIONS)
        listOf(
            "R.string.btn_default_s to Book.imgStyleDefault",
            "R.string.image_style_full to Book.imgStyleFull",
            "R.string.image_style_text to Book.imgStyleText",
            "R.string.image_style_single to Book.imgStyleSingle",
        ).forEach { entry ->
            assertTrue("图片样式项丢失：$entry", source.contains(entry))
        }
        assertTrue("图片样式未回传", source.contains("ReadBookIntent.MenuImageStyle(style)"))
        assertTrue(
            "图片样式丢失选中态",
            source.contains("currentStyle == style"),
        )
    }

    /** 翻页动画十项、选中态，以及写入 `ReadBook.book` 的副作用。 */
    @Test
    fun pageAnimOptionsKeepTenChoicesAndSideEffect() {
        val source = codeOf(MORE_ACTIONS)
        listOf(
            "R.string.btn_default_s to -1",
            "R.string.page_anim_cover to PageAnim.coverPageAnim",
            "R.string.page_anim_slide to PageAnim.slidePageAnim",
            "R.string.page_anim_simulation to PageAnim.simulationPageAnim",
            "R.string.page_anim_ios_curl to PageAnim.iosCurlPageAnim",
            "R.string.page_anim_google_curl to PageAnim.googleCurlPageAnim",
            "R.string.page_anim_parallax to PageAnim.parallaxPageAnim",
            "R.string.page_anim_scroll to PageAnim.scrollPageAnim",
            "R.string.page_anim_fade to PageAnim.fadePageAnim",
            "R.string.page_anim_none to PageAnim.noAnim",
        ).forEach { entry ->
            assertTrue("翻页动画项丢失：$entry", source.contains(entry))
        }
        assertTrue("动画未写回书籍", source.contains("ReadBook.book?.setPageAnim(value)"))
        assertTrue("动画未通知页面", source.contains("ReadBookIntent.PageAnimChanged"))
        assertTrue("动画丢失选中态", source.contains("currentPageAnim == value"))
    }

    /** 动作格的「更多」通道与菜单挂载点保持。 */
    @Test
    fun menusStillOpenFromActionSquareMoreChannel() {
        val more = codeOf(MORE_ACTIONS)
        assertTrue("动作格「更多」通道丢失", more.contains("onMoreClick = { expanded = true }"))
        assertTrue("菜单未按动作 id 分派", more.contains("when (action.id)"))
        assertEquals(
            "动作格应仍是单一份 expanded 状态",
            1,
            Regex("var expanded by remember\\(action\\.id\\)").findAll(more).count(),
        )

        val manga = codeOf(MANGA_SHEETS)
        assertTrue(
            "漫画动作格「更多」门控丢失",
            manga.contains("hasMore = action.longClickIntent != null"),
        )
    }

    /** 漫画长按动作只有一条，点击后派发该动作自己的 intent。 */
    @Test
    fun mangaLongActionKeepsIntent() {
        val source = codeOf(MANGA_SHEETS)
        assertTrue(
            "漫画长按文案丢失",
            source.contains("R.string.source_custom_button_long_action"),
        )
        assertTrue("漫画长按未派发 intent", source.contains("dispatch(longClickIntent)"))
    }

    /** 迁完后 5 处动作格菜单都应是受控 [AppActionMenu]，且锚点是动作格矩形。 */
    @Test
    fun actionSquareMenusUseControlledAppActionMenu() {
        val more = codeOf(MORE_ACTIONS)
        assertEquals("仍手画菜单", 0, Regex("RoundDropdownMenu").findAll(more).count())
        assertEquals(
            "五处菜单都应由动作格 expanded 驱动",
            5,
            Regex("embeddedExpanded = expanded").findAll(more).count(),
        )
        assertEquals(
            "五处受控弹层都不应渲染 trigger",
            5,
            Regex("trigger = \\{\\}").findAll(more).count(),
        )
        assertEquals(
            "五处都应锚在动作格矩形上",
            5,
            Regex("Modifier\\.matchParentSize\\(\\)").findAll(more).count(),
        )
        assertTrue(
            "图片样式 ID 未用稳定前缀",
            more.contains("private const val ReaderMoreImageStyleIdPrefix = \"reader_more_image_style:\""),
        )
        assertTrue(
            "翻页动画 ID 未回解成选项值",
            more.contains("removePrefix(ReaderMorePageAnimIdPrefix).toIntOrNull()"),
        )

        val manga = codeOf(MANGA_SHEETS)
        assertEquals("漫画仍手画菜单", 0, Regex("RoundDropdownMenu").findAll(manga).count())
        assertTrue("漫画菜单未受控", manga.contains("embeddedExpanded = expanded"))
        assertTrue(
            "漫画菜单锚点丢失",
            manga.contains("modifier = Modifier.matchParentSize()"),
        )
        assertTrue(
            "漫画菜单缺少稳定 ID",
            manga.contains("private const val MangaSourceLongAction = \"manga_source_long_action\""),
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
        const val MORE_ACTIONS = "io/legado/app/ui/book/read/sheet/ReaderMoreActionsSheet.kt"
        const val MANGA_SHEETS = "io/legado/app/ui/book/manga/MangaReaderSheets.kt"
    }
}
