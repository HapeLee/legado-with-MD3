package io.legado.app.ui.book.read

import io.legado.app.data.entities.Bookmark
import io.legado.app.data.repository.BookmarkRepository
import io.legado.app.model.ReadBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                chapterPos = ReadBook.durPageIndex,
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
}
