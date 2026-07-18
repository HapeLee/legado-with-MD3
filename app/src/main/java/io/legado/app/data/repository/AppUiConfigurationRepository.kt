package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AppUiConfigurationRepository internal constructor(
    private val appLocaleGateway: AppLocaleGateway,
    preferencesFlow: StateFlow<Preferences> = AppConfigStore.preferencesFlow,
    processScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AppUiConfigurationGateway {

    private val initialConfiguration = preferencesFlow.value.toAppUiConfiguration(
        language = appLocaleGateway.currentLanguage,
    )

    override val currentConfiguration: AppUiConfiguration
        get() = configuration.value

    override val configuration: StateFlow<AppUiConfiguration> = combine(
        appLocaleGateway.language,
        preferencesFlow,
    ) { language, preferences ->
        preferences.toAppUiConfiguration(language)
    }.stateIn(
        scope = processScope,
        started = SharingStarted.Eagerly,
        initialValue = initialConfiguration,
    )
}

internal fun Preferences.toAppUiConfiguration(language: String): AppUiConfiguration =
    AppUiConfiguration(
        language = language,
        appShell = toAppShellSettings(),
        theme = toThemeSettings(),
        cover = toCoverSettings(),
    )
