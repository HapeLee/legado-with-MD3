package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.StateFlow

data class BookSourceCheckState(
    val isRunning: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentSourceName: String = "",
    val results: Map<String, String> = emptyMap(),
)

interface BookSourceCheckGateway {
    val state: StateFlow<BookSourceCheckState>
    suspend fun check(sourceIds: Set<String>, keyword: String)
}
