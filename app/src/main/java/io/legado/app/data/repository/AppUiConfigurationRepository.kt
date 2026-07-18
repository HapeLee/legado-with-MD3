package io.legado.app.data.repository

import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.AppUiConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AppUiConfigurationRepository(
    private val appLocaleGateway: AppLocaleGateway,
    private val appShellSettingsGateway: AppShellSettingsGateway,
    private val themeSettingsGateway: ThemeSettingsGateway,
) : AppUiConfigurationGateway {

    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initialConfiguration = AppUiConfiguration(
        language = appLocaleGateway.currentLanguage,
        appShell = appShellSettingsGateway.currentSettings,
        theme = themeSettingsGateway.currentSettings,
    )

    override val currentConfiguration: AppUiConfiguration
        get() = configuration.value

    override val configuration: StateFlow<AppUiConfiguration> = combine(
        appLocaleGateway.language,
        appShellSettingsGateway.settings,
        themeSettingsGateway.settings,
        ::AppUiConfiguration,
    ).stateIn(
        scope = processScope,
        started = SharingStarted.Eagerly,
        initialValue = initialConfiguration,
    )
}
