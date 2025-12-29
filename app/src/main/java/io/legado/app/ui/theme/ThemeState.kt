package io.legado.app.ui.theme

import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeState {

    private val _themeMode = MutableStateFlow(resolveThemeMode(AppConfig.AppTheme.toString()))
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun updateThemeMode() {
        val newMode = resolveThemeMode(AppConfig.AppTheme.toString())
        if (_themeMode.value != newMode) {
            _themeMode.value = newMode
        }
    }
}

fun resolveThemeMode(value: String): AppThemeMode = when (value) {
    "0" -> AppThemeMode.Dynamic
    "1" -> AppThemeMode.GR
    "2" -> AppThemeMode.Lemon
    "3" -> AppThemeMode.WH
    "4" -> AppThemeMode.Elink
    "5" -> AppThemeMode.Sora
    "6" -> AppThemeMode.August
    "7" -> AppThemeMode.Carlotta
    "8" -> AppThemeMode.Koharu
    "9" -> AppThemeMode.Yuuka
    "10" -> AppThemeMode.Phoebe
    "11" -> AppThemeMode.Mujika
    "12" -> AppThemeMode.CUSTOM
    "13" -> AppThemeMode.Transparent
    else -> AppThemeMode.Dynamic
}