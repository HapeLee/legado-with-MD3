package io.legado.app.feature.reader.core.navigation

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageNavigatorTest {
    private fun page(index: Int, start: Int, chapterIndex: Int = 4) = ReaderPage(
        id = ReaderPageId(chapterIndex, index), chapterTitle = "", text = "字",
        widthPx = 100, heightPx = 100, contentTopPx = 0f, contentBottomPx = 100f,
        elements = listOf(ReaderElement.Text(ReaderRect(0f, 0f, 10f, 10f), 8f, "字", ReaderTextStyle(0, 10f), false, false, chapterPosition = start)),
        revision = 1,
    )

    @Test
    fun locatesByChapterPositionAndBuildsWindow() {
        val pages = listOf(page(0, 0), page(1, 20), page(2, 40))
        val index = ReaderPageNavigator.locate(pages, 4, 35)
        val window = ReaderPageNavigator.window(pages, index)
        assertEquals(1, index)
        assertEquals(0, window.previous?.id?.pageIndex)
        assertEquals(2, window.next?.id?.pageIndex)
    }

    @Test
    fun reportsBoundaryWithoutDroppingCurrentPage() {
        val pages = listOf(page(0, 0), page(1, 20))
        val result = ReaderPageNavigator.move(pages, 0, -1)
        assertTrue(result.hitBoundary)
        assertEquals(0, result.pageIndex)
        assertNull(result.window.previous)
    }

    @Test
    fun windowAndMovementRemainContinuousAcrossChapterBoundary() {
        val pages = listOf(
            page(index = 0, start = 20, chapterIndex = 3),
            page(index = 0, start = 0, chapterIndex = 4),
            page(index = 1, start = 20, chapterIndex = 4),
        )

        val result = ReaderPageNavigator.move(pages, pageIndex = 0, delta = 1)

        assertTrue(!result.hitBoundary)
        assertEquals(3, result.window.previous?.id?.chapterIndex)
        assertEquals(4, result.window.current?.id?.chapterIndex)
        assertEquals(1, ReaderPageNavigator.locate(pages, chapterIndex = 4, chapterPosition = 10))
    }

    @Test
    fun mapsGlobalWindowIndexToChapterLocalProgressAndBack() {
        val pages = listOf(
            page(index = 0, start = 0, chapterIndex = 3),
            page(index = 1, start = 20, chapterIndex = 3),
            page(index = 0, start = 0, chapterIndex = 4),
            page(index = 1, start = 20, chapterIndex = 4),
            page(index = 2, start = 40, chapterIndex = 4),
            page(index = 0, start = 0, chapterIndex = 5),
        )

        assertEquals(
            ReaderChapterPagePosition(chapterIndex = 4, pageIndex = 1, pageCount = 3),
            ReaderPageNavigator.chapterPosition(pages, pageIndex = 3),
        )
        assertEquals(4, ReaderPageNavigator.locateChapterPage(pages, chapterIndex = 4, chapterPageIndex = 2))
        assertEquals(2, ReaderPageNavigator.locateChapterPage(pages, chapterIndex = 4, chapterPageIndex = -1))
        assertNull(ReaderPageNavigator.locateChapterPage(pages, chapterIndex = 9, chapterPageIndex = 0))
    }

    @Test
    fun buildsBookmarkRangeFromCanvasPageAndNextPageBoundary() {
        val pages = listOf(
            page(index = 0, start = 10, chapterIndex = 4),
            page(index = 1, start = 30, chapterIndex = 4),
            page(index = 0, start = 0, chapterIndex = 5),
        )

        assertEquals(
            ReaderPageContext(
                chapterIndex = 4,
                chapterTitle = "",
                startPosition = 10,
                endPosition = 30,
                text = "字",
                contentStartPosition = 10,
                anchorText = "字",
            ),
            ReaderPageNavigator.pageContext(pages, pageIndex = 0),
        )
        assertEquals(31, ReaderPageNavigator.pageContext(pages, pageIndex = 1)?.endPosition)
        assertNull(ReaderPageNavigator.pageContext(emptyList(), pageIndex = 0))
    }

    @Test
    fun bodyAnchorUsesLeftColumnBeforeHigherRightColumn() {
        val style = ReaderTextStyle(0, 10f)
        val page = page(0, 0).copy(elements = listOf(
            ReaderElement.Text(ReaderRect(5f, 20f, 15f, 30f), 28f, "左", style, false, false, chapterPosition = 10, paragraphIndex = 1),
            ReaderElement.Text(ReaderRect(55f, 0f, 65f, 10f), 8f, "右", style, false, false, chapterPosition = 30, paragraphIndex = 2),
        ))
        val context = ReaderPageNavigator.pageContext(listOf(page), 0)
        assertEquals(10, context?.contentStartPosition)
        assertEquals("左", context?.anchorText)
    }

    @Test
    fun pageContextUsesFirstBodyParagraphForEditorAnchor() {
        val style = ReaderTextStyle(0, 10f)
        val page = page(index = 0, start = 0).copy(
            text = "标题正文",
            elements = listOf(
                ReaderElement.Text(ReaderRect(0f, 0f, 10f, 10f), 8f, "标题", style, false, true, chapterPosition = 0, paragraphIndex = 0),
                ReaderElement.Text(ReaderRect(0f, 20f, 10f, 30f), 28f, "正", style, false, false, chapterPosition = 100, paragraphIndex = 1),
                ReaderElement.Text(ReaderRect(10f, 20f, 20f, 30f), 28f, "文", style, false, false, chapterPosition = 101, paragraphIndex = 1),
            ),
        )

        val context = ReaderPageNavigator.pageContext(listOf(page), pageIndex = 0)

        assertEquals(100, context?.contentStartPosition)
        assertEquals(100, context?.startPosition)
        assertEquals(102, context?.endPosition)
        assertEquals(100, ReaderPageNavigator.pageStart(page))
        assertEquals("正文", context?.anchorText)
    }

    @Test
    fun titleOffsetsDoNotBecomeBodyBookmarkOrReadAloudOffsets() {
        val body = (page(0, 0).elements.single() as ReaderElement.Text).copy(paragraphIndex = 1)
        val title = body.copy(emphasized = true, chapterPosition = 50, paragraphIndex = 0)
        val page = page(0, 0).copy(elements = listOf(title, body))
        assertEquals(1, ReaderPageNavigator.pageContext(listOf(page), 0)?.endPosition)
        assertEquals(1, ReaderPageNavigator.bodyParagraphAt(listOf(page), 4, 50))
        assertNull(ReaderPageNavigator.bodyParagraphAt(listOf(page), 5, 50))
        val titleOnly = page.copy(elements = listOf(title))
        assertEquals(0, ReaderPageNavigator.pageStart(titleOnly))
        assertNull(ReaderPageNavigator.pageContext(listOf(titleOnly), 0)?.contentStartPosition)
        assertNull(ReaderPageNavigator.bodyParagraphAt(listOf(titleOnly), 4, 50))
    }

    @Test
    fun placeholderIsRequestedOnlyForExistingUnloadedAdjacentChapters() {
        // 邻章在书中存在但未分页: 上一章 3、下一章 5 都缺
        val pages = listOf(page(0, 0), page(1, 20))
        assertEquals(
            listOf(3, 5),
            ReaderPageNavigator.missingAdjacentChapters(pages, pageIndex = 0, chapterCount = 6),
        )
        // 邻章已分页或不存在: 不需要占位
        assertEquals(
            listOf(3),
            ReaderPageNavigator.missingAdjacentChapters(pages, pageIndex = 0, chapterCount = 5),
        )
        // 上一章已分页、下一章超出书末: 不需要占位
        val withPrevChapter = pages + page(index = 0, start = 0, chapterIndex = 3)
        assertEquals(
            emptyList<Int>(),
            ReaderPageNavigator.missingAdjacentChapters(
                withPrevChapter, pageIndex = 0, chapterCount = 5,
            ),
        )
        val withNextChapter = pages + page(index = 0, start = 0, chapterIndex = 5)
        assertEquals(
            listOf(3),
            ReaderPageNavigator.missingAdjacentChapters(
                withNextChapter, pageIndex = 0, chapterCount = 6,
            ),
        )
    }

    @Test
    fun placeholderPageNeverExtendsFurtherPlaceholders() {
        // 占位页是死端: 装载完成前不再向更远处串章
        val placeholder = page(0, 0, chapterIndex = 5).copy(isPlaceholder = true)
        assertEquals(
            emptyList<Int>(),
            ReaderPageNavigator.missingAdjacentChapters(
                listOf(placeholder), pageIndex = 0, chapterCount = 8,
            ),
        )
    }

    @Test
    fun turnIsAllowedWhenOnlyTheBusinessNeighborChapterExists() {
        // 对照旧 View TextPageFactory.hasNext()/hasPrev()：放行看"书里还有没有邻章"，
        // 与邻章是否已排版无关。邻章排版滞后时窗口里没有邻页，也必须放行，否则跨章
        // 翻页只会弹"没有下一页"且不再触发该章装载（读完本章无法进入下一章）。
        val pages = listOf(page(0, 0), page(1, 20))
        val lastPage = ReaderPageNavigator.window(pages, pageIndex = 1)
        assertNull(lastPage.next)
        assertTrue(ReaderPageNavigator.canTurnNext(lastPage, hasNextChapter = true))
        assertTrue(!ReaderPageNavigator.canTurnNext(lastPage, hasNextChapter = false))

        val firstPage = ReaderPageNavigator.window(pages, pageIndex = 0)
        assertNull(firstPage.previous)
        assertTrue(ReaderPageNavigator.canTurnPrevious(firstPage, hasPreviousChapter = true))
        assertTrue(!ReaderPageNavigator.canTurnPrevious(firstPage, hasPreviousChapter = false))

        // 邻页已就绪时与旧实现一致：不需要业务邻章也放行；窗口为空则不放行。
        assertTrue(ReaderPageNavigator.canTurnNext(ReaderPageNavigator.window(pages, 0), false))
        assertTrue(ReaderPageNavigator.canTurnPrevious(ReaderPageNavigator.window(pages, 1), false))
        assertTrue(!ReaderPageNavigator.canTurnNext(ReaderPageWindow(), false))
        assertTrue(!ReaderPageNavigator.canTurnPrevious(ReaderPageWindow(), false))
    }

    @Test
    fun locateReportsMissingChapterInsteadOfCollapsingToBookStart() {
        // 章不在页表中时 locate 折叠成 0（全书首页是合法下标），发布该下标会把阅读
        // 位置跳回书首；调用方用 locateOrNull 区分"未定位"与"第一章"。
        val pages = listOf(page(0, 0), page(1, 20))
        assertEquals(0, ReaderPageNavigator.locate(pages, chapterIndex = 9, chapterPosition = 0))
        assertNull(ReaderPageNavigator.locateOrNull(pages, chapterIndex = 9, chapterPosition = 0))
        assertEquals(
            1,
            ReaderPageNavigator.locateOrNull(pages, chapterIndex = 4, chapterPosition = 25)
        )
    }
}
