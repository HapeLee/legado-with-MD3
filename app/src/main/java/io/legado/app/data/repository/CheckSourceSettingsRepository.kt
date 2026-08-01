package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class CheckSourceSettingsRepository : CheckSourceSettingsGateway {

    override val currentSettings: CheckSourceSettings
        get() = AppConfigStore.preferences.toSettings()

    override val settings = AppConfigStore.preferencesFlow
        .map(Preferences::toSettings)
        .distinctUntilChanged()

    override suspend fun update(settings: CheckSourceSettings) {
        require(settings.timeoutMillis > 0L)
        require(settings.checkSearch || settings.checkDiscovery)
        AppConfigStore.putAllAndAwait(
            mapOf(
                LocalPreferencesKeys.CHECK_SOURCE_TIMEOUT.name to settings.timeoutMillis,
                LocalPreferencesKeys.CHECK_SOURCE_SEARCH.name to settings.checkSearch,
                LocalPreferencesKeys.CHECK_SOURCE_DISCOVERY.name to settings.checkDiscovery,
                LocalPreferencesKeys.CHECK_SOURCE_INFO.name to settings.checkInfo,
                LocalPreferencesKeys.CHECK_SOURCE_CATEGORY.name to settings.checkCategory,
                LocalPreferencesKeys.CHECK_SOURCE_CONTENT.name to settings.checkContent,
            )
        )
    }
}

private fun Preferences.toSettings() = CheckSourceSettings(
    timeoutMillis = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_TIMEOUT, 180_000L),
    checkSearch = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_SEARCH, true),
    checkDiscovery = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_DISCOVERY, true),
    checkInfo = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_INFO, true),
    checkCategory = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_CATEGORY, true),
    checkContent = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_CONTENT, true),
)
