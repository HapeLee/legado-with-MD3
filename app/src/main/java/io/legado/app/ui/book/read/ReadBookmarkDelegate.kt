package io.legado.app.ui.book.read

import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.repository.BookmarkRepository
import io.legado.app.model.ReadBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

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
) {

    interface Host {
        /** 打开/关闭书签弹层的同时收起阅读菜单。 */
        fun setActiveSheet(sheet: ReadBookSheet?)

        fun emitEffect(effect: ReadBookEffect)
    }

    /**
     * 下滑手势：本页无书签则直接存一条（不弹编辑器），已有则删掉本页范围内的全部书签。
     *
     * 页范围取 `[页首位置, 下一页页首位置)`——末页取到章节已排版长度，
     * 与 [addForCurrentPage] 存入的 `ReadBook.durChapterPos` 落点一致。
     */
    fun toggleForCurrentPage() {
        scope.launch(IO) {
            val book = ReadBook.book ?: return@launch
            val chapter = ReadBook.curTextChapter ?: return@launch
            val pageIndex = ReadBook.durPageIndex
            val page = chapter.getPage(pageIndex) ?: return@launch
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
                bookmarkRepository.deleteAll(existing)
                host.emitEffect(
                    ReadBookEffect.ShowToast(appCtx.getString(R.string.bookmark_removed))
                )
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
