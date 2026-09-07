package io.legado.app.data.repository

import io.legado.app.domain.gateway.HomepageSettingsGateway
import io.legado.app.domain.model.settings.HomepageSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val HOMEPAGE_SOURCE_HIDDEN = "homepageSourceHidden"

class HomepageSettingsRepository(
    private val preferences: PreferenceStore,
) : HomepageSettingsGateway {
    override val currentSettings: HomepageSettings
        get() = preferences.currentSnapshot().toHomepageSettings()

    override val settings: Flow<HomepageSettings> = preferences.observeSnapshot()
        .map { it.toHomepageSettings() }
        .distinctUntilChanged()

    override suspend fun setHiddenSourceUrlsJson(value: String) {
        preferences.setAllAndAwait(
            mapOf(HOMEPAGE_SOURCE_HIDDEN to PreferenceValue.StringValue(value)),
        )
    }
}

internal fun Map<String, PreferenceValue>.toHomepageSettings() = HomepageSettings(
    hiddenSourceUrlsJson = compatString(HOMEPAGE_SOURCE_HIDDEN).orEmpty(),
)
