package io.legado.app.ui.theme

import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeState {
    private val _themeMode =
        MutableStateFlow(resolveThemeMode(AppConfig.AppTheme.toString()))

    val themeMode: StateFlow<AppThemeMode> = _themeMode

    fun updateThemeMode() {
        _themeMode.value = resolveThemeMode(AppConfig.AppTheme.toString())
    }
}
