package io.legado.app.ui.config.themeConfig

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.LabSettingsGateway
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.main.MainDestination
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import io.legado.app.utils.openInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
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

    private var fontJob: Job? = null

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
            is ThemeConfigIntent.UpdateTheme -> updateTheme(intent.transform)
            is ThemeConfigIntent.ShowSheet ->
                _uiState.update { it.copy(activeSheet = intent.sheet) }
            ThemeConfigIntent.DismissSheet ->
                _uiState.update { it.copy(activeSheet = null) }
            is ThemeConfigIntent.ShowDialog ->
                _uiState.update { it.copy(activeDialog = intent.dialog) }
            ThemeConfigIntent.DismissDialog ->
                _uiState.update { it.copy(activeDialog = null) }
            ThemeConfigIntent.ResetDefaults -> resetDefaults()
            is ThemeConfigIntent.SelectTheme -> selectTheme(intent.value)
            is ThemeConfigIntent.SetThemeMode -> updateAppShell(
                transform = { it.copy(themeMode = intent.value) },
                effect = ThemeConfigEffect.ApplyDayNight,
            )
            is ThemeConfigIntent.SetComposeEngine -> updateAppShell {
                it.copy(composeEngine = intent.value)
            }
            is ThemeConfigIntent.SetPredictiveBackEnabled -> updateAppShell {
                it.copy(predictiveBackEnabled = intent.enabled)
            }
            is ThemeConfigIntent.SetFontScale -> updateAppShell {
                it.copy(fontScale = intent.value)
            }
            is ThemeConfigIntent.SetShowStatusBar -> updateAppShell(
                transform = { it.copy(showStatusBar = intent.visible) },
                effect = ThemeConfigEffect.NotifyMain,
            )
            is ThemeConfigIntent.SetSwipeAnimation -> updateAppShell {
                it.copy(swipeAnimation = intent.enabled)
            }
            is ThemeConfigIntent.SetShowBottomView -> updateAppShell {
                it.copy(showBottomView = intent.visible)
            }
            is ThemeConfigIntent.SetUseFloatingBottomBar -> updateAppShell {
                it.copy(useFloatingBottomBar = intent.enabled)
            }
            is ThemeConfigIntent.SetUseFloatingBottomBarLiquidGlass -> updateAppShell {
                it.copy(useFloatingBottomBarLiquidGlass = intent.enabled)
            }
            is ThemeConfigIntent.SetTabletInterface -> updateAppShell {
                it.copy(tabletInterface = intent.value)
            }
            is ThemeConfigIntent.SetLabelVisibilityMode -> updateAppShell {
                it.copy(labelVisibilityMode = intent.value)
            }
            is ThemeConfigIntent.SetMiuixMonet -> setMiuixMonet(intent.enabled)
            is ThemeConfigIntent.SetDynamicColors -> setDynamicColors(intent.enabled)
            is ThemeConfigIntent.SetBlurEnabled -> setBlurEnabled(intent.enabled)
            is ThemeConfigIntent.SetMainDestinationVisible -> setMainDestinationVisible(intent)
            is ThemeConfigIntent.SetMainNavigationOrder -> updateAppShell {
                it.copy(mainNavigationOrder = intent.routes)
            }
            is ThemeConfigIntent.SetDefaultHomePage -> updateAppShell {
                it.copy(defaultHomePage = intent.route)
            }
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
            ThemeConfigIntent.ClearAppFont -> clearAppFont()
            is ThemeConfigIntent.SetFontFolder -> viewModelScope.launch {
                readSettingsGateway.update { it.copy(fontFolder = intent.path) }
            }
            ThemeConfigIntent.RequestFontFolder -> _effects.tryEmit(ThemeConfigEffect.OpenFontFolder)
            is ThemeConfigIntent.RequestTimePicker -> _effects.tryEmit(
                ThemeConfigEffect.OpenTimePicker(intent.field, intent.currentValue)
            )
            is ThemeConfigIntent.SetTime -> setTime(intent.field, intent.value)
            ThemeConfigIntent.DismissRefactorTip -> updateTheme {
                it.copy(showRefactorTip = false)
            }
        }
    }

    private fun updateAppShell(
        effect: ThemeConfigEffect? = null,
        transform: (AppShellSettings) -> AppShellSettings,
    ) {
        viewModelScope.launch {
            appShellSettingsGateway.update(transform)
            effect?.let(_effects::tryEmit)
        }
    }

    private fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch { themeSettingsGateway.update(transform) }
    }

    private fun resetDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            val defaultShell = AppShellSettings()
            val defaultTheme = ThemeSettings()
            val currentTheme = _uiState.value.theme

            listOf(currentTheme.backgroundImageLight, currentTheme.backgroundImageDark)
                .filterNotNull()
                .map(::File)
                .filter { it.absolutePath.startsWith(appCtx.externalFiles.absolutePath) }
                .forEach { it.delete() }
            listOf("home", "bookshelf", "explore", "rss", "my")
                .map { File(appCtx.filesDir, "nav_icons/$it.png") }
                .forEach { it.delete() }
            currentTheme.appFontPath?.let { path ->
                File(path)
                    .takeIf { it.absolutePath.startsWith(appCtx.filesDir.absolutePath) }
                    ?.delete()
            }

            appShellSettingsGateway.update { defaultShell }
            themeSettingsGateway.update { defaultTheme }
            _effects.tryEmit(ThemeConfigEffect.ApplyDayNight)
            _effects.tryEmit(ThemeConfigEffect.NotifyMain)
            _effects.tryEmit(ThemeConfigEffect.ChangeLauncherIcon(defaultShell.launcherIcon))
            _effects.tryEmit(ThemeConfigEffect.ShowToast(R.string.theme_config_reset_success))
            _uiState.update { it.copy(activeDialog = null) }
        }
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
            themeSettingsGateway.update {
                it.copy(
                    appTheme = value,
                    containerOpacity = if (value == "13") 0 else it.containerOpacity,
                )
            }
        }
    }

    private fun setMiuixMonet(enabled: Boolean) {
        viewModelScope.launch {
            themeSettingsGateway.update {
                it.copy(
                    useMiuixMonet = enabled,
                    appTheme = if (enabled && it.appTheme != "0" && it.appTheme != "12") {
                        "0"
                    } else {
                        it.appTheme
                    },
                )
            }
        }
    }

    private fun setDynamicColors(enabled: Boolean) {
        val value = if (enabled) "0" else "12"
        if (value != _uiState.value.theme.appTheme) selectTheme(value)
    }

    private fun setBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themeSettingsGateway.update {
                it.copy(
                    enableBlur = enabled,
                    enableProgressiveBlur = if (enabled) {
                        it.enableProgressiveBlur
                    } else {
                        false
                    },
                )
            }
        }
    }

    private fun setMainDestinationVisible(intent: ThemeConfigIntent.SetMainDestinationVisible) {
        val transform: (AppShellSettings) -> AppShellSettings = when (intent.route) {
            MainDestination.Home.route -> { current ->
                current.copy(
                    showHome = intent.visible,
                    defaultHomePage = current.fallbackHomePage(intent),
                )
            }
            MainDestination.Explore.route -> { current ->
                current.copy(
                    showDiscovery = intent.visible,
                    defaultHomePage = current.fallbackHomePage(intent),
                )
            }
            MainDestination.Rss.route -> { current ->
                current.copy(
                    showRss = intent.visible,
                    defaultHomePage = current.fallbackHomePage(intent),
                )
            }
            else -> return
        }
        updateAppShell(transform = transform)
    }

    private fun AppShellSettings.fallbackHomePage(
        intent: ThemeConfigIntent.SetMainDestinationVisible,
    ): String = if (!intent.visible && defaultHomePage == intent.route) {
        MainDestination.Bookshelf.route
    } else {
        defaultHomePage
    }

    private fun selectLauncherIcon(value: String) {
        viewModelScope.launch {
            appShellSettingsGateway.update { it.copy(launcherIcon = value) }
            _effects.tryEmit(ThemeConfigEffect.ChangeLauncherIcon(value))
        }
    }

    private fun selectNavigationIcon(intent: ThemeConfigIntent.SelectNavigationIcon) {
        val transform: (AppShellSettings) -> AppShellSettings = when (intent.destination) {
            MainDestination.Home.route -> { settings ->
                settings.copy(navIconHome = intent.path)
            }
            MainDestination.Bookshelf.route -> { settings ->
                settings.copy(navIconBookshelf = intent.path)
            }
            MainDestination.Explore.route -> { settings ->
                settings.copy(navIconExplore = intent.path)
            }
            MainDestination.Rss.route -> { settings ->
                settings.copy(navIconRss = intent.path)
            }
            MainDestination.My.route -> { settings ->
                settings.copy(navIconMy = intent.path)
            }
            else -> return
        }
        updateAppShell(transform = transform)
    }

    private fun setTime(field: ThemeTimeField, value: String) {
        updateTheme {
            when (field) {
                ThemeTimeField.EyeProtectionStart -> it.copy(eyeProtectionStartTime = value)
                ThemeTimeField.EyeProtectionEnd -> it.copy(eyeProtectionEndTime = value)
            }
        }
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
        themeSettingsGateway.update {
            if (dark) it.copy(backgroundImageDark = newPath)
            else it.copy(backgroundImageLight = newPath)
        }
    }

    private fun setAppFont(fileDoc: FileDoc) {
        launchFontJob {
            val extension = fileDoc.name.substringAfterLast('.', "ttf")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                ?: "ttf"
            val fontDir = appFontDir()
            val temp = File(fontDir, "app_font_${UUID.randomUUID()}.tmp")
            val target = try {
                fileDoc.openInputStream().getOrThrow().use { input ->
                    FileOutputStream(temp).use(input::copyTo)
                }
                // 以内容摘要命名：同一字体无论导入多少次都指向同一路径，
                // 既不会留下重复副本，字体缓存也能按路径命中。
                val digest = temp.inputStream().use(MD5Utils::md5Encode)
                File(fontDir, "app_font_$digest.$extension").also { target ->
                    if (target.isFile) {
                        temp.delete()
                    } else if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }
            } catch (e: Throwable) {
                temp.delete()
                throw e
            }
            // 复制期间可能已被新的选择或清除取代，此时不能写回路径。
            ensureActive()
            themeSettingsGateway.update { it.copy(appFontPath = target.absolutePath) }
            pruneCopiedFonts(keep = target)
        }
    }

    private fun clearAppFont() {
        launchFontJob {
            themeSettingsGateway.update { it.copy(appFontPath = null) }
            pruneCopiedFonts()
        }
    }

    /**
     * 字体的选择与清除串行执行：新任务先取消并等待上一个结束，
     * 避免未完成的复制晚于清除写回 appFontPath。
     */
    private fun launchFontJob(block: suspend CoroutineScope.() -> Unit) {
        val previous = fontJob
        fontJob = viewModelScope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            runCatching { block() }
                .onFailure { if (it !is CancellationException) it.printStackTrace() }
        }
    }

    private fun appFontDir() = File(appCtx.filesDir, "fonts").apply { mkdirs() }

    /**
     * 清理私有目录里已经用不到的字体副本，只认本应用复制的 app_font 前缀，
     * 不碰主题包导入的 theme_ 资源。
     */
    private fun pruneCopiedFonts(keep: File? = null) {
        appFontDir().listFiles()?.forEach { file ->
            if (file.name.startsWith("app_font") && file != keep) file.delete()
        }
    }
}
