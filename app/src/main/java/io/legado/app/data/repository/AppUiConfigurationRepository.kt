package io.legado.app.data.repository

import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
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
    private val otherSettingsGateway: OtherSettingsGateway,
    private val backupSettingsGateway: BackupSettingsGateway,
    private val coverSettingsGateway: CoverSettingsGateway,
) : AppUiConfigurationGateway {

    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initialConfiguration = AppUiConfiguration(
        language = appLocaleGateway.currentLanguage,
        appShell = appShellSettingsGateway.currentSettings,
        theme = themeSettingsGateway.currentSettings,
        other = otherSettingsGateway.currentSettings,
        backup = backupSettingsGateway.currentSettings,
        cover = coverSettingsGateway.currentSettings,
    )

    override val currentConfiguration: AppUiConfiguration
        get() = configuration.value

    private val configurationWithoutCover = combine(
        appLocaleGateway.language,
        appShellSettingsGateway.settings,
        themeSettingsGateway.settings,
        otherSettingsGateway.settings,
        backupSettingsGateway.settings,
    ) { language, appShell, theme, other, backup ->
        AppUiConfiguration(
            language = language,
            appShell = appShell,
            theme = theme,
            other = other,
            backup = backup,
            cover = coverSettingsGateway.currentSettings,
        )
    }

    override val configuration: StateFlow<AppUiConfiguration> = configurationWithoutCover
        .combine(coverSettingsGateway.settings) { configuration, cover ->
            configuration.copy(cover = cover)
        }.stateIn(
        scope = processScope,
        started = SharingStarted.Eagerly,
        initialValue = initialConfiguration,
    )
}
