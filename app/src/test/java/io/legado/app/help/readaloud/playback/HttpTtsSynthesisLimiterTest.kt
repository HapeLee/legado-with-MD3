package io.legado.app.help.readaloud.playback

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HttpTtsSynthesisLimiterTest {
    @Test fun `foreground and background share source quota while other sources proceed`() = runTest {
        val limiter = HttpTtsSynthesisLimiter()
        val playing = limiter.acquire("B", 1)
        val background = async { limiter.acquire("B", 1) }
        val otherSource = async { limiter.acquire("A", 8) }
        runCurrent()
        assertFalse(background.isCompleted)
        assertTrue(otherSource.isCompleted)
        playing.close()
        runCurrent()
        assertTrue(background.isCompleted)
        background.await().close()
        otherSource.await().close()
    }

    @Test fun `cancelled waiter and repeated close do not leak or create permits`() = runTest {
        val limiter = HttpTtsSynthesisLimiter()
        val first = limiter.acquire("B", 1)
        val cancelled = async { limiter.acquire("B", 1) }
        runCurrent()
        cancelled.cancel()
        first.close()
        first.close()
        val next = limiter.acquire("B", 1)
        val waiting = async { limiter.acquire("B", 1) }
        runCurrent()
        assertFalse(waiting.isCompleted)
        next.close()
        waiting.await().close()
    }

    @Test fun `changing limit does not replace quota with requests in flight`() = runTest {
        val limiter = HttpTtsSynthesisLimiter()
        val first = limiter.acquire("B", 2)
        val second = limiter.acquire("B", 2)
        val lowered = async { limiter.acquire("B", 1) }
        runCurrent()
        first.close()
        runCurrent()
        assertFalse(lowered.isCompleted)
        second.close()
        lowered.await().close()
    }
}
