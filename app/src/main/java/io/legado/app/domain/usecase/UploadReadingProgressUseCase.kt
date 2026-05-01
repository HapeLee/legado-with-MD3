package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.model.ReadingProgress

class UploadReadingProgressUseCase(
    private val readingProgressGateway: ReadingProgressGateway
) {

    suspend fun execute(progress: ReadingProgress): Long? {
        return readingProgressGateway.uploadProgress(progress)
    }
}
