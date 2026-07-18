package io.legado.app.domain.gateway

import io.legado.app.domain.model.readaloud.TtsEngineDescriptor
import kotlinx.coroutines.flow.Flow

interface HttpTtsEngineGateway {
    fun observeAll(): Flow<List<TtsEngineDescriptor>>
}
