package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.StateFlow

enum class BookSourceCheckStatus(val isTerminal: Boolean) {
    Pending(false),
    Running(false),
    Succeeded(true),
    Failed(true),
    Cancelled(true),
}

enum class BookSourceCheckFailure {
    CheckFailed,
    SourceMissing,
    SaveFailed,
    Incomplete,
}

data class BookSourceCheckResult(
    val status: BookSourceCheckStatus,
    val failure: BookSourceCheckFailure? = null,
    val detail: String? = null,
)

data class BookSourceCheckState(
    val isRunning: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentSourceName: String = "",
    val results: Map<String, BookSourceCheckResult> = emptyMap(),
) {
    val succeededCount: Int
        get() = results.values.count { it.status == BookSourceCheckStatus.Succeeded }

    val failedCount: Int
        get() = results.values.count { it.status == BookSourceCheckStatus.Failed }

    val cancelledCount: Int
        get() = results.values.count { it.status == BookSourceCheckStatus.Cancelled }
}

interface BookSourceCheckGateway {
    val state: StateFlow<BookSourceCheckState>
    suspend fun check(sourceIds: Set<String>, keyword: String)
}
