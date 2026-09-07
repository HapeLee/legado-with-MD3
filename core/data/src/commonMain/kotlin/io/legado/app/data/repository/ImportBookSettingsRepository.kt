package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ImportBookSettingsGateway
import io.legado.app.domain.model.settings.ImportBookSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ImportBookSettingsRepository(
    private val preferences: PreferenceStore,
) : ImportBookSettingsGateway {
    override val currentSettings: ImportBookSettings
        get() = preferences.currentSnapshot().toImportBookSettings()

    override val settings: Flow<ImportBookSettings> = preferences.observeSnapshot()
        .map { it.toImportBookSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (ImportBookSettings) -> ImportBookSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toImportBookSettings() },
            toPrefMap = ImportBookSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun Map<String, PreferenceValue>.toImportBookSettings() = ImportBookSettings(
    importBookPath = compatString(PreferKey.importBookPath),
    bookImportFileName = compatString(PreferKey.bookImportFileName),
    localBookImportSort = compatInt(PreferKey.localBookImportSort) ?: 0,
    remoteServerId = compatLong(PreferKey.remoteServerId) ?: 0L,
)

internal fun ImportBookSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.importBookPath to importBookPath,
    PreferKey.bookImportFileName to bookImportFileName,
    PreferKey.localBookImportSort to localBookImportSort,
    PreferKey.remoteServerId to remoteServerId,
)
