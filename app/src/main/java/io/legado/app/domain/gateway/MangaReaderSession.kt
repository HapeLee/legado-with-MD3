package io.legado.app.domain.gateway

import io.legado.app.domain.model.manga.MangaSessionCommand
import io.legado.app.domain.model.manga.MangaSessionEvent
import io.legado.app.domain.model.manga.MangaSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MangaReaderSession : AutoCloseable {
    val state: StateFlow<MangaSessionState>
    val events: Flow<MangaSessionEvent>

    suspend fun execute(command: MangaSessionCommand)
}
