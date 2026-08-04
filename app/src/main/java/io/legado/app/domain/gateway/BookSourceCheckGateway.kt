package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.StateFlow

enum class BookSourceCheckStatus {
    Pending,
    Running,
    Succeeded,
    Failed,
    Cancelled,
}

data class BookSourceCheckResult(
    val status: BookSourceCheckStatus,
    val message: String,
)

data class BookSourceCheckState(
    val isRunning: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentSourceName: String = "",
    val results: Map<String, BookSourceCheckResult> = emptyMap(),
)

interface BookSourceCheckGateway {
    val state: StateFlow<BookSourceCheckState>
    suspend fun check(sourceIds: Set<String>, keyword: String)
}
