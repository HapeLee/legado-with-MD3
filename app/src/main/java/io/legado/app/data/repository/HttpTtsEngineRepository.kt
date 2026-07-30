package io.legado.app.data.repository

import io.legado.app.data.dao.HttpTTSDao
import io.legado.app.domain.gateway.HttpTtsEngineGateway
import io.legado.app.domain.model.readaloud.TtsEngineDescriptor
import io.legado.app.domain.model.readaloud.TtsEngineKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HttpTtsEngineRepository(
    private val httpTtsDao: HttpTTSDao,
) : HttpTtsEngineGateway {

    override fun observeAll(): Flow<List<TtsEngineDescriptor>> = httpTtsDao.flowAll().map { sources ->
        sources.map { source ->
            TtsEngineDescriptor(
                id = "http:${source.id}",
                kind = TtsEngineKind.Http,
                sourceId = source.id.toString(),
                displayName = source.name,
                providerName = "HTTP TTS",
                supportsVoiceDiscovery = false,
                loginUrl = source.loginUrl.orEmpty(),
            )
        }
    }
}
