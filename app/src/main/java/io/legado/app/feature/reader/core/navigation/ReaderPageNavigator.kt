package io.legado.app.feature.reader.core.navigation

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.navigation.ReaderPageNavigator.locate

data class ReaderNavigationResult(
    val pageIndex: Int,
    val window: ReaderPageWindow,
    val hitBoundary: Boolean,
)

data class ReaderChapterPagePosition(
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
)

data class ReaderPageContext(
    val chapterIndex: Int,
    val chapterTitle: String,
    val startPosition: Int,
    val endPosition: Int,
    val text: String,
    val contentStartPosition: Int?,
    val anchorText: String?,
)

object ReaderPageNavigator {
    fun bodyParagraphAt(pages: List<ReaderPage>, chapterIndex: Int, chapterPosition: Int): Int? = pages
        .asSequence()
        .filter { it.id.chapterIndex == chapterIndex }
        .flatMap { it.elements.asSequence() }
        .filterIsInstance<ReaderElement.Text>()
        .filter { !it.emphasized && it.paragraphIndex >= 0 && it.chapterPosition <= chapterPosition }
        .maxByOrNull { it.chapterPosition }
        ?.paragraphIndex

    fun window(pages: List<ReaderPage>, pageIndex: Int): ReaderPageWindow {
        if (pages.isEmpty()) return ReaderPageWindow()
        val index = pageIndex.coerceIn(pages.indices)
        return ReaderPageWindow(
            previous = pages.getOrNull(index - 1),
            current = pages[index],
            next = pages.getOrNull(index + 1),
            nextPlus = pages.getOrNull(index + 2),
        )
    }

    /**
     * 章节边界占位语义（对照 shutiao 的"正在加载中"页）：当前真实页的邻章在书中
     * 存在但尚未分页进 [pages] 时，需要预置占位页，让手势层在邻章未装载时也能把
     * 页面拖/滚进"加载中"。占位页本身不再扩展（装载完成前是死端）。
     *
     * 判定不看邻章正文是否已缓存：旧 View `TextPageFactory.nextPage/prevPage` 在邻章
     * 还没有页时一律给出 `R.string.data_loading` 兜底页，而"正文已缓存、只差排版"恰恰
     * 是最容易读到章末的窗口——缺了占位页，章末残页下方就没有任何页面承接。
     */
    fun missingAdjacentChapters(
        pages: List<ReaderPage>,
        pageIndex: Int,
        chapterCount: Int,
    ): List<Int> {
        val page = pages.getOrNull(pageIndex) ?: return emptyList()
        if (page.isPlaceholder) return emptyList()
        return listOf(page.id.chapterIndex - 1, page.id.chapterIndex + 1).filter { chapter ->
            chapter in 0 until chapterCount && pages.none { it.id.chapterIndex == chapter }
        }
    }

    /**
     * 章末第三槽（`ReaderPageWindow.nextPlus`）是否需要"继续滑动以加载下一章…"兜底页，
     * 对照旧 View `TextPageFactory.nextPlusPage`：本章最后一页的下一章还没有第二页
     * （只有一页，或该章还没排完）时，旧实现用 `R.string.keep_swipe_tip` 的提示页占住
     * 第三槽；书末没有下一章也就不需要提示页。滚动模式会把第三槽画出来，缺了它章末
     * 连续滑动会先看到一段没有页面的空档。
     */
    fun needsSwipeTipNextPlus(window: ReaderPageWindow, hasNextChapter: Boolean): Boolean =
        hasNextChapter && window.next != null && window.nextPlus == null

    fun move(pages: List<ReaderPage>, pageIndex: Int, delta: Int): ReaderNavigationResult {
        if (pages.isEmpty()) return ReaderNavigationResult(0, ReaderPageWindow(), true)
        val target = (pageIndex + delta).coerceIn(pages.indices)
        return ReaderNavigationResult(target, window(pages, target), target != pageIndex + delta)
    }

    /**
     * 翻页放行条件（对照旧 View `TextPageFactory.hasNext()` / `hasPrev()`）：
     * 旧实现是 `hasNextChapter() || 本章还有下一页`、`hasPrevChapter() || pageIndex > 0`，
     * 只看"书里还有没有邻章/本章还有没有下一页"，与邻章是否已完成排版无关。
     *
     * Canvas 早期版本只看 [ReaderPageWindow.next] 是否为 null（排版就绪），邻章排版滞后
     * 时会把可翻的边界误判成书末，点按/拖拽只弹"没有下一页"且不再触发装载，形成死端。
     * 排版未就绪的邻章在旧 View 里由 `moveToNextChapter` → 标题占位页承接；Canvas 的等价
     * 承接是 `ReadBookController.crossComposeChapterBoundary`（预置"加载中"占位页或直接启动
     * 该章排版）。因此放行必须回到业务语义，把翻页交给宿主处理。
     */
    fun canTurnNext(window: ReaderPageWindow, hasNextChapter: Boolean): Boolean =
        window.next != null || hasNextChapter

    /** 上一页放行条件，对照旧 View `TextPageFactory.hasPrev()`。 */
    fun canTurnPrevious(window: ReaderPageWindow, hasPreviousChapter: Boolean): Boolean =
        window.previous != null || hasPreviousChapter

    /**
     * 按章内位置定位页下标；该章不在 [pages] 中时返回 null。
     *
     * 注意与 [locate] 的区别：本函数不把"未定位"折叠成 0。0 是全书首页的合法下标，
     * 调用方拿它发布窗口会把阅读位置跳到书首（见 `publishDirectReaderPageWindow`）。
     */
    fun locateOrNull(pages: List<ReaderPage>, chapterIndex: Int, chapterPosition: Int): Int? {
        val chapterPages = pages.withIndex().filter { it.value.id.chapterIndex == chapterIndex }
        if (chapterPages.isEmpty()) return null
        return chapterPages.lastOrNull { pageStart(it.value) <= chapterPosition }?.index
            ?: chapterPages.first().index
    }

    fun locate(pages: List<ReaderPage>, chapterIndex: Int, chapterPosition: Int): Int =
        locateOrNull(pages, chapterIndex, chapterPosition) ?: 0

    fun chapterPosition(pages: List<ReaderPage>, pageIndex: Int): ReaderChapterPagePosition? {
        val page = pages.getOrNull(pageIndex) ?: return null
        val chapterIndex = page.id.chapterIndex
        val chapterPages = pages.filter { it.id.chapterIndex == chapterIndex }
        val localIndex = chapterPages.indexOfFirst { it === page }
            .takeIf { it >= 0 }
            ?: chapterPages.indexOfFirst { it.id == page.id }
        if (localIndex < 0) return null
        return ReaderChapterPagePosition(chapterIndex, localIndex, chapterPages.size)
    }

    fun locateChapterPage(
        pages: List<ReaderPage>,
        chapterIndex: Int,
        chapterPageIndex: Int,
    ): Int? {
        val chapterPages = pages.withIndex().filter { it.value.id.chapterIndex == chapterIndex }
        if (chapterPages.isEmpty()) return null
        return chapterPages[chapterPageIndex.coerceIn(chapterPages.indices)].index
    }

    fun pageContext(pages: List<ReaderPage>, pageIndex: Int): ReaderPageContext? {
        val page = pages.getOrNull(pageIndex) ?: return null
        val positions = page.elements.mapNotNull(::elementRange)
        if (positions.isEmpty() && page.elements.none { it is ReaderElement.Text && it.emphasized }) return null
        val start = positions.minOfOrNull { it.first } ?: 0
        val contentEnd = positions.maxOfOrNull { it.last + 1 } ?: 0
        val nextPageStart = pages.getOrNull(pageIndex + 1)
            ?.takeIf { it.id.chapterIndex == page.id.chapterIndex }
            ?.elements
            ?.mapNotNull(::elementRange)
            ?.minOfOrNull { it.first }
            ?.takeIf { it > start }
        val firstBodyParagraph = page.elements.asSequence()
            .filterIsInstance<ReaderElement.Text>()
            .filterNot { it.emphasized }
            .groupBy { it.paragraphIndex }
            .values
            .minByOrNull { paragraph ->
                paragraph.minOf { it.chapterPosition }
            }
        return ReaderPageContext(
            chapterIndex = page.id.chapterIndex,
            chapterTitle = page.chapterTitle,
            startPosition = start,
            endPosition = nextPageStart ?: contentEnd.coerceAtLeast(start + 1),
            text = page.text,
            contentStartPosition = firstBodyParagraph?.minOf { it.chapterPosition },
            anchorText = firstBodyParagraph
                ?.joinToString(separator = "") { it.value }
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        )
    }

    /**
     * 页首章内位置。**排除标题元素**：新模型的标题与正文是两套独立坐标（见 [ReaderSelection]
     * 的 anchorIsTitle 说明），把标题位置当成正文页首会把书签/朗读定位拖进标题坐标空间。
     * 旧 View 的 `TextPage.chapterPosition = textLines.first().chapterPosition` 建立在两者同空间的
     * 前提上，迁移后不再等价。
     */
    fun pageStart(page: ReaderPage): Int = page.elements.mapNotNull(::elementRange)
        .minOfOrNull { it.first } ?: 0

    private fun elementRange(element: ReaderElement): IntRange? = when (element) {
        is ReaderElement.Text -> if (element.emphasized) null else element.chapterPosition until
            (element.chapterPosition + element.value.length.coerceAtLeast(1))
        is ReaderElement.Image -> element.chapterPosition..element.chapterPosition
        is ReaderElement.Spacer -> element.chapterPosition..element.chapterPosition
        else -> null
    }
}
