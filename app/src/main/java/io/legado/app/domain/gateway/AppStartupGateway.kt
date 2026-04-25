package io.legado.app.domain.gateway

interface AppStartupGateway {
    suspend fun deleteNotShelfBooks()
    suspend fun ensureDefaultHttpTts()
}
