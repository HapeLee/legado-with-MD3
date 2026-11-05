package io.legado.app.domain.gateway

import io.legado.app.domain.model.manga.MangaOcrModelState
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import kotlinx.coroutines.flow.StateFlow

/** Optional on-device model sets. Downloads never start implicitly during translation. */
interface MangaOcrModelGateway {
    val state: StateFlow<Map<MangaLocalOcrEngine, MangaOcrModelState>>
    suspend fun refresh()
    suspend fun download(engine: MangaLocalOcrEngine)
    suspend fun delete(engine: MangaLocalOcrEngine)
}
