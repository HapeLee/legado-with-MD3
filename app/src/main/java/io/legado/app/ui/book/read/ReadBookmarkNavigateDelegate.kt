package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.usecase.BookmarkTargetVerdict
import io.legado.app.domain.usecase.RelocateMarkingTargetUseCase
import io.legado.app.domain.usecase.VerifyBookmarkTargetUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 书签/笔记跳转校验域（无自持渲染状态）。
 *
 * 换源后 bookUrl 会变、目录会重排，书签/笔记的 chapterIndex/chapterPos 是创建时源里的
 * 坐标，可能偏移。跳转前比对创建时的源指纹与章节标题，不通过则交给 Host 弹「仍跳转」。
 *
 * 校验逻辑（取目标章节标题 + verify use case + 判定）住在本域；确认框状态
 * [PendingBookmarkTarget] 是瞬态对话框状态（与 activeDialog 同性质），留 UiState。
 */
class ReadBookmarkNavigateDelegate(
    private val scope: CoroutineScope,
    private val bookRepository: BookRepository,
    private val verifyUseCase: VerifyBookmarkTargetUseCase,
    private val relocateMarkingTargetUseCase: RelocateMarkingTargetUseCase,
    private val host: Host,
) {

    interface Host {
        val pendingTarget: PendingBookmarkTarget?
        fun jumpToChapter(chapterIndex: Int, chapterPos: Int)
        fun setPendingTarget(pending: PendingBookmarkTarget?)
    }

    fun navigateToBookmark(bookmark: Bookmark) {
        val book = ReadBook.book ?: return
        scope.launch {
            val targetTitle = bookRepository.getChapterTitle(
                book.name, book.author, bookmark.chapterIndex,
            )
            val verdict = verifyUseCase.verify(
                currentBookUrl = book.bookUrl,
                targetChapterTitle = targetTitle,
                storedBookUrl = bookmark.bookUrl,
                storedChapterName = bookmark.chapterName,
            )
            if (verdict is BookmarkTargetVerdict.Match) {
                host.jumpToChapter(bookmark.chapterIndex, bookmark.chapterPos)
            } else {
                host.setPendingTarget(
                    PendingBookmarkTarget(bookmark.chapterIndex, bookmark.chapterPos, verdict)
                )
            }
        }
    }

    fun navigateToMarking(marking: BookMarking) {
        val book = ReadBook.book ?: return
        val anchor = marking.anchor() ?: return
        val chapterIndex = marking.chapterIndex ?: anchor.chapterIndex
        val chapterPos = anchor.chapterPosition ?: 0
        scope.launch {
            val targetTitle = bookRepository.getChapterTitle(
                book.name, book.author, chapterIndex,
            )
            val verdict = verifyUseCase.verify(
                currentBookUrl = book.bookUrl,
                targetChapterTitle = targetTitle,
                storedBookUrl = marking.bookUrl,
                storedChapterName = marking.chapterName,
            )
            if (verdict is BookmarkTargetVerdict.Match) {
                host.jumpToChapter(chapterIndex, chapterPos)
            } else {
                relocateMarking(book, marking.chapterName, anchor)
                    ?.let { host.jumpToChapter(it.chapterIndex, it.chapterPosition) }
                    ?: host.setPendingTarget(
                        PendingBookmarkTarget(
                            chapterIndex,
                            chapterPos,
                            verdict
                        )
                    )
            }
        }
    }

    fun confirmJump() {
        val pending = host.pendingTarget ?: return
        host.setPendingTarget(null)
        host.jumpToChapter(pending.chapterIndex, pending.chapterPos)
    }

    fun cancelJump() {
        host.setPendingTarget(null)
    }

    /**
     * Only inspect locally available chapter content. A missing or ambiguous match keeps the
     * existing confirmation flow, rather than fetching network content while the sheet is open.
     */
    private suspend fun relocateMarking(
        book: io.legado.app.data.entities.Book,
        chapterName: String,
        anchor: TextProcessAnchor,
    ): RelocateMarkingTargetUseCase.Target? {
        val processor = ContentProcessor.get(book)
        val candidates = bookRepository.getChapters(book.bookUrl)
            .asSequence()
            .filter {
                it.index == anchor.chapterIndex ||
                        (chapterName.isNotBlank() && it.title == chapterName)
            }
            .distinctBy { it.index }
            .mapNotNull { chapter ->
                val rawContent = BookHelp.getContent(book, chapter) ?: return@mapNotNull null
                RelocateMarkingTargetUseCase.Candidate(
                    chapterIndex = chapter.index,
                    content = processor.getContent(book, chapter, rawContent, includeTitle = false)
                        .toString(),
                )
            }
            .toList()
        return relocateMarkingTargetUseCase.locate(anchor, candidates)
    }

    private fun BookMarking.anchor(): TextProcessAnchor? =
        GSON.fromJsonObject<TextProcessAnchor>(anchorJson).getOrNull()
}
