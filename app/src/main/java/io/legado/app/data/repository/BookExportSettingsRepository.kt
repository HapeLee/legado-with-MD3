package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.BookExportSettingsGateway
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

    override suspend fun update(transform: (BookExportSettings) -> BookExportSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toBookExportSettings,
            toPrefMap = BookExportSettings::toPrefMap,
            transform = transform,
        )
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

internal fun BookExportSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.bookExportFileName to bookExportFileName,
    PreferKey.episodeExportFileName to episodeExportFileName,
    PreferKey.exportCharset to exportCharset,
    PreferKey.exportUseReplace to exportUseReplace,
    PreferKey.exportToWebDav to exportToWebDav,
    PreferKey.exportNoChapterName to exportNoChapterName,
    PreferKey.enableCustomExport to enableCustomExport,
    PreferKey.exportType to exportType,
    PreferKey.exportPictureFile to exportPictureFile,
    PreferKey.parallelExportBook to parallelExportBook,
)
