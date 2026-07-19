package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.AppUiConfiguration
import kotlinx.coroutines.flow.StateFlow

interface AppUiConfigurationGateway {
    val currentConfiguration: AppUiConfiguration
    val configuration: StateFlow<AppUiConfiguration>

    fun synchronizeSystemDarkTheme(isDarkTheme: Boolean)
}
