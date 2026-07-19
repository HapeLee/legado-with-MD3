package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AppUiConfigurationRepository internal constructor(
    private val appLocaleGateway: AppLocaleGateway,
    preferencesFlow: StateFlow<Preferences> = AppConfigStore.preferencesFlow,
    processScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    initialSystemDarkTheme: Boolean = false,
) : AppUiConfigurationGateway {

    private val systemDarkTheme = MutableStateFlow(initialSystemDarkTheme)

    private val initialConfiguration = preferencesFlow.value.toAppUiConfiguration(
        language = appLocaleGateway.currentLanguage,
        isSystemDarkTheme = initialSystemDarkTheme,
    )

    override val currentConfiguration: AppUiConfiguration
        get() = configuration.value

    override val configuration: StateFlow<AppUiConfiguration> = combine(
        appLocaleGateway.language,
        preferencesFlow,
        systemDarkTheme,
    ) { language, preferences, isSystemDarkTheme ->
        preferences.toAppUiConfiguration(language, isSystemDarkTheme)
    }.stateIn(
        scope = processScope,
        started = SharingStarted.Eagerly,
        initialValue = initialConfiguration,
    )

    override fun synchronizeSystemDarkTheme(isDarkTheme: Boolean) {
        systemDarkTheme.value = isDarkTheme
    }
}

internal fun Preferences.toAppUiConfiguration(
    language: String,
    isSystemDarkTheme: Boolean,
): AppUiConfiguration =
    AppUiConfiguration(
        language = language,
        appShell = toAppShellSettings(),
        theme = toThemeSettings(),
        cover = toCoverSettings(),
        isSystemDarkTheme = isSystemDarkTheme,
    )
