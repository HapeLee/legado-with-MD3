package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.AiHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import java.io.IOException
import javax.net.ssl.SSLException

internal class MangaAiEmptyResponseException(message: String) : IllegalArgumentException(message)

/** Shared by transport retries and response corrections for one logical AI request. */
internal class MangaAiRetryBudget(private val maximum: Int) {
    private var used = 0
    init { require(maximum in 0..10) }

    suspend fun retry(error: Exception, invalidResponse: Boolean = false) {
        currentCoroutineContext().ensureActive()
        val timeout = error is TimeoutCancellationException
        if (error is CancellationException && !timeout) throw error
        val transient = error is MangaAiEmptyResponseException || error is IOException && error !is SSLException ||
            error is AiHttpException && (error.statusCode in setOf(408, 429) || error.statusCode in 500..599)
        if (used >= maximum || !invalidResponse && !transient && !timeout) throw error
        used++
        delay(minOf(8000L, 500L shl (used - 1).coerceAtMost(4)))
    }
}
