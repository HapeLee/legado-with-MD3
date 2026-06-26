package io.legado.app.data.repository

import io.legado.app.data.dao.AiArtifactDao
import io.legado.app.data.entities.AiArtifact
import io.legado.app.domain.gateway.AiArtifactGateway
import kotlinx.coroutines.flow.Flow

class AiArtifactRepository(
    private val aiArtifactDao: AiArtifactDao
) : AiArtifactGateway {

    override fun observeBookArtifacts(bookUrl: String, taskType: String): Flow<List<AiArtifact>> {
        return aiArtifactDao.observeBookArtifacts(bookUrl, taskType)
    }

    override suspend fun getCachedArtifact(
        bookUrl: String,
        chapterIndex: Int?,
        taskType: String,
        contentHash: String,
        promptHash: String,
        modelProfileId: String
    ): AiArtifact? {
        return aiArtifactDao.getCachedArtifact(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            taskType = taskType,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = modelProfileId
        )
    }

    override suspend fun upsertArtifact(artifact: AiArtifact) {
        aiArtifactDao.upsert(artifact)
    }
}
