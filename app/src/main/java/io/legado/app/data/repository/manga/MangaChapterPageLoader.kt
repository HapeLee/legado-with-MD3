package io.legado.app.data.repository.manga

import android.app.Application
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaPageContent
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.toList

/** Selects where chapter page metadata comes from without leaking that choice into presentation. */
internal class MangaChapterPageLoader(
    private val application: Application,
    private val database: AppDatabase,
) : AutoCloseable {

    private val retainedChapters =
        java.util.concurrent.ConcurrentHashMap<ChapterKey, LoadedChapter>()

    suspend fun load(
        book: Book,
        chapter: BookChapter,
        retain: Boolean = true,
    ): MangaChapterContent {
        val key = ChapterKey(book.bookUrl, chapter.index)
        retainedChapters[key]?.let { return it.content }
        val content = BookHelp.getContent(book, chapter)
            ?: RemoteChapterContentLoader(application, database).load(book, chapter)
        val imageUrls = BookHelp.flowImages(chapter, content)
            .distinctUntilChanged()
            .toList()
        if (imageUrls.isEmpty() && !chapter.isVolume) {
            throw NoStackTraceException(application.getString(io.legado.app.R.string.content_empty))
        }
        return MangaChapterContent(
            chapterIndex = chapter.index,
            chapterTitle = chapter.title,
            chapterUrl = chapter.url,
            pages = imageUrls.mapIndexed { index, url ->
                MangaPageContent(url, index, imageUrls.size)
            },
            isVolume = chapter.isVolume,
        ).also { if (retain) retainedChapters[key] = LoadedChapter(it) }
    }

    fun retain(bookUrl: String, chapterIndexes: Set<Int>) {
        retainedChapters.entries.removeAll { (key, loaded) ->
            val release = key.bookUrl != bookUrl || key.chapterIndex !in chapterIndexes
            if (release) loaded.close()
            release
        }
    }

    override fun close() {
        retainedChapters.values.forEach(LoadedChapter::close)
        retainedChapters.clear()
    }

    private data class ChapterKey(val bookUrl: String, val chapterIndex: Int)

    /** Boundary for future archive/directory handles; current URL-backed chapters own no open stream. */
    private class LoadedChapter(val content: MangaChapterContent) : AutoCloseable {
        override fun close() = Unit
    }
}

private class RemoteChapterContentLoader(
    private val application: Application,
    private val database: AppDatabase,
) {
    suspend fun load(book: Book, chapter: BookChapter): String {
        val source = database.bookSourceDao.getBookSource(book.origin)
            ?: throw NoStackTraceException(application.getString(io.legado.app.R.string.manga_reader_details_failed))
        val nextUrl = database.bookChapterDao.getChapter(book.bookUrl, chapter.index + 1)?.url
        return WebBook.getContentAwait(source, book, chapter, nextUrl).also { content ->
            if (content.isEmpty() && !chapter.isVolume) {
                throw NoStackTraceException(application.getString(io.legado.app.R.string.content_empty))
            }
        }
    }
}
