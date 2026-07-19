package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.domain.gateway.HomepageSettingsGateway
import io.legado.app.domain.model.settings.HomepageSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val HOMEPAGE_SOURCE_HIDDEN = "homepageSourceHidden"

class HomepageSettingsRepository : HomepageSettingsGateway {
    override val currentSettings: HomepageSettings
        get() = AppConfigStore.preferences.toHomepageSettings()

    override val settings: Flow<HomepageSettings> = AppConfigStore.preferencesFlow
        .map { it.toHomepageSettings() }
        .distinctUntilChanged()

    override suspend fun setHiddenSourceUrlsJson(value: String) {
        AppConfigStore.putAll(mapOf(HOMEPAGE_SOURCE_HIDDEN to value))
    }
}

internal fun Preferences.toHomepageSettings() = HomepageSettings(
    hiddenSourceUrlsJson = compatDsString(HOMEPAGE_SOURCE_HIDDEN).orEmpty(),
)
