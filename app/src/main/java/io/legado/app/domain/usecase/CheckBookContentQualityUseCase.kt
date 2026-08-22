package io.legado.app.domain.usecase

import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.ContentQualityConfig
import io.legado.app.domain.model.ContentQualityProgress
import io.legado.app.domain.model.ContentQualityResult
import io.legado.app.domain.model.ContentQualityStage
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class CheckBookContentQualityUseCase(
    private val chapterContentUseCase: GetChapterContentUseCase,
) {

    suspend fun execute(
        books: List<SearchBook>,
        config: ContentQualityConfig,
        onProgress: suspend (ContentQualityProgress) -> Unit,
    ): List<ContentQualityResult> {
        val results = ArrayList<ContentQualityResult>(books.size)
        books.forEachIndexed { index, book ->
            coroutineContext.ensureActive()
            onProgress(
                ContentQualityProgress(
                    stage = ContentQualityStage.ChapterCount,
                    processedBooks = index,
                    totalBooks = books.size,
                    currentBookName = book.name,
                )
            )
            results += checkBook(book, config, index, books.size, onProgress)
        }
        onProgress(
            ContentQualityProgress(
                stage = ContentQualityStage.Completed,
                processedBooks = books.size,
                totalBooks = books.size,
            )
        )
        return results
    }

    private suspend fun checkBook(
        book: SearchBook,
        config: ContentQualityConfig,
        processedBooks: Int,
        totalBooks: Int,
        onProgress: suspend (ContentQualityProgress) -> Unit,
    ): ContentQualityResult {
        val bookKey = book.qualityKey()
        val sourceName = book.originName.ifBlank { book.origin }
        return runCatching {
            val (toc, _) = chapterContentUseCase.getToc(book.toBook())
            val requestedMaximum = Regex("\\d+")
                .findAll(config.chapterSpec)
                .mapNotNull { it.value.toIntOrNull() }
                .maxOrNull()
            if (requestedMaximum != null && requestedMaximum > toc.size) {
                return@runCatching ContentQualityResult(
                    bookKey = bookKey,
                    sourceName = sourceName,
                    sampledChapterCount = 0,
                    matchedChapterCount = 0,
                    keywordHits = 0,
                    excluded = true,
                    errorMessage = "章节数不足",
                )
            }
            val chapterIndices = parseChapterIndices(config.chapterSpec, toc.size)
            if (chapterIndices.isEmpty()) {
                return@runCatching ContentQualityResult(
                    bookKey = bookKey,
                    sourceName = sourceName,
                    sampledChapterCount = 0,
                    matchedChapterCount = 0,
                    keywordHits = 0,
                    excluded = true,
                    errorMessage = "没有可检测的章节",
                )
            }

            var sampledChapterCount = 0
            var matchedChapterCount = 0
            var keywordHits = 0
            chapterIndices.forEachIndexed { _, chapterIndex ->
                coroutineContext.ensureActive()
                onProgress(
                    ContentQualityProgress(
                        stage = ContentQualityStage.ContentCleaning,
                        processedBooks = processedBooks,
                        totalBooks = totalBooks,
                        currentBookName = book.name,
                    )
                )
                val chapter = toc[chapterIndex - 1]
                val nextChapterUrl = toc.getOrNull(chapterIndex)?.url
                // 单章获取失败（网络抖动等瞬时错误）只跳过该章，不把整个书源判为排除
                val content = runCatching {
                    chapterContentUseCase.getContent(
                        book = book.toBook(),
                        chapter = chapter,
                        nextChapterUrl = nextChapterUrl,
                    )
                }.getOrNull() ?: return@forEachIndexed
                sampledChapterCount++
                val cleaned = cleanContent(content, config.skipHeadChars)
                onProgress(
                    ContentQualityProgress(
                        stage = ContentQualityStage.KeywordMatching,
                        processedBooks = processedBooks,
                        totalBooks = totalBooks,
                        currentBookName = book.name,
                    )
                )
                val chapterHits = config.keywords.sumOf { keyword ->
                    countOccurrences(cleaned, keyword)
                }
                keywordHits += chapterHits
                if (chapterHits > 0) {
                    matchedChapterCount++
                }
            }

            val excluded = sampledChapterCount > 0 && keywordHits == 0
            onProgress(ContentQualityProgress(ContentQualityStage.Excluding, processedBooks, totalBooks, book.name))
            ContentQualityResult(
                bookKey = bookKey,
                sourceName = sourceName,
                sampledChapterCount = sampledChapterCount,
                matchedChapterCount = matchedChapterCount,
                keywordHits = keywordHits,
                excluded = excluded,
            )
        }.getOrElse { error ->
            // 整本检测失败（如目录拉取异常）：不算“排除”，只记录原因，避免瞬时错误误杀书源
            ContentQualityResult(
                bookKey = bookKey,
                sourceName = sourceName,
                sampledChapterCount = 0,
                matchedChapterCount = 0,
                keywordHits = 0,
                excluded = false,
                errorMessage = error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: error.javaClass.simpleName,
            )
        }
    }

    companion object {
        fun parseChapterIndices(spec: String, totalChapterCount: Int): List<Int> {
            if (totalChapterCount <= 0) return emptyList()
            return Regex("\\d+(?:\\s*[-~至到]\\s*\\d+)?")
                .findAll(spec)
                .flatMap { match ->
                    val parts = match.value.split(Regex("\\s*[-~至到]\\s*"))
                    val start = parts.first().toIntOrNull() ?: return@flatMap emptySequence()
                    val end = parts.getOrNull(1)?.toIntOrNull() ?: start
                    (minOf(start, end)..maxOf(start, end)).asSequence()
                }
                .filter { it in 1..totalChapterCount }
                .distinct()
                .toList()
        }

        internal fun cleanContent(content: String, skipHeadChars: Int): String {
            return content
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .drop(skipHeadChars.coerceAtLeast(0))
        }

        internal fun countOccurrences(content: String, keyword: String): Int {
            if (keyword.isBlank()) return 0
            var count = 0
            var start = 0
            while (start < content.length) {
                val index = content.indexOf(keyword, start, ignoreCase = true)
                if (index < 0) break
                count++
                start = index + keyword.length.coerceAtLeast(1)
            }
            return count
        }

        private fun SearchBook.qualityKey(): String = "$origin:$bookUrl"
    }
}
