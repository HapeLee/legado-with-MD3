package io.legado.app.help.glide.progress

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressManagerTest {
    @Test
    fun `events retain source options while legacy listeners accept plain URLs`() = runTest {
        val plainUrl = "https://example.org/page.jpg"
        val originalUrl = "$plainUrl,{\"headers\":{\"Referer\":\"https://example.org/\"}}"
        val events = mutableListOf<DownloadProgress>()
        val callbacks = mutableListOf<Int>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            ProgressManager.progress.collect { events.add(it) }
        }
        ProgressManager.addListener(plainUrl) { _, percentage, _, _ ->
            callbacks.add(percentage)
        }
        try {
            ProgressManager.LISTENER.onProgress(originalUrl, 50, 100)
            ProgressManager.LISTENER.onProgress(originalUrl, 100, 100)
            runCurrent()
            assertEquals(listOf(originalUrl, originalUrl), events.map { it.url })
            assertEquals(listOf(50, 100), events.map { it.percentage })
            assertEquals(listOf(1, 50, 100), callbacks)
            assertTrue(events.last().isComplete)
            assertEquals(null, ProgressManager.getProgressListener(plainUrl))
        } finally {
            ProgressManager.removeListener(plainUrl)
            collector.cancel()
        }
    }

    @Test
    fun `requests sharing a URL retain distinct source options in events`() = runTest {
        val urls = listOf(
            "https://example.org/page.jpg,{\"headers\":{\"Authorization\":\"one\"}}",
            "https://example.org/page.jpg,{\"headers\":{\"Authorization\":\"two\"}}",
        )
        val events = mutableListOf<DownloadProgress>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            ProgressManager.progress.collect { events.add(it) }
        }
        try {
            urls.forEach { ProgressManager.LISTENER.onProgress(it, 50, 100) }
            runCurrent()
            assertEquals(urls, events.map { it.url })
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `unknown content length does not emit a percentage or report completion`() = runTest {
        val url = "https://example.org/unknown.jpg"
        val events = mutableListOf<DownloadProgress>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            ProgressManager.progress.collect { events.add(it) }
        }
        try {
            ProgressManager.LISTENER.onProgress(url, 50, -1)
            runCurrent()
            assertTrue(events.isEmpty())
        } finally {
            collector.cancel()
        }
    }
}
