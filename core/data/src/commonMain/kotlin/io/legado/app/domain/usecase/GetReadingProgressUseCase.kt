package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.model.ReadingProgress

class GetReadingProgressUseCase(
    private val readingProgressGateway: ReadingProgressGateway
) {

    val isConfigured: Boolean
        get() = readingProgressGateway.isConfigured

    suspend fun execute(name: String, author: String): ReadingProgress? {
        return readingProgressGateway.getProgress(name, author)
    }
}
