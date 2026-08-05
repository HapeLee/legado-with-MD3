package io.legado.app.domain.gateway

import io.legado.app.domain.model.ReadingProgress

interface ReadingProgressGateway {
    val isConfigured: Boolean

    suspend fun getProgress(name: String, author: String): ReadingProgress?
    suspend fun uploadProgress(progress: ReadingProgress): Long?
}
