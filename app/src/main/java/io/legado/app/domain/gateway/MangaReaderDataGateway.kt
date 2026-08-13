package io.legado.app.domain.gateway

import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaBookPresentation
import io.legado.app.domain.model.manga.MangaProgressState
import io.legado.app.domain.model.manga.OpenedMangaBook
import kotlinx.coroutines.flow.Flow

interface MangaReaderDataGateway {
    fun observeBookPresentation(bookUrl: String): Flow<MangaBookPresentation>

    suspend fun openBook(
        bookUrl: String?,
        inBookshelf: Boolean,
        chapterChanged: Boolean,
    ): OpenedMangaBook

    suspend fun loadChapter(bookUrl: String, chapterIndex: Int): MangaChapterContent

    suspend fun prefetchChapter(bookUrl: String, chapterIndex: Int)

    suspend fun persistProgress(
        bookUrl: String,
        chapterIndex: Int,
        pageIndex: Int,
    )

    suspend fun applyProgress(bookUrl: String, progress: MangaProgressState)

    suspend fun resume(bookUrl: String)

    suspend fun pause(bookUrl: String, inBookshelf: Boolean)

    suspend fun syncProgress(bookUrl: String): MangaProgressState?
}
