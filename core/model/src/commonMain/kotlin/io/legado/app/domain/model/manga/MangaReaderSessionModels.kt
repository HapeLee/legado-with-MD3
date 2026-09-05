package io.legado.app.domain.model.manga

import kotlin.uuid.Uuid

@JvmInline
value class MangaSessionId(val value: String) {
    companion object {
        fun create(): MangaSessionId = MangaSessionId(Uuid.random().toString())
    }
}

data class MangaBookState(
    val bookUrl: String,
    val name: String,
    val author: String,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val sourceOrigin: String?,
    val sourceName: String,
    val sourceType: Int?,
    val inBookshelf: Boolean,
    val scrollMode: Int?,
    val sidePaddingDp: Int?,
    val isLocal: Boolean,
    /** TOC titles are retained so a chapter transition stays informative before pages load. */
    val chapterTitles: List<String>,
)

data class MangaBookPresentation(
    val scrollMode: Int?,
    val sidePaddingDp: Int?,
)

data class OpenedMangaBook(
    val book: MangaBookState,
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterCount: Int,
    val newerProgress: MangaProgressState? = null,
)

data class MangaLoadToken(
    val sessionId: MangaSessionId,
    val revision: Long,
    val bookUrl: String,
    val chapterIndex: Int,
)

sealed interface MangaChapterState {
    data object Empty : MangaChapterState
    data class Loading(val token: MangaLoadToken) : MangaChapterState
    data class Ready(
        val token: MangaLoadToken,
        val chapter: MangaChapterContent,
    ) : MangaChapterState
    data class Failed(
        val token: MangaLoadToken,
        val message: String,
        val retryable: Boolean = true,
    ) : MangaChapterState
}

data class MangaSessionState(
    val sessionId: MangaSessionId,
    val revision: Long = 0,
    val book: MangaBookState? = null,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val chapterCount: Int = 0,
    val previousChapter: MangaChapterState = MangaChapterState.Empty,
    val currentChapter: MangaChapterState = MangaChapterState.Empty,
    val nextChapter: MangaChapterState = MangaChapterState.Empty,
    val resumed: Boolean = false,
    val openError: String? = null,
) {
    fun accepts(token: MangaLoadToken): Boolean =
        token.sessionId == sessionId &&
            token.revision == revision &&
            token.bookUrl == book?.bookUrl

    fun tokenFor(chapterIndex: Int): MangaLoadToken? = book?.let {
        MangaLoadToken(sessionId, revision, it.bookUrl, chapterIndex)
    }
}

sealed interface MangaSessionCommand {
    data class Open(
        val bookUrl: String?,
        val inBookshelf: Boolean,
        val chapterChanged: Boolean,
    ) : MangaSessionCommand
    data class OpenChapter(val chapterIndex: Int, val pageIndex: Int = 0) : MangaSessionCommand
    data object PreviousChapter : MangaSessionCommand
    data object NextChapter : MangaSessionCommand
    data class PromoteVisibleChapter(val chapterIndex: Int, val pageIndex: Int) : MangaSessionCommand
    data class VisiblePageChanged(val chapterIndex: Int, val pageIndex: Int) : MangaSessionCommand
    data object PersistVisibleProgress : MangaSessionCommand
    data class RetryChapter(val chapterIndex: Int) : MangaSessionCommand
    data class PrefetchCountChanged(val count: Int) : MangaSessionCommand
    data class BookPresentationObserved(
        val bookUrl: String,
        val presentation: MangaBookPresentation,
    ) : MangaSessionCommand
    data class ApplyProgress(val progress: MangaProgressState) : MangaSessionCommand
    data class ChapterLoaded(
        val token: MangaLoadToken,
        val result: Result<MangaChapterContent>,
    ) : MangaSessionCommand
    data object Resume : MangaSessionCommand
    data object Pause : MangaSessionCommand
    data object NetworkAvailable : MangaSessionCommand
    data object Close : MangaSessionCommand
}

sealed interface MangaSessionEvent {
    data class ConfirmProgress(val progress: MangaProgressState) : MangaSessionEvent
    data class Message(val message: String) : MangaSessionEvent
    data class OpenPaymentUrl(
        val url: String,
        val sourceOrigin: String?,
        val sourceName: String?,
        val sourceType: Int?,
    ) : MangaSessionEvent
}

data class MangaProgressState(
    val bookName: String,
    val bookAuthor: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterTitle: String?,
    val updatedAt: Long,
)

data class MangaChapterContent(
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapterUrl: String?,
    val pages: List<MangaPageContent>,
    val isVolume: Boolean,
)

data class MangaPageContent(
    val imageUrl: String,
    val pageIndex: Int,
    val pageCount: Int,
)
