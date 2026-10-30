package io.legado.app.domain.gateway

import io.legado.app.domain.model.manga.MangaCloudOcrConfig
import io.legado.app.domain.model.manga.MangaCloudOcrProvider
import kotlinx.coroutines.flow.Flow

interface MangaCloudOcrGateway {
    fun observe(provider: MangaCloudOcrProvider): Flow<MangaCloudOcrConfig>
    suspend fun get(provider: MangaCloudOcrProvider): MangaCloudOcrConfig
    suspend fun update(provider: MangaCloudOcrProvider, transform: (MangaCloudOcrConfig) -> MangaCloudOcrConfig)
}
