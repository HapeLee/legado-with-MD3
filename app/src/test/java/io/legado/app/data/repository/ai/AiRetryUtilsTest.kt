package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiHttpException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AiRetryUtilsTest {

    @Test
    fun retriesTypedRetryableStatus() = runBlocking {
        var attempts = 0

        runCatching {
            retryWithBackoff<Unit>(
                maxAttempts = 2,
                baseDelayMs = 0,
                maxDelayMs = 0,
                retryableStatusCodes = setOf(429)
            ) {
                attempts++
                throw AiHttpException(429, "HTTP 429: rate limited")
            }
        }

        assertEquals(2, attempts)
    }

    @Test
    fun doesNotRetryPermanentStatusEvenWhenBodyLooksRetryable() = runBlocking {
        var attempts = 0

        runCatching {
            retryWithBackoff<Unit>(
                maxAttempts = 2,
                baseDelayMs = 0,
                maxDelayMs = 0,
                retryableStatusCodes = setOf(429)
            ) {
                attempts++
                throw AiHttpException(400, """HTTP 400: {"detail":"HTTP 429: upstream throttled"}""")
            }
        }

        assertEquals(1, attempts)
    }

    @Test
    fun retriesLegacyStringStatusWithoutTypedException() = runBlocking {
        var attempts = 0

        runCatching {
            retryWithBackoff<Unit>(
                maxAttempts = 2,
                baseDelayMs = 0,
                maxDelayMs = 0,
                retryableStatusCodes = setOf(503)
            ) {
                attempts++
                throw Exception("HTTP 503: service unavailable")
            }
        }

        assertEquals(2, attempts)
    }
}
