package io.legado.app.model.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CacheDownloadStateStore {

    private val _stateFlow = MutableStateFlow(CacheDownloadState())
    val stateFlow = _stateFlow.asStateFlow()

    val state: CacheDownloadState
        get() = _stateFlow.value

    fun updateBookQueue(
        bookUrl: String,
        waitingCount: Int,
        runningIndices: Set<Int>,
    ) {
        updateBook(bookUrl) { current ->
            current.copy(
                waitingCount = waitingCount,
                runningIndices = runningIndices,
            )
        }
    }

    fun markSuccess(bookUrl: String, chapterIndex: Int) {
        updateBook(bookUrl) { current ->
            val successIndices = current.successIndices + chapterIndex
            current.copy(
                runningIndices = current.runningIndices - chapterIndex,
                failedIndices = current.failedIndices - chapterIndex,
                successIndices = successIndices,
                successCount = successIndices.size,
            )
        }
    }

    fun markFailed(bookUrl: String, chapterIndex: Int) {
        updateBook(bookUrl) { current ->
            current.copy(
                runningIndices = current.runningIndices - chapterIndex,
                failedIndices = current.failedIndices + chapterIndex,
            )
        }
    }

    fun clearFailure(bookUrl: String, chapterIndex: Int) {
        updateBook(bookUrl) { current ->
            current.copy(failedIndices = current.failedIndices - chapterIndex)
        }
    }

    fun removeBook(bookUrl: String) {
        _stateFlow.update { state ->
            state.copy(books = state.books - bookUrl).recalculate()
        }
    }

    fun clear() {
        _stateFlow.value = CacheDownloadState()
    }

    fun bookState(bookUrl: String): CacheBookDownloadState? {
        return state.books[bookUrl]
    }

    private fun updateBook(
        bookUrl: String,
        transform: (CacheBookDownloadState) -> CacheBookDownloadState,
    ) {
        _stateFlow.update { state ->
            val current = state.books[bookUrl] ?: CacheBookDownloadState(bookUrl)
            state.copy(books = state.books + (bookUrl to transform(current))).recalculate()
        }
    }

    private fun CacheDownloadState.recalculate(): CacheDownloadState {
        val totalWaiting = books.values.sumOf { it.waitingCount }
        val totalRunning = books.values.sumOf { it.runningIndices.size }
        val totalFailure = books.values.sumOf { it.failedIndices.size }
        val totalSuccess = books.values.sumOf { it.successCount }
        return copy(
            isRunning = totalWaiting > 0 || totalRunning > 0,
            totalWaiting = totalWaiting,
            totalRunning = totalRunning,
            totalFailure = totalFailure,
            totalSuccess = totalSuccess,
        )
    }
}
