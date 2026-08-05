package io.legado.app.domain.gateway

import io.legado.app.domain.model.readaloud.CloudTtsEngine
import kotlinx.coroutines.flow.Flow

interface CloudTtsEngineGateway {
    fun observeAll(): Flow<List<CloudTtsEngine>>
    suspend fun getAll(): List<CloudTtsEngine>
    suspend fun get(id: String): CloudTtsEngine?
    suspend fun upsert(engine: CloudTtsEngine)
    suspend fun delete(engine: CloudTtsEngine)
}
