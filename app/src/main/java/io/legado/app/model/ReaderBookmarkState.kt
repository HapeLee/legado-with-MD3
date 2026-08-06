package io.legado.app.model

import io.legado.app.data.entities.Bookmark

/**
 * 当前阅读会话的书签位置快照，供渲染层同步判定「本页是否已有书签」（右上角角标）。
 *
 * 渲染层（`PageView.setProgress`）在主线程热路径上按页查询，不能起协程查库；
 * 由 `ReadBookmarkDelegate` 收集 `BookmarkRepository.flowByBook` 后写入。
 * 只读缓存，不参与持久化——与 [ReadSessionState] 同层。
 *
 * 快照携带 `bookName to bookAuthor` 书键：查库/写快照都带键，键不一致视为无书签。
 * 这样换书后、新书书签流第一条数据到达之前，上一本残留的旧快照不会误供给新书
 * （旧键 vs 新查询键 → 失配 → 不显示角标）。
 */
object ReaderBookmarkState {

    private data class Snapshot(
        val bookName: String,
        val bookAuthor: String,
        /** chapterIndex → 该章内所有书签的 chapterPos。整体替换，读侧无需加锁。 */
        val positionsByChapter: Map<Int, List<Int>>,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    /**
     * 判定 `[startPos, endPos)` 这段章节内位置区间是否落有书签。
     *
     * @param bookName 当前书书名（`ReadBook.book.name`），须与快照书键一致
     * @param bookAuthor 当前书作者
     * @param startPos 页首字符在章节内的位置（`TextPage.chapterPosition`）
     * @param endPos 页尾之后一个字符的位置
     */
    fun hasBookmarkInRange(
        bookName: String,
        bookAuthor: String,
        chapterIndex: Int,
        startPos: Int,
        endPos: Int,
    ): Boolean {
        val snap = snapshot ?: return false
        if (snap.bookName != bookName || snap.bookAuthor != bookAuthor) return false
        val positions = snap.positionsByChapter[chapterIndex] ?: return false
        return positions.any { it >= startPos && it < endPos }
    }

    fun update(bookName: String, bookAuthor: String, bookmarks: List<Bookmark>) {
        snapshot = Snapshot(
            bookName = bookName,
            bookAuthor = bookAuthor,
            positionsByChapter = bookmarks.groupBy({ it.chapterIndex }, { it.chapterPos }),
        )
    }

    fun clear() {
        snapshot = null
    }
}
