package io.legado.app.data.repository

import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class CheckSourceSettingsRepository(
    private val preferences: PreferenceStore,
) : CheckSourceSettingsGateway {
    override val currentSettings: CheckSourceSettings
        get() = preferences.currentValues(defaults).toSettings()

    override val settings: Flow<CheckSourceSettings> = preferences.observeValues(defaults)
        .map { values -> values.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(settings: CheckSourceSettings) {
        require(settings.timeoutMillis > 0L)
        require(settings.checkSearch || settings.checkDiscovery)
        preferences.setAllAndAwait(
            mapOf(
                CheckSourcePreferenceKeys.Timeout to PreferenceValue.LongValue(settings.timeoutMillis),
                CheckSourcePreferenceKeys.Search to PreferenceValue.BooleanValue(settings.checkSearch),
                CheckSourcePreferenceKeys.Discovery to PreferenceValue.BooleanValue(settings.checkDiscovery),
                CheckSourcePreferenceKeys.Info to PreferenceValue.BooleanValue(settings.checkInfo),
                CheckSourcePreferenceKeys.Category to PreferenceValue.BooleanValue(settings.checkCategory),
                CheckSourcePreferenceKeys.Content to PreferenceValue.BooleanValue(settings.checkContent),
            ),
        )
    }

    private companion object {
        val defaults = mapOf(
            CheckSourcePreferenceKeys.Timeout to PreferenceValue.LongValue(180_000L),
            CheckSourcePreferenceKeys.Search to PreferenceValue.BooleanValue(true),
            CheckSourcePreferenceKeys.Discovery to PreferenceValue.BooleanValue(true),
            CheckSourcePreferenceKeys.Info to PreferenceValue.BooleanValue(true),
            CheckSourcePreferenceKeys.Category to PreferenceValue.BooleanValue(true),
            CheckSourcePreferenceKeys.Content to PreferenceValue.BooleanValue(true),
        )
    }
}

private fun Map<String, PreferenceValue>.toSettings() = CheckSourceSettings(
    timeoutMillis = (getValue(CheckSourcePreferenceKeys.Timeout) as PreferenceValue.LongValue).value,
    checkSearch = (getValue(CheckSourcePreferenceKeys.Search) as PreferenceValue.BooleanValue).value,
    checkDiscovery = (getValue(CheckSourcePreferenceKeys.Discovery) as PreferenceValue.BooleanValue).value,
    checkInfo = (getValue(CheckSourcePreferenceKeys.Info) as PreferenceValue.BooleanValue).value,
    checkCategory = (getValue(CheckSourcePreferenceKeys.Category) as PreferenceValue.BooleanValue).value,
    checkContent = (getValue(CheckSourcePreferenceKeys.Content) as PreferenceValue.BooleanValue).value,
)
