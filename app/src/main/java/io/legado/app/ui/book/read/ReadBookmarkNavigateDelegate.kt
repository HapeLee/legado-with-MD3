package io.legado.app.ui.book.read

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.feature.reader.core.semantic.ReaderSemanticLocation
import io.legado.app.domain.usecase.BookmarkTargetVerdict
import io.legado.app.domain.usecase.RelocateMarkingTargetUseCase
import io.legado.app.domain.usecase.VerifyBookmarkTargetUseCase
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import io.legado.app.feature.reader.platform.JsoupReaderHtmlParser
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.readaloud.cast.CastAssignmentStore
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBookSemanticHtml
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val castLabelsProvider: suspend (Book, BookChapter) -> Map<Int, String> = { book, chapter ->
        CastAssignmentStore.labelsForChapter(book.bookUrl, chapter.index)
    },
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
            val anchor = bookmark.bookText.takeIf { it.isNotBlank() }?.let { text ->
                TextProcessAnchor(bookmark.chapterIndex, bookmark.chapterPos, text, normalizedTextHash = "")
            }
            val current = if (verdict is BookmarkTargetVerdict.Match) {
                validatedPosition(book, bookmark.chapterIndex, bookmark.chapterPos, anchor, pageText = true)
            } else null
            if (current != null) {
                host.jumpToChapter(bookmark.chapterIndex, current)
            } else {
                val relocated = anchor?.let { relocateMarking(book, bookmark.chapterName, it) }
                if (relocated != null) host.jumpToChapter(relocated.chapterIndex, relocated.chapterPosition)
                else host.setPendingTarget(PendingBookmarkTarget(bookmark.chapterIndex, bookmark.chapterPos,
                    if (verdict is BookmarkTargetVerdict.Match) BookmarkTargetVerdict.ContentMismatch else verdict))
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
            val current = if (verdict is BookmarkTargetVerdict.Match) {
                validatedPosition(book, chapterIndex, chapterPos, anchor)
            } else null
            if (current != null) host.jumpToChapter(chapterIndex, current)
            else {
                relocateMarking(book, marking.chapterName, anchor)
                    ?.let { host.jumpToChapter(it.chapterIndex, it.chapterPosition) }
                    ?: host.setPendingTarget(PendingBookmarkTarget(chapterIndex, chapterPos,
                        if (verdict is BookmarkTargetVerdict.Match) BookmarkTargetVerdict.ContentMismatch else verdict))
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

    fun navigateToInternalLink(link: String) {
        if (LocalBookSemanticHtml.classifyLink(link) != LocalBookSemanticHtml.LinkKind.INTERNAL) return
        val book = ReadBook.book ?: return
        val currentIndex = ReadBook.durChapterIndex
        val fragment = link.substringAfter("#", "").trim()
        scope.launch {
            val (targetIndex, targetPos) = withContext(Dispatchers.IO) {
                val chapters = bookRepository.getChapters(book.bookUrl)
                val resolvedIndex = LocalBookSemanticHtml.resolveInternalLinkTarget(link, chapters, currentIndex)
                    ?: return@withContext null to null
                val targetChapter = chapters.firstOrNull { it.index == resolvedIndex }
                val pos = if (fragment.isNotEmpty() && targetChapter != null) {
                    if (targetChapter.startFragmentId.equals(fragment, ignoreCase = true)) {
                        0
                    } else {
                        parseChapterSource(book, targetChapter)
                            ?.blocks
                            ?.filterIsInstance<ReaderChapterSourceBlock.Html>()
                            ?.firstNotNullOfOrNull { block ->
                                JsoupReaderHtmlParser.findAnchorPosition(
                                    block.value,
                                    fragment,
                                    block.chapterPosition,
                                )
                            }
                    }
                } else null
                resolvedIndex to pos
            }
            if (targetIndex != null && (targetIndex != currentIndex || targetPos != null)) {
                host.jumpToChapter(targetIndex, targetPos ?: 0)
            }
        }
    }

    /**
     * Only inspect locally available chapter content. A missing or ambiguous match keeps the
     * existing confirmation flow, rather than fetching network content while the sheet is open.
     */
    private suspend fun relocateMarking(
        book: Book,
        chapterName: String,
        anchor: TextProcessAnchor,
    ): RelocateMarkingTargetUseCase.Target? = withContext(Dispatchers.IO) {
        val candidates = bookRepository.getChapters(book.bookUrl)
            .filter {
                it.index == anchor.chapterIndex ||
                        (chapterName.isNotBlank() && it.title == chapterName)
            }
            .distinctBy { it.index }
            .mapNotNull { chapter ->
                val source = parseChapterSource(book, chapter) ?: return@mapNotNull null
                RelocateMarkingTargetUseCase.Candidate(
                    chapterIndex = chapter.index,
                    content = source.semanticContent,
                )
            }
            .toList()
        relocateMarkingTargetUseCase.locate(anchor, candidates)
    }

    private suspend fun validatedPosition(
        book: Book, chapterIndex: Int, position: Int, anchor: TextProcessAnchor?, pageText: Boolean = false,
    ): Int? = withContext(Dispatchers.IO) {
        val chapter = bookRepository.getChapters(book.bookUrl).firstOrNull { it.index == chapterIndex }
            ?: return@withContext null
        val content = parseChapterSource(book, chapter)?.semanticContent ?: return@withContext null
        val text = anchor?.selectedText
        if (text.isNullOrBlank()) return@withContext ReaderSemanticLocation.clampOffset(content, position)
        if (ReaderSemanticLocation.matchesAt(content, position, text, anchor?.contextBefore.orEmpty(), anchor?.contextAfter.orEmpty())) return@withContext position
        // Page bookmarks store the visible page, which need not begin at the saved reading
        // position. Treat a unique containing page as evidence, not as an exact-start anchor.
        if (pageText && ReaderSemanticLocation.matchesContainingText(content, position, text)) return@withContext position
        null
    }

    private suspend fun parseChapterSource(book: Book, chapter: BookChapter): ReaderChapterSource? {
        val rawContent = BookHelp.getContent(book, chapter) ?: return null
        val processor = ContentProcessor.get(book)
        val settings = processor.captureSettings()
        val processed = processor.getContent(book, chapter, rawContent, includeTitle = false,
            paragraphIndent = null, settings = settings)
        return ReaderChapterSourceParser.parse(
            chapterIndex = chapter.index,
            title = "",
            paragraphs = processed.textList,
            includeTitle = false,
            adaptSpecialStyle = settings.adaptSpecialStyle,
            htmlSemanticTextResolver = AndroidReaderHtmlSemanticTextResolver,
            castLabels = if (readAloudSettingsGateway.currentSettings.multiRoleCast) {
                castLabelsProvider(book, chapter)
            } else emptyMap(),
        )
    }

    private fun BookMarking.anchor(): TextProcessAnchor? =
        GSON.fromJsonObject<TextProcessAnchor>(anchorJson).getOrNull()
}
