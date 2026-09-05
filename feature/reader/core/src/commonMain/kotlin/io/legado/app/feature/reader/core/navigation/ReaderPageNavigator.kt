package io.legado.app.feature.reader.core.navigation

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageWindow

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

    fun move(pages: List<ReaderPage>, pageIndex: Int, delta: Int): ReaderNavigationResult {
        if (pages.isEmpty()) return ReaderNavigationResult(0, ReaderPageWindow(), true)
        val target = (pageIndex + delta).coerceIn(pages.indices)
        return ReaderNavigationResult(target, window(pages, target), target != pageIndex + delta)
    }

    fun locate(pages: List<ReaderPage>, chapterIndex: Int, chapterPosition: Int): Int {
        val chapterPages = pages.withIndex().filter { it.value.id.chapterIndex == chapterIndex }
        if (chapterPages.isEmpty()) return 0
        return chapterPages.lastOrNull { pageStart(it.value) <= chapterPosition }?.index
            ?: chapterPages.first().index
    }

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
