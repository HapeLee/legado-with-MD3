package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.BookExportSettingsGateway
import io.legado.app.domain.gateway.BookExportSettingsUpdate
import io.legado.app.domain.model.settings.BookExportSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class BookExportSettingsRepository : BookExportSettingsGateway {
    override val currentSettings: BookExportSettings
        get() = AppConfigStore.preferences.toBookExportSettings()

    override val settings: Flow<BookExportSettings> = AppConfigStore.preferencesFlow
        .map { it.toBookExportSettings() }
        .distinctUntilChanged()

    override suspend fun update(update: BookExportSettingsUpdate) {
        val (key, value) = when (update) {
            is BookExportSettingsUpdate.BookExportFileName -> PreferKey.bookExportFileName to update.value
            is BookExportSettingsUpdate.EpisodeExportFileName -> PreferKey.episodeExportFileName to update.value
            is BookExportSettingsUpdate.ExportCharset -> PreferKey.exportCharset to update.value
            is BookExportSettingsUpdate.ExportUseReplace -> PreferKey.exportUseReplace to update.value
            is BookExportSettingsUpdate.ExportToWebDav -> PreferKey.exportToWebDav to update.value
            is BookExportSettingsUpdate.ExportNoChapterName -> PreferKey.exportNoChapterName to update.value
            is BookExportSettingsUpdate.EnableCustomExport -> PreferKey.enableCustomExport to update.value
            is BookExportSettingsUpdate.ExportType -> PreferKey.exportType to update.value
            is BookExportSettingsUpdate.ExportPictureFile -> PreferKey.exportPictureFile to update.value
            is BookExportSettingsUpdate.ParallelExportBook -> PreferKey.parallelExportBook to update.value
        }
        AppConfigStore.putAll(mapOf(key to value))
    }
}

internal fun Preferences.toBookExportSettings() = BookExportSettings(
    bookExportFileName = compatDsString(PreferKey.bookExportFileName),
    episodeExportFileName = compatDsString(PreferKey.episodeExportFileName).orEmpty(),
    exportCharset = compatDsString(PreferKey.exportCharset) ?: "UTF-8",
    exportUseReplace = compatDsBoolean(PreferKey.exportUseReplace) ?: true,
    exportToWebDav = compatDsBoolean(PreferKey.exportToWebDav) ?: false,
    exportNoChapterName = compatDsBoolean(PreferKey.exportNoChapterName) ?: false,
    enableCustomExport = compatDsBoolean(PreferKey.enableCustomExport) ?: false,
    exportType = compatDsInt(PreferKey.exportType) ?: 0,
    exportPictureFile = compatDsBoolean(PreferKey.exportPictureFile) ?: false,
    parallelExportBook = compatDsBoolean(PreferKey.parallelExportBook) ?: false,
)
