package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.BookExportSettings
import kotlinx.coroutines.flow.Flow

interface BookExportSettingsGateway {
    val currentSettings: BookExportSettings
    val settings: Flow<BookExportSettings>
    suspend fun update(update: BookExportSettingsUpdate)
}

sealed interface BookExportSettingsUpdate {
    data class BookExportFileName(val value: String?) : BookExportSettingsUpdate
    data class EpisodeExportFileName(val value: String) : BookExportSettingsUpdate
    data class ExportCharset(val value: String) : BookExportSettingsUpdate
    data class ExportUseReplace(val value: Boolean) : BookExportSettingsUpdate
    data class ExportToWebDav(val value: Boolean) : BookExportSettingsUpdate
    data class ExportNoChapterName(val value: Boolean) : BookExportSettingsUpdate
    data class EnableCustomExport(val value: Boolean) : BookExportSettingsUpdate
    data class ExportType(val value: Int) : BookExportSettingsUpdate
    data class ExportPictureFile(val value: Boolean) : BookExportSettingsUpdate
    data class ParallelExportBook(val value: Boolean) : BookExportSettingsUpdate
}
