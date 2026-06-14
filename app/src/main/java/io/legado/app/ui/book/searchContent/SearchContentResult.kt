package io.legado.app.ui.book.searchContent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton bus for passing search results from [SearchContentScreen] back to the reader.
 *
 * Uses [MutableSharedFlow] with `replay = 1` so the reader entry receives the result
 * even if it subscribes after the emission (the reader entry is not composed while
 * the search route is on top of the NavDisplay back stack).
 */
object SearchContentResult {

    data class Result(
        val bookUrl: String,
        val searchResults: List<SearchResult>,
        val index: Int,
        val query: String,
    )

    private val _results = MutableSharedFlow<Result>(replay = 1)
    val results = _results.asSharedFlow()

    fun emitResult(result: Result) {
        _results.tryEmit(result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun resetReplayCache() {
        _results.resetReplayCache()
    }
}
