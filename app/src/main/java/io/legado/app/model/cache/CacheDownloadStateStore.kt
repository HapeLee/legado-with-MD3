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
        pausedIndices: Set<Int> = emptySet(),
    ) {
        updateBook(bookUrl) { current ->
            current.copy(
                waitingCount = waitingCount,
                runningIndices = runningIndices,
                pausedIndices = pausedIndices,
                failureMessage = if (waitingCount > 0 || runningIndices.isNotEmpty() || pausedIndices.isNotEmpty()) {
                    null
                } else {
                    current.failureMessage
                },
            )
        }
    }

    fun markSuccess(bookUrl: String, chapterIndex: Int) {
        updateBook(bookUrl) { current ->
            current.copy(
                runningIndices = current.runningIndices - chapterIndex,
                pausedIndices = current.pausedIndices - chapterIndex,
                failedIndices = current.failedIndices - chapterIndex,
                successCount = current.successCount + 1,
                failureMessage = null,
                chapterProgress = current.chapterProgress - chapterIndex,
            )
        }
    }

    fun markFailed(bookUrl: String, chapterIndex: Int) {
        updateBook(bookUrl) { current ->
            current.copy(
                runningIndices = current.runningIndices - chapterIndex,
                pausedIndices = current.pausedIndices - chapterIndex,
                failedIndices = current.failedIndices + chapterIndex,
                chapterProgress = current.chapterProgress - chapterIndex,
            )
        }
    }

    fun updateChapterProgress(
        bookUrl: String,
        chapterIndex: Int,
        progress: CacheChapterProgress,
    ) {
        updateBook(bookUrl) { current ->
            current.copy(chapterProgress = current.chapterProgress + (chapterIndex to progress))
        }
    }

    fun clearChapterProgress(bookUrl: String, chapterIndex: Int) {
        updateBook(bookUrl) { current ->
            current.copy(chapterProgress = current.chapterProgress - chapterIndex)
        }
    }

    fun markBookFailed(bookUrl: String, message: String) {
        updateBook(bookUrl) { current ->
            current.copy(
                waitingCount = 0,
                runningIndices = emptySet(),
                pausedIndices = emptySet(),
                failedIndices = emptySet(),
                failureMessage = message,
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

    fun clearRuntimeState() {
        _stateFlow.update { state ->
            val failureBooks = state.books
                .mapValues { (_, bookState) ->
                    bookState.copy(
                        waitingCount = 0,
                        runningIndices = emptySet(),
                        successCount = 0,
                        chapterProgress = emptyMap(),
                    )
                }
                .filterValues { bookState ->
                    bookState.pausedIndices.isNotEmpty() ||
                            bookState.failedIndices.isNotEmpty() ||
                            bookState.failureMessage != null
                }
            state.copy(books = failureBooks).recalculate()
        }
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
        var totalWaiting = 0
        var totalRunning = 0
        var totalPaused = 0
        var totalFailure = 0
        var totalSuccess = 0

        books.values.forEach { bookState ->
            totalWaiting += bookState.waitingCount
            totalRunning += bookState.runningIndices.size
            totalPaused += bookState.pausedIndices.size
            totalFailure += bookState.failedIndices.size
            if (bookState.failureMessage != null) totalFailure++
            totalSuccess += bookState.successCount
        }

        return copy(
            isRunning = totalWaiting > totalPaused || totalRunning > 0,
            totalWaiting = totalWaiting,
            totalRunning = totalRunning,
            totalPaused = totalPaused,
            totalFailure = totalFailure,
            totalSuccess = totalSuccess,
        )
    }
}
