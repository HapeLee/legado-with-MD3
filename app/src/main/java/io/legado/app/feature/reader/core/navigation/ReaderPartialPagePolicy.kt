package io.legado.app.feature.reader.core.navigation

/**
 * 部分排版章节（页正在逐页流出）的发布时机与翻页护栏，逐条对照旧 View：
 *
 * - `TextChapterLayout.onPageCompleted()`（`TextChapterLayout.kt:240-260`）每成型一页就
 *   `channel.trySend`，宿主 `ReadBook.loadContent`（`7a61ea86d^` `ReadBook.kt:1479-1530`）
 *   按页消费并决定何时 `upContent`；
 * - offset = 0（当前章）：含 `durChapterPos` 的页成型时重绘；滚动模式另有
 *   `max(index - 3, 0) < durPageIndex` 的 3 页余量；
 * - offset = 1（下一章）：只在 `page.index <= 1` 时重绘（前两页够顶掉兜底页）；
 * - offset = -1（上一章）：不早推，只在收尾时重绘；
 * - `TextChapter.isLastIndex` 要求 `isCompleted`、`isLastIndexCurrent` 用于拒绝越过还没成型的页，
 *   `TextPageFactory.moveToPrev`（`TextPageFactory.kt:75-77`）用 `prevChapter.isCompleted == false`
 *   拒绝退回还没排完的上一章。
 */
object ReaderPartialPagePolicy {

    /** 一页成型后是否立刻换窗（旧 View 三条 `upContent(offset)` 触发条件）。 */
    fun shouldPublishPage(
        chapterOffset: Int,
        pageIndex: Int,
        currentPageIndex: Int,
        containsReadingPosition: Boolean,
        continuousScroll: Boolean,
    ): Boolean = when {
        // 上一章不早推：旧 loadContent 的 offset=-1 分支只在收尾时 upContent。
        chapterOffset < 0 -> false
        chapterOffset > 0 -> pageIndex <= NEXT_CHAPTER_EARLY_PAGE_LIMIT
        containsReadingPosition -> true
        continuousScroll -> maxOf(pageIndex - SCROLL_LOOKAHEAD_PAGES, 0) < currentPageIndex
        else -> false
    }

    /**
     * 目标页属于别的章节且该章还在逐页流出时能不能翻过去。
     *
     * - 向前：本章还有没成型的页，窗口里的"下一页"其实已经是下一章了——翻过去会跳过本章
     *   剩余内容（旧 `isLastIndexCurrent` 拒绝，渲染层改画"加载中"页）；
     * - 向后：上一章的末页还没成型，落点必错（旧 `moveToPrev` 等它 `isCompleted`）。
     */
    fun allowsChapterMove(
        fromChapterIndex: Int,
        targetChapterIndex: Int,
        fromChapterStreaming: Boolean,
        targetChapterStreaming: Boolean,
    ): Boolean = when {
        targetChapterIndex > fromChapterIndex -> !fromChapterStreaming
        targetChapterIndex < fromChapterIndex -> !targetChapterStreaming
        else -> true
    }

    /**
     * 换章（layout key 变化）时该保留哪个章节"已流出但整章还没提交"的页。
     *
     * 旧 View 的页是**增量追加**的：`TextChapterLayout.onPageCompleted()`
     * （`7a61ea86d^` `TextChapterLayout.kt`）把成型的页 `textPages.add(textPage)`，整章排完才置
     * `TextChapter.isCompleted`；`TextChapter.isLayoutRunning` 的注释写明它用来区分"还在排"和
     * "排到一半就废了"，**前者可以继续用已排出的页**。所以换章只是换阅读位置，已排好的页照旧
     * 可见，尾部交给空白页承接——而不是把它们摘掉、等新一批从头排完再重新出现。
     *
     * 两种不能保留的情况：
     * - 排版环境变化（对照旧 `TextChapter.isLayoutSizeMatch()` 失败）：几何全部失效，必须整批重排；
     * - 新当前章本来就没有部分页：没有可承接的页，与旧行为一致。
     *
     * @param streamedChapters 当前挂着"部分页"的章节下标集合。
     * @return 需要保留的章节下标；null 表示不保留任何部分页。
     */
    fun retainedStreamedChapter(
        chapterIndex: Int,
        environmentChanged: Boolean,
        streamedChapters: Set<Int>,
    ): Int? = chapterIndex.takeIf { !environmentChanged && it in streamedChapters }

    /** 旧 `ReadBook.loadContent`：下一章排到 `page.index > 1` 就不再提前重绘。 */
    private const val NEXT_CHAPTER_EARLY_PAGE_LIMIT = 1

    /** 旧 `ReadBook.loadContent` 滚动模式的重绘余量：`max(index - 3, 0) < durPageIndex`。 */
    private const val SCROLL_LOOKAHEAD_PAGES = 3
}
