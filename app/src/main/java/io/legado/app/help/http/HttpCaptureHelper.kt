package io.legado.app.help.http

import io.legado.app.data.entities.BookSource
import kotlin.coroutines.CoroutineContext

/**
 * Placeholder for HTTP request capture functionality.
 * The full implementation requires WebView instrumentation that is not available
 * in this build configuration. Calls will return an error indicating the feature
 * is unavailable.
 */
object HttpCaptureHelper {

    data class Config(
        val url: String,
        val source: BookSource? = null,
        val waitMs: Long = 5_000L,
        val timeoutMs: Long = 30_000L,
        val includeRegex: String? = null,
        val excludeRegex: String? = null,
        val maxRequests: Int = 50,
        val maxBodyChars: Int = 20_000,
        val replayResponse: Boolean = true,
        val replayTimeoutMs: Long = 8_000L,
        val maxReplayRequests: Int = 5,
        val replayOnlyMatched: Boolean = true,
        val js: String? = null,
        val coroutineContext: CoroutineContext? = null
    )

    suspend fun capture(config: Config): String {
        error("HttpCaptureHelper is not available in this build configuration")
    }
}
