package io.legado.app.domain.gateway

import io.legado.app.domain.model.manga.MangaInpaintEngine
import io.legado.app.domain.model.manga.MangaInpaintModelState
import kotlinx.coroutines.flow.StateFlow

/** Explicit model management; selecting an engine never starts a download. */
interface MangaInpaintModelGateway {
    val state: StateFlow<Map<MangaInpaintEngine, MangaInpaintModelState>>
    suspend fun refresh()
    suspend fun download(engine: MangaInpaintEngine)
    suspend fun delete(engine: MangaInpaintEngine)
}
