package io.legado.app.ui.theme

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx

object ThemeState {

    private val _themeMode =
        MutableStateFlow(ThemeResolver.resolveThemeMode(AppConfig.AppTheme.toString()))
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isPureBlack =
        MutableStateFlow(appCtx.getPrefBoolean(PreferKey.pureBlack, false))
    val isPureBlack: StateFlow<Boolean> = _isPureBlack.asStateFlow()

    private val _hasImageBg =
        MutableStateFlow(AppConfig.hasImageBg)
    val hasImageBg: StateFlow<Boolean> = _hasImageBg.asStateFlow()

    private val _paletteStyle =
        MutableStateFlow(AppConfig.paletteStyle)
    val paletteStyle: StateFlow<String?> = _paletteStyle.asStateFlow()

    private val _enableBlur =
        MutableStateFlow(AppConfig.enableBlur)
    val enableBlur: StateFlow<Boolean> = _enableBlur.asStateFlow()

    private val _containerOpacity =
        MutableStateFlow(AppConfig.containerOpacity)
    val containerOpacity: StateFlow<Int> = _containerOpacity

    fun updateThemeMode(newMode: AppThemeMode) {
        if (_themeMode.value != newMode) {
            _themeMode.value = newMode
        }
    }

    fun updatePureBlack(enabled: Boolean) {
        if (_isPureBlack.value != enabled) {
            _isPureBlack.value = enabled
        }
    }

    fun updateImageBg(enabled: Boolean) {
        if (_hasImageBg.value != enabled) {
            _hasImageBg.value = enabled
        }
    }

    fun updatePaletteStyle(style: String?) {
        if (_paletteStyle.value != style) {
            _paletteStyle.value = style
        }
    }

    fun updateEnableBlur(enabled: Boolean) {
        if (_enableBlur.value != enabled) {
            _enableBlur.value = enabled
        }
    }

    fun updateContainerOpacity(opacity: Int) {
        if (_containerOpacity.value != opacity) {
            _containerOpacity.value = opacity
        }
    }

}
