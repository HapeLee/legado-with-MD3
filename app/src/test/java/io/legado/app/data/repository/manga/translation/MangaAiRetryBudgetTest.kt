package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.AiHttpException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MangaAiRetryBudgetTest {
    @Test fun `temporary failures consume the shared budget with bounded backoff`() = runTest {
        val budget = MangaAiRetryBudget(3)
        budget.retry(IOException())
        budget.retry(IllegalArgumentException(), invalidResponse = true)
        budget.retry(AiHttpException(429, "rate limited"))
        assertEquals(3500L, testScheduler.currentTime)
        val error = IOException("exhausted")
        assertSame(error, runCatching { budget.retry(error) }.exceptionOrNull())
    }
    @Test fun `auth config and user cancellation fail immediately`() = runTest {
        for (error in listOf(AiHttpException(401, "auth"), AiHttpException(400, "config"), IllegalStateException("missing model"), CancellationException("cancel"))) {
            assertSame(error, runCatching { MangaAiRetryBudget(3).retry(error) }.exceptionOrNull())
        }
        assertEquals(0L, testScheduler.currentTime)
    }
    @Test fun `zero retries and cancellation during backoff send no further attempts`() = runTest {
        val error = IOException()
        assertSame(error, runCatching { MangaAiRetryBudget(0).retry(error) }.exceptionOrNull())
        val job = launch { MangaAiRetryBudget(3).retry(error); fail("Must be cancelled") }
        testScheduler.runCurrent()
        job.cancelAndJoin()
    }
}
