package io.legado.app.ui.book.read.page

import io.legado.app.ui.book.read.page.entities.TextChapter

/**
 * `ReadView` 的只读页数据入口（Track D·D2，见 docs/dev/mad-modernization-plan.md §Track D）。
 *
 * 与出站的 [ReaderEvent] 相对：业务意图往外发、页数据往里读。`ReadView` 不认识 `ReadBook`，
 * 由宿主（`ReadBookController`）实现本接口把数据喂进来。
 *
 * 这里刻意是**逐次读取**而不是不可变快照：取页由 `pageFactory` 在绘制期驱动，
 * 换成发布期快照会改变时序。快照化是 D2 之后的独立议题，不与本步捆绑。
 */
interface ReaderPageSource {

    val durChapterIndex: Int

    val durPageIndex: Int

    val simulatedChapterSize: Int

    val pageAnim: Int

    /** chapterOnDur: 0 当前章, 1 下一章, -1 上一章 */
    fun textChapter(chapterOnDur: Int): TextChapter?
}
