package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ImportBookSettingsGateway
import io.legado.app.domain.gateway.ImportBookSettingsUpdate
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

    override suspend fun update(update: ImportBookSettingsUpdate) {
        val (key, value) = when (update) {
            is ImportBookSettingsUpdate.ImportBookPath -> PreferKey.importBookPath to update.value
            is ImportBookSettingsUpdate.BookImportFileName ->
                PreferKey.bookImportFileName to update.value
            is ImportBookSettingsUpdate.LocalBookImportSort ->
                PreferKey.localBookImportSort to update.value
            is ImportBookSettingsUpdate.RemoteServerId -> PreferKey.remoteServerId to update.value
        }
        AppConfigStore.putAll(mapOf(key to value))
    }
}

internal fun Preferences.toImportBookSettings() = ImportBookSettings(
    importBookPath = compatDsString(PreferKey.importBookPath),
    bookImportFileName = compatDsString(PreferKey.bookImportFileName),
    localBookImportSort = compatDsInt(PreferKey.localBookImportSort) ?: 0,
    remoteServerId = compatDsLong(PreferKey.remoteServerId) ?: 0L,
)
