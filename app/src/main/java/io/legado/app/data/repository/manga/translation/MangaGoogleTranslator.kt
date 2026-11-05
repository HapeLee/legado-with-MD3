package io.legado.app.data.repository.manga.translation

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * gtx returns one concatenated translation and rewrites whitespace, so ID alignment has to happen
 * here: every distinct text becomes its own request, and identical lettering ("……", "！") is sent
 * once and mapped back to all of its regions. At most [concurrency] requests run together; three
 * keeps the free endpoint stable while a long chapter still finishes.
 */
internal class MangaGoogleTranslator(
    private val request: suspend (text: String, targetLanguage: String) -> Result<String>,
    private val readCache: suspend (key: String) -> String? = { null },
    private val writeCache: suspend (key: String, value: String) -> Unit = { _, _ -> },
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) {

    suspend fun translate(texts: List<String>, targetLanguage: String): Map<String, String> {
        val unique = texts.distinct()
        val gate = Semaphore(concurrency)
        return coroutineScope {
            unique.map { text ->
                async {
                    val key = cacheKey(targetLanguage, text)
                    readCache(key)?.takeIf { it.isNotBlank() }?.let { return@async text to it }
                    val translated = gate.withPermit { request(text, targetLanguage) }
                        .getOrElse { throw MangaGoogleTranslationException(text, it) }
                    if (translated.isBlank()) {
                        throw MangaGoogleTranslationException(text, IllegalStateException("Empty translation"))
                    }
                    writeCache(key, translated)
                    text to translated
                }
            }.map { it.await() }.toMap()
        }
    }

    private fun cacheKey(targetLanguage: String, text: String) = "$VERSION|$targetLanguage|$text"

    companion object {
        const val VERSION = "google-gtx-v1"
        const val DEFAULT_CONCURRENCY = 3
    }
}

/** Carries the failing source text so callers can report it without dumping the whole page. */
internal class MangaGoogleTranslationException(val text: String, cause: Throwable) :
    IllegalStateException("Google translation failed for \"${text.take(24)}\"", cause)
