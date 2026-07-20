package io.legado.app.ui.config.themeConfig

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AppShellBooleanSetting
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.AppShellSettingsUpdate
import io.legado.app.domain.gateway.AppShellStringSetting
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ThemeBooleanSetting
import io.legado.app.domain.gateway.ThemeIntSetting
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsUpdate
import io.legado.app.domain.gateway.ThemeStringSetting
import io.legado.app.domain.gateway.LabSettingsGateway
import io.legado.app.ui.main.MainDestination
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import io.legado.app.utils.openInputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class ThemeConfigViewModel(
    private val appShellSettingsGateway: AppShellSettingsGateway,
    private val readSettingsGateway: ReadSettingsGateway,
    private val themeSettingsGateway: ThemeSettingsGateway,
    private val labSettingsGateway: LabSettingsGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ThemeConfigUiState(
            appShell = appShellSettingsGateway.currentSettings,
            theme = themeSettingsGateway.currentSettings,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ThemeConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            appShellSettingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(appShell = settings) }
            }
        }
        viewModelScope.launch {
            themeSettingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(theme = settings) }
            }
        }
        viewModelScope.launch {
            readSettingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(fontFolder = settings.fontFolder) }
            }
        }
        viewModelScope.launch {
            labSettingsGateway.settings.collect { settings ->
                _uiState.update {
                    it.copy(showEInkTheme = settings.enabled && settings.eInkDisplay)
                }
            }
        }
    }

    fun onIntent(intent: ThemeConfigIntent) {
        when (intent) {
            is ThemeConfigIntent.UpdateAppShell -> updateAppShell(intent.update)
            is ThemeConfigIntent.UpdateTheme -> updateTheme(intent.update)
            is ThemeConfigIntent.ShowSheet ->
                _uiState.update { it.copy(activeSheet = intent.sheet) }
            ThemeConfigIntent.DismissSheet ->
                _uiState.update { it.copy(activeSheet = null) }
            is ThemeConfigIntent.SelectTheme -> selectTheme(intent.value)
            is ThemeConfigIntent.SetMiuixMonet -> setMiuixMonet(intent.enabled)
            is ThemeConfigIntent.SetDynamicColors -> setDynamicColors(intent.enabled)
            is ThemeConfigIntent.SetBlurEnabled -> setBlurEnabled(intent.enabled)
            is ThemeConfigIntent.SetMainDestinationVisible -> setMainDestinationVisible(intent)
            is ThemeConfigIntent.SetMainNavigationOrder -> updateAppShell(
                AppShellSettingsUpdate.MainNavigationOrder(intent.routes)
            )
            is ThemeConfigIntent.SetDefaultHomePage -> updateAppShell(
                AppShellSettingsUpdate.StringValue(
                    AppShellStringSetting.DefaultHomePage,
                    intent.route,
                )
            )
            is ThemeConfigIntent.SelectLauncherIcon -> selectLauncherIcon(intent.value)
            is ThemeConfigIntent.SelectNavigationIcon -> selectNavigationIcon(intent)
            is ThemeConfigIntent.RequestNavigationIcon -> _effects.tryEmit(
                ThemeConfigEffect.OpenNavigationIcon(intent.destination)
            )
            is ThemeConfigIntent.RequestBackgroundImage -> _effects.tryEmit(
                ThemeConfigEffect.OpenBackgroundImage(intent.dark)
            )
            is ThemeConfigIntent.SelectBackground -> setBackground(intent.uri, intent.dark)
            is ThemeConfigIntent.RemoveBackground -> removeBackground(intent.dark)
            is ThemeConfigIntent.SelectAppFont -> setAppFont(intent.file)
            ThemeConfigIntent.ClearAppFont -> updateTheme(ThemeSettingsUpdate.AppFontPath(null))
            is ThemeConfigIntent.SetFontFolder -> viewModelScope.launch {
                readSettingsGateway.update { it.copy(fontFolder = intent.path) }
            }
            ThemeConfigIntent.RequestFontFolder -> _effects.tryEmit(ThemeConfigEffect.OpenFontFolder)
            is ThemeConfigIntent.RequestTimePicker -> _effects.tryEmit(
                ThemeConfigEffect.OpenTimePicker(intent.field, intent.currentValue)
            )
            is ThemeConfigIntent.SetTime -> setTime(intent.field, intent.value)
            ThemeConfigIntent.DismissRefactorTip -> updateTheme(
                ThemeSettingsUpdate.BooleanValue(ThemeBooleanSetting.ShowRefactorTip, false)
            )
        }
    }

    private fun updateAppShell(update: AppShellSettingsUpdate) {
        viewModelScope.launch {
            appShellSettingsGateway.update(update)
            when (update) {
                is AppShellSettingsUpdate.ThemeMode ->
                    _effects.tryEmit(ThemeConfigEffect.ApplyDayNight)
                is AppShellSettingsUpdate.BooleanValue -> if (
                    update.setting == AppShellBooleanSetting.ShowStatusBar
                ) {
                    _effects.tryEmit(ThemeConfigEffect.NotifyMain)
                }
                else -> Unit
            }
        }
    }

    private fun updateTheme(update: ThemeSettingsUpdate) {
        viewModelScope.launch { themeSettingsGateway.update(update) }
    }

    private fun selectTheme(value: String) {
        val theme = _uiState.value.theme
        if (value == "13" &&
            (theme.backgroundImageLight.isNullOrEmpty() || theme.backgroundImageDark.isNullOrEmpty())
        ) {
            _effects.tryEmit(ThemeConfigEffect.ShowToast(R.string.transparent_theme_alarm))
            return
        }
        viewModelScope.launch {
            val updates = buildList {
                if (value == "13") {
                    add(ThemeSettingsUpdate.IntValue(ThemeIntSetting.ContainerOpacity, 0))
                }
                add(ThemeSettingsUpdate.AppTheme(value))
            }
            themeSettingsGateway.updateAll(updates)
        }
    }

    private fun setMiuixMonet(enabled: Boolean) {
        viewModelScope.launch {
            val currentTheme = _uiState.value.theme.appTheme
            themeSettingsGateway.updateAll(
                buildList {
                    add(
                        ThemeSettingsUpdate.BooleanValue(
                            ThemeBooleanSetting.UseMiuixMonet,
                            enabled,
                        )
                    )
                    if (enabled && currentTheme != "0" && currentTheme != "12") {
                        add(ThemeSettingsUpdate.AppTheme("0"))
                    }
                }
            )
        }
    }

    private fun setDynamicColors(enabled: Boolean) {
        val value = if (enabled) "0" else "12"
        if (value != _uiState.value.theme.appTheme) selectTheme(value)
    }

    private fun setBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themeSettingsGateway.updateAll(
                buildList {
                    add(
                        ThemeSettingsUpdate.BooleanValue(
                            ThemeBooleanSetting.EnableBlur,
                            enabled,
                        )
                    )
                    if (!enabled) {
                        add(
                            ThemeSettingsUpdate.BooleanValue(
                                ThemeBooleanSetting.EnableProgressiveBlur,
                                false,
                            )
                        )
                    }
                }
            )
        }
    }

    private fun setMainDestinationVisible(intent: ThemeConfigIntent.SetMainDestinationVisible) {
        val setting = when (intent.route) {
            MainDestination.Home.route -> AppShellBooleanSetting.ShowHome
            MainDestination.Explore.route -> AppShellBooleanSetting.ShowDiscovery
            MainDestination.Rss.route -> AppShellBooleanSetting.ShowRss
            else -> return
        }
        viewModelScope.launch {
            appShellSettingsGateway.updateAll(
                buildList {
                    add(AppShellSettingsUpdate.BooleanValue(setting, intent.visible))
                    if (!intent.visible && _uiState.value.appShell.defaultHomePage == intent.route) {
                        add(
                            AppShellSettingsUpdate.StringValue(
                                AppShellStringSetting.DefaultHomePage,
                                MainDestination.Bookshelf.route,
                            )
                        )
                    }
                }
            )
        }
    }

    private fun selectLauncherIcon(value: String) {
        viewModelScope.launch {
            appShellSettingsGateway.update(
                AppShellSettingsUpdate.StringValue(AppShellStringSetting.LauncherIcon, value)
            )
            _effects.tryEmit(ThemeConfigEffect.ChangeLauncherIcon(value))
        }
    }

    private fun selectNavigationIcon(intent: ThemeConfigIntent.SelectNavigationIcon) {
        val setting = when (intent.destination) {
            MainDestination.Home.route -> AppShellStringSetting.NavIconHome
            MainDestination.Bookshelf.route -> AppShellStringSetting.NavIconBookshelf
            MainDestination.Explore.route -> AppShellStringSetting.NavIconExplore
            MainDestination.Rss.route -> AppShellStringSetting.NavIconRss
            MainDestination.My.route -> AppShellStringSetting.NavIconMy
            else -> return
        }
        updateAppShell(AppShellSettingsUpdate.StringValue(setting, intent.path))
    }

    private fun setTime(field: ThemeTimeField, value: String) {
        val setting = when (field) {
            ThemeTimeField.EyeProtectionStart -> ThemeStringSetting.EyeProtectionStartTime
            ThemeTimeField.EyeProtectionEnd -> ThemeStringSetting.EyeProtectionEndTime
        }
        updateTheme(ThemeSettingsUpdate.StringValue(setting, value))
    }

    private fun setBackground(uriString: String, dark: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val fileDoc = FileDoc.fromUri(uri, false)
                val suffix = fileDoc.name.substringAfterLast(".", "jpg")
                val md5 = uri.inputStream(appCtx).getOrThrow().use(MD5Utils::md5Encode)
                val preferenceKey = if (dark) PreferKey.bgImageN else PreferKey.bgImage
                val folder = File(appCtx.externalFiles, preferenceKey)
                val file = File(folder, "$md5.$suffix")
                if (!file.exists()) {
                    FileUtils.createFileIfNotExist(file.absolutePath)
                    uri.inputStream(appCtx).getOrThrow().use { input ->
                        FileOutputStream(file).use(input::copyTo)
                    }
                }
                updateBackgroundPath(dark, file.absolutePath)
            }.onFailure(Throwable::printStackTrace)
        }
    }

    private fun removeBackground(dark: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { updateBackgroundPath(dark, null) }
    }

    private suspend fun updateBackgroundPath(dark: Boolean, newPath: String?) {
        val oldPath = if (dark) {
            _uiState.value.theme.backgroundImageDark
        } else {
            _uiState.value.theme.backgroundImageLight
        }
        if (oldPath != newPath && oldPath != null) {
            File(oldPath)
                .takeIf { it.absolutePath.contains(appCtx.externalFiles.absolutePath) }
                ?.delete()
        }
        themeSettingsGateway.update(
            ThemeSettingsUpdate.StringValue(
                if (dark) ThemeStringSetting.BackgroundImageDark
                else ThemeStringSetting.BackgroundImageLight,
                newPath,
            )
        )
    }

    private fun setAppFont(fileDoc: FileDoc) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val extension = fileDoc.name.substringAfterLast('.', "ttf")
                    .lowercase()
                    .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                    ?: "ttf"
                val fontDir = File(appCtx.filesDir, "fonts").apply { mkdirs() }
                val target = File(fontDir, "app_font.$extension")
                val temp = File(fontDir, "app_font.$extension.tmp")
                fileDoc.openInputStream().getOrThrow().use { input ->
                    FileOutputStream(temp).use(input::copyTo)
                }
                if (target.exists() && !target.delete()) error("Unable to replace app font")
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                themeSettingsGateway.update(ThemeSettingsUpdate.AppFontPath(target.absolutePath))
            }.onFailure(Throwable::printStackTrace)
        }
    }
}
