package io.legado.app.model

import io.legado.app.data.entities.Bookmark

/**
 * 当前阅读会话的书签位置快照，供渲染层同步判定「本页是否已有书签」（右上角角标）。
 *
 * 渲染层（`PageView.setProgress`）在主线程热路径上按页查询，不能起协程查库；
 * 由 `ReadBookViewModel` 收集 `BookmarkRepository.flowByBook` 后写入。
 * 只读缓存，不参与持久化——与 [ReadSessionState] 同层。
 */
object ReaderBookmarkState {

    /** chapterIndex → 该章内所有书签的 chapterPos。整体替换，读侧无需加锁。 */
    @Volatile
    private var positionsByChapter: Map<Int, List<Int>> = emptyMap()

    /**
     * 判定 `[startPos, endPos)` 这段章节内位置区间是否落有书签。
     *
     * @param startPos 页首字符在章节内的位置（`TextPage.chapterPosition`）
     * @param endPos 页尾之后一个字符的位置
     */
    fun hasBookmarkInRange(chapterIndex: Int, startPos: Int, endPos: Int): Boolean {
        val positions = positionsByChapter[chapterIndex] ?: return false
        return positions.any { it >= startPos && it < endPos }
    }

    fun update(bookmarks: List<Bookmark>) {
        positionsByChapter = bookmarks.groupBy({ it.chapterIndex }, { it.chapterPos })
    }

    fun clear() {
        positionsByChapter = emptyMap()
    }
}
