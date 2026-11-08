package io.legado.app.domain.gateway

import io.legado.app.domain.model.manga.MangaExportFormat

interface MangaExportGateway {
    /** Exports cached originals only. Returns the created file/directory name. */
    suspend fun export(
        bookUrl: String,
        destination: String,
        format: MangaExportFormat,
        fileNameTemplate: String?,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): String
}
