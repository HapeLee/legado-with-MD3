package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.BookExportSettingsGateway
import io.legado.app.domain.model.settings.BookExportSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class BookExportSettingsRepository(
    private val preferences: PreferenceStore,
) : BookExportSettingsGateway {
    override val currentSettings: BookExportSettings
        get() = preferences.currentSnapshot().toBookExportSettings()

    override val settings: Flow<BookExportSettings> = preferences.observeSnapshot()
        .map { it.toBookExportSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (BookExportSettings) -> BookExportSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toBookExportSettings() },
            toPrefMap = BookExportSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun Map<String, PreferenceValue>.toBookExportSettings() = BookExportSettings(
    bookExportFileName = compatString(PreferKey.bookExportFileName),
    episodeExportFileName = compatString(PreferKey.episodeExportFileName).orEmpty(),
    exportCharset = compatString(PreferKey.exportCharset) ?: "UTF-8",
    exportUseReplace = compatBoolean(PreferKey.exportUseReplace) ?: true,
    exportToWebDav = compatBoolean(PreferKey.exportToWebDav) ?: false,
    exportNoChapterName = compatBoolean(PreferKey.exportNoChapterName) ?: false,
    enableCustomExport = compatBoolean(PreferKey.enableCustomExport) ?: false,
    exportType = compatInt(PreferKey.exportType) ?: 0,
    exportPictureFile = compatBoolean(PreferKey.exportPictureFile) ?: false,
    parallelExportBook = compatBoolean(PreferKey.parallelExportBook) ?: false,
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
