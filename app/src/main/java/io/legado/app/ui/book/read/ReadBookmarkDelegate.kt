package io.legado.app.ui.book.read

import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.repository.BookmarkRepository
import io.legado.app.model.ReadBook
import io.legado.app.model.ReaderBookmarkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.math.abs

/**
 * 书签域（R2.2 续批）。
 *
 * **无自持状态**：书签编辑器就是 `ReadBookSheet.Bookmark`，草稿随 sheet 参数走，
 * `activeSheet` 是单一持有者，故读写经 [Host]。
 */
class ReadBookmarkDelegate(
    private val scope: CoroutineScope,
    private val host: Host,
    private val bookmarkRepository: BookmarkRepository,
    /**
     * 当前书的 `书名 to 作者`（未开书时为 null）——`bookmarks` 表没有 bookUrl 列，
     * 这两个字段就是关联键。[start] 用它当切换键重订书签流。
     */
    private val bookKey: Flow<Pair<String, String>?>,
) {

    /**
     * 串行化下滑手势的切换：先查再写不是原子的，快速连滑会双双看到「空」而重复插入。
     * 锁住读-查-写整段后，两次滑动退化成正确的两次 toggle（加一条再删一条），不会出现重复。
     */
    private val toggleMutex = Mutex()

    interface Host {
        /** 打开/关闭书签弹层的同时收起阅读菜单。 */
        fun setActiveSheet(sheet: ReadBookSheet?)

        fun emitEffect(effect: ReadBookEffect)
    }

    /**
     * 维护 [ReaderBookmarkState]：渲染层要在主线程热路径上同步判定「本页是否有书签」画角标，
     * 不能起协程查库，所以把当前书的书签位置整份缓存进会话快照。VM 构造时调一次。
     *
     * 收集随 [scope] 结束，`finally` 里清掉快照——否则下一本书会读到上一本的书签位置。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            try {
                bookKey
                    .distinctUntilChanged()
                    .flatMapLatest { key ->
                        if (key == null) {
                            flowOf(key to emptyList())
                        } else {
                            bookmarkRepository.flowByBook(key.first, key.second).map { key to it }
                        }
                    }
                    .collect { (key, bookmarks) ->
                        if (key == null) {
                            ReaderBookmarkState.clear()
                        } else {
                            ReaderBookmarkState.update(key.first, key.second, bookmarks)
                        }
                        host.emitEffect(ReadBookEffect.UpBookmarkBadge)
                    }
            } finally {
                ReaderBookmarkState.clear()
            }
        }
    }

    /**
     * 下滑手势：本页无书签则直接存一条（不弹编辑器），已有则删掉离当前阅读位置最近的一条。
     *
     * 页范围取 `[页首位置, 下一页页首位置)`——末页取到章节已排版长度，
     * 与 [addForCurrentPage] 存入的 `ReadBook.durChapterPos` 落点一致。
     * 消息页/空页与角标判定一致地跳过（页首位置取 `textLines.first()`，空页会抛异常）。
     * 整体串行化：先查后写不是原子的，快速连滑会双双看到「空」而重复插入。
     */
    fun toggleForCurrentPage() {
        scope.launch(IO) {
            toggleMutex.withLock {
                val book = ReadBook.book ?: return@withLock
                val chapter = ReadBook.curTextChapter ?: return@withLock
                val pageIndex = ReadBook.durPageIndex
                val page = chapter.getPage(pageIndex) ?: return@withLock
                if (page.isMsgPage || page.lineSize <= 0) return@withLock
                val startPos = page.chapterPosition
                val endPos = chapter.getPage(pageIndex + 1)?.chapterPosition
                    ?: (startPos + page.charSize)
                val existing = bookmarkRepository.getByChapterRange(
                    bookName = book.name,
                    bookAuthor = book.author,
                    chapterIndex = chapter.chapter.index,
                    startPos = startPos,
                    endPos = endPos,
                )
                if (existing.isEmpty()) {
                    bookmarkRepository.save(
                        Bookmark(
                            bookName = book.name,
                            bookAuthor = book.author,
                            bookUrl = book.bookUrl,
                            chapterIndex = chapter.chapter.index,
                            chapterName = chapter.title,
                            chapterPos = ReadBook.durChapterPos,
                            bookText = page.text.replace(BOOK_TEXT_MARKS, "").trim(),
                            content = "",
                        )
                    )
                    host.emitEffect(
                        ReadBookEffect.ShowToast(appCtx.getString(R.string.bookmark_added))
                    )
                } else {
                    // 只删离当前阅读位置最近的一条：同一页可能有多条书签，不应整页误删。
                    // 书签与划线笔记完全独立，这里只删书签本身。
                    val nearest = existing.minByOrNull { abs(it.chapterPos - ReadBook.durChapterPos) }
                        ?: return@withLock
                    bookmarkRepository.delete(nearest)
                    host.emitEffect(
                        ReadBookEffect.ShowToast(appCtx.getString(R.string.bookmark_removed))
                    )
                }
            }
        }
    }

    /** 从菜单「加书签」进入：以当前页正文预填草稿。 */
    fun addForCurrentPage() {
        scope.launch(IO) {
            val book = ReadBook.book ?: return@launch
            val chapter = ReadBook.curTextChapter ?: return@launch
            val page = chapter.pages.getOrNull(ReadBook.durPageIndex) ?: return@launch
            val bookmark = Bookmark(
                bookName = book.name,
                bookAuthor = book.author,
                bookUrl = book.bookUrl,
                chapterIndex = chapter.chapter.index,
                chapterName = chapter.title,
                chapterPos = ReadBook.durChapterPos,
                bookText = page.text,
                content = "",
            )
            withContext(Main) {
                host.setActiveSheet(ReadBookSheet.Bookmark(bookmark))
            }
        }
    }

    /** 从划词菜单「加书签」进入：草稿已由调用方按选中文本构造好。 */
    fun openEditor(bookmark: Bookmark) {
        host.setActiveSheet(ReadBookSheet.Bookmark(bookmark))
    }

    fun save(bookmark: Bookmark) {
        scope.launch(IO) {
            bookmarkRepository.save(bookmark)
            host.setActiveSheet(null)
        }
    }

    fun delete(bookmark: Bookmark) {
        scope.launch(IO) {
            bookmarkRepository.delete(bookmark)
            host.setActiveSheet(null)
        }
    }

    private companion object {
        /** 与 ReadBookController.addBookmark 一致：剔除正文里的排版占位符。 */
        val BOOK_TEXT_MARKS = Regex("[袮꧁]")
    }
}
