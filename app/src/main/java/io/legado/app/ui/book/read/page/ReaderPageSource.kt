package io.legado.app.ui.book.read.page

import io.legado.app.ui.book.read.page.entities.TextChapter

/**
 * 渲染层的页数据入口 + 页位置命令口（Track D·D2，见 docs/dev/mad-modernization-plan.md §Track D）。
 *
 * 与出站的 [ReaderEvent] 相对：业务意图往外发、页数据往里读。`ReadView` 与
 * `TextPageFactory` 都不认识 `ReadBook`，由宿主（`ReadBookController`）实现本接口把数据喂进来、
 * 把页位置命令接下去。
 *
 * 这里刻意是**逐次读取**而不是不可变快照：取页由 `pageFactory` 在绘制期驱动，
 * 换成发布期快照会改变时序。快照化是 D2 之后的独立议题，不与本步捆绑。
 *
 * **为什么页位置命令不走 [ReaderEvent]**：`ReaderEvent` 是即发即忘的出站意图，而
 * [setPageIndex]/[moveToNextChapter]/[moveToPrevChapter] 必须**同步**生效——
 * `TextPageFactory.moveToNext/moveToPrev` 下达命令后立即用新位置取页并返回布尔给翻页委托
 * 决定动画。所以它们是同步命令口，不是事件。
 */
interface ReaderPageSource {

    val durChapterIndex: Int

    val durPageIndex: Int

    val simulatedChapterSize: Int

    val pageAnim: Int

    /** 加载中/出错时要顶替正文显示的消息；为 null 表示正常显示正文 */
    val msg: String?

    /** chapterOnDur: 0 当前章, 1 下一章, -1 上一章 */
    fun textChapter(chapterOnDur: Int): TextChapter?

    /** 章内翻页：把阅读位置移到本章第 [index] 页 */
    fun setPageIndex(index: Int)

    /**
     * 跨章前进。实现方必须用 `upContentInPlace = false`——由 `TextPageFactory` 在命令返回后
     * 自行决定是否 `upContent`，避免同一次翻页刷两遍内容。
     */
    fun moveToNextChapter(upContent: Boolean)

    /** 跨章后退，`upContentInPlace = false` 的约定同 [moveToNextChapter] */
    fun moveToPrevChapter(upContent: Boolean)
}
