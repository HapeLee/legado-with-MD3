package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiArtifact
import kotlinx.coroutines.flow.Flow

interface AiArtifactGateway {
    fun observeBookArtifacts(bookUrl: String, taskType: String): Flow<List<AiArtifact>>
    suspend fun getCachedArtifact(
        bookUrl: String,
        chapterIndex: Int?,
        taskType: String,
        contentHash: String,
        promptHash: String,
        modelProfileId: String
    ): AiArtifact?
    suspend fun upsertArtifact(artifact: AiArtifact)
}
