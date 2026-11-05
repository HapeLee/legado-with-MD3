package io.legado.app.data.repository.manga.translation

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MangaGoogleTranslatorTest {

    @Test fun `identical lettering is requested once and mapped back to every region`() = runTest {
        val requests = mutableListOf<String>()
        val translator = MangaGoogleTranslator(request = { text, target ->
            requests += "$target:$text"
            Result.success("[$text]")
        })
        val result = translator.translate(listOf("……", "你好", "……"), "zh-CN")
        assertEquals(listOf("zh-CN:……", "zh-CN:你好"), requests)
        assertEquals(mapOf("……" to "[……]", "你好" to "[你好]"), result)
    }

    @Test fun `at most three requests run together`() = runTest {
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        val translator = MangaGoogleTranslator(request = { text, _ ->
            val current = inFlight.incrementAndGet()
            peak.updateAndGet { maxOf(it, current) }
            delay(10)
            inFlight.decrementAndGet()
            Result.success("t:$text")
        })
        val result = translator.translate((1..10).map { "t$it" }, "zh-CN")
        assertEquals(10, result.size)
        assertEquals(MangaGoogleTranslator.DEFAULT_CONCURRENCY, peak.get())
    }

    @Test fun `cached text skips the request`() = runTest {
        val requests = mutableListOf<String>()
        val written = mutableMapOf<String, String>()
        val translator = MangaGoogleTranslator(
            request = { text, _ -> requests += text; Result.success("new:$text") },
            readCache = { key -> if (key.endsWith("|cached")) "old" else null },
            writeCache = { key, value -> written[key] = value },
        )
        val result = translator.translate(listOf("cached", "fresh"), "zh-CN")
        assertEquals(listOf("fresh"), requests)
        assertEquals("old", result["cached"])
        assertTrue(written.keys.none { it.endsWith("|cached") })
        assertTrue(written.keys.any { it.endsWith("|fresh") })
    }

    @Test fun `failed or empty responses abort the page instead of falling back`() = runTest {
        val failed = runCatching {
            MangaGoogleTranslator(request = { _, _ -> Result.failure(IllegalStateException("HTTP 429")) })
                .translate(listOf("a"), "zh-CN")
        }
        assertTrue(failed.exceptionOrNull() is MangaGoogleTranslationException)
        assertEquals("a", (failed.exceptionOrNull() as MangaGoogleTranslationException).text)

        val blank = runCatching {
            MangaGoogleTranslator(request = { _, _ -> Result.success("  ") }).translate(listOf("b"), "zh-CN")
        }
        assertTrue(blank.exceptionOrNull() is MangaGoogleTranslationException)
    }
}
