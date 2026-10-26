package io.legado.app.data.repository

import androidx.media3.common.MediaItem
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** 视频书源的规则、网络与缓存操作；WebBook 的 Await API 本身不会切换线程。 */
class VideoContentRepository {

    suspend fun loadBookInfo(source: BookSource, book: Book) {
        withContext(Dispatchers.IO) {
            WebBook.getBookInfoAwait(source, book)
        }
    }

    suspend fun loadChapters(source: BookSource, book: Book): List<BookChapter> =
        withContext(Dispatchers.IO) {
            WebBook.getChapterListAwait(source, book).getOrThrow()
        }

    suspend fun loadMedia(source: BookSource, book: Book, chapter: BookChapter): MediaItem? =
        withContext(Dispatchers.IO) {
            val content = WebBook.getContentAwait(source, book, chapter)
            if (content.isBlank()) return@withContext null
            AnalyzeUrl(
                content,
                source = source,
                ruleData = book,
                chapter = chapter,
                coroutineContext = coroutineContext,
            ).getMediaItem()
        }
}
