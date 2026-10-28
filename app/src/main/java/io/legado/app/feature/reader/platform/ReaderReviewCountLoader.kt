package io.legado.app.feature.reader.platform

import android.util.LruCache
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.feature.reader.core.layout.ReviewCountParser
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.CancellationException

/**
 * 服务端段评图（`chapter_review` / `para_review`）的评论数。
 *
 * 只有这两类接口才会请求：取回 SVG 文本读出 `<text>` 数字，读到才允许应用自绘气泡，
 * 读不到就当普通图片。结果（含失败）按 URL 缓存，同一章重排、翻页不重复请求。
 */
object ReaderReviewCountLoader {
    private const val FAILED = -1
    private const val MAX_ENTRIES = 128
    private const val READ_TIMEOUT_MS = 1500L
    private const val CALL_TIMEOUT_MS = 2000L

    private val cache = LruCache<String, Int>(MAX_ENTRIES)

    /** null = 还没取过；-1 = 取过但失败/没数字。 */
    fun cached(source: String): Int? {
        val url = ReviewCountParser.serverReviewUrl(source) ?: return null
        return synchronized(cache) { cache.get(url) }
    }

    suspend fun resolve(
        source: String,
        book: Book,
        bookSource: BookSource?,
        chapter: BookChapter,
    ): Int? {
        val url = ReviewCountParser.serverReviewUrl(source) ?: return null
        synchronized(cache) { cache.get(url) }?.let { return it.takeIf { value -> value > 0 } }
        val count = try {
            val body = AnalyzeUrl(
                mUrl = url,
                source = bookSource,
                ruleData = book,
                chapter = chapter,
                baseUrl = chapter.url,
                readTimeout = READ_TIMEOUT_MS,
                callTimeout = CALL_TIMEOUT_MS,
            ).getStrResponseAwait().body
            body?.let(ReviewCountParser::fromSvgText) ?: FAILED
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            FAILED
        }
        synchronized(cache) { cache.put(url, count) }
        return count.takeIf { it > 0 }
    }
}
