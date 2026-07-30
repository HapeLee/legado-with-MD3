package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ImportBookSettingsGateway
import io.legado.app.domain.model.settings.ImportBookSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsLong
import io.legado.app.help.config.compatDsString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ImportBookSettingsRepository : ImportBookSettingsGateway {
    override val currentSettings: ImportBookSettings
        get() = AppConfigStore.preferences.toImportBookSettings()

    override val settings: Flow<ImportBookSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toImportBookSettings)
        .distinctUntilChanged()

    override suspend fun update(transform: (ImportBookSettings) -> ImportBookSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toImportBookSettings,
            toPrefMap = ImportBookSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun Preferences.toImportBookSettings() = ImportBookSettings(
    importBookPath = compatDsString(PreferKey.importBookPath),
    bookImportFileName = compatDsString(PreferKey.bookImportFileName),
    localBookImportSort = compatDsInt(PreferKey.localBookImportSort) ?: 0,
    remoteServerId = compatDsLong(PreferKey.remoteServerId) ?: 0L,
)

internal fun ImportBookSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.importBookPath to importBookPath,
    PreferKey.bookImportFileName to bookImportFileName,
    PreferKey.localBookImportSort to localBookImportSort,
    PreferKey.remoteServerId to remoteServerId,
)
