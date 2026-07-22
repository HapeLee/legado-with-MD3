package io.legado.app.base

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.BuildConfig
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.diffFrom
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.ui.book.read.eyeProtectionColorFilter
import io.legado.app.ui.book.read.rememberEyeProtectionActive
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.utils.LogUtils
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.isNightMode
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.themeColor
import io.legado.app.utils.toggleSystemBar
import io.legado.app.utils.windowSize
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import top.yukonga.miuix.kmp.theme.MiuixTheme

abstract class BaseComposeActivity(
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true
) : AppCompatActivity() {

    private val appLocaleGateway by inject<AppLocaleGateway>()
    private val appUiConfigurationGateway by inject<AppUiConfigurationGateway>()
    private val themeSettingsGateway by inject<ThemeSettingsGateway>()
    private var lastUiConfiguration: AppUiConfiguration? = null

    @Composable
    protected abstract fun Content()

    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.disableAutoFill()
        AppContextWrapper.applyFont(this)

        super.onCreate(savedInstanceState)
        lastUiConfiguration = appUiConfigurationGateway.currentConfiguration
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setupSystemBar()
        // Compose 入口
        val initialUiConfiguration = appUiConfigurationGateway.currentConfiguration
        val initialThemeSettings = themeSettingsGateway.currentSettings
        setContent {
            val uiConfiguration by appUiConfigurationGateway.configuration
                .collectAsStateWithLifecycle(initialUiConfiguration)
            AppTheme(configuration = uiConfiguration) {
                SyncWindowBackground()
                val themeSettings by themeSettingsGateway.settings
                    .collectAsStateWithLifecycle(initialThemeSettings)
                val eyeProtectionActive = rememberEyeProtectionActive(
                    enabled = themeSettings.eyeProtectionEnabled,
                    schedule = themeSettings.eyeProtectionSchedule,
                    startTime = themeSettings.eyeProtectionStartTime,
                    endTime = themeSettings.eyeProtectionEndTime,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .eyeProtectionColorFilter(
                            enabled = eyeProtectionActive,
                            intensity = themeSettings.colorTemperature,
                        )
                ) {
                    Content()
                }
            }
        }

        if (imageBg) {
            upBackgroundImage()
        }

        observeLiveBus()
        observeAppUiConfiguration()
        traceConfiguration("onCreate")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        appUiConfigurationGateway.synchronizeSystemDarkTheme(newConfig.isNightMode)
        super.onConfigurationChanged(newConfig)
        // manifest 声明 locale/layoutDirection/screenLayout/uiMode configChanges 后，Compose 主壳
        // 可直接响应语言与深浅色变化，无需销毁 Activity。AppCompat 手动回调本方法时
        // 不会走 View 树分发，需要自己同步给 Compose（LocalConfiguration），并刷新
        // 系统栏与背景图。
        window.decorView.dispatchConfigurationChanged(newConfig)
        appLocaleGateway.synchronizeFromPlatform()
        setupSystemBar()
        if (imageBg) {
            upBackgroundImage()
        }
        traceConfiguration("onConfigurationChanged")
    }

    override fun onLocalesChanged(locales: LocaleListCompat) {
        super.onLocalesChanged(locales)
        appLocaleGateway.synchronizeFromPlatform()
    }

    open fun setupSystemBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setStatusBarColorAuto(
            themeColor(com.google.android.material.R.attr.colorSurface),
            true
        )

        toggleSystemBar(appUiConfigurationGateway.currentConfiguration.appShell.showStatusBar)
    }

    open fun upBackgroundImage() {
        try {
            val background = ThemeConfigStore.getBgImage(this, windowManager.windowSize)
            if (background != null) {
                hasWindowBgImage = true
                window.setBackgroundDrawable(background.toDrawable(resources))
            } else if (hasWindowBgImage) {
                hasWindowBgImage = false
                lastWindowBgColor = null
                window.setBackgroundDrawable(
                    themeColor(com.google.android.material.R.attr.colorSurface).toDrawable()
                )
            }
        } catch (_: Exception) {}
    }

    protected var hasWindowBgImage = false
    private var lastWindowBgColor: Int? = null

    /**
     * 窗口背景色与 Compose 主题背景色同步。
     * 窗口背景默认来自 XML 主题，与运行时计算的 Compose 主题色存在色差；
     * 转场淡出、启动交接等场景露出窗口背景时会出现颜色跳变。
     */
    @Composable
    private fun SyncWindowBackground() {
        if (transparent) return
        val backgroundColor = if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
            MiuixTheme.colorScheme.surface
        } else {
            LegadoTheme.colorScheme.background
        }
        SideEffect {
            if (!hasWindowBgImage) {
                val colorInt = backgroundColor.toArgb()
                if (lastWindowBgColor != colorInt) {
                    window.setBackgroundDrawable(colorInt.toDrawable())
                    lastWindowBgColor = colorInt
                }
            }
        }
    }

    open fun observeLiveBus() {
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            setupSystemBar()
        }
    }

    private fun observeAppUiConfiguration() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appUiConfigurationGateway.configuration.collect { configuration ->
                    val previous = lastUiConfiguration
                    lastUiConfiguration = configuration
                    if (previous == null) return@collect
                    val diff = configuration.diffFrom(previous)
                    if (diff.windowChanged) {
                        setupSystemBar()
                        if (imageBg) upBackgroundImage()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        traceConfiguration(
            "onDestroy changingConfigurations=0x${changingConfigurations.toString(16)} " +
                "isChangingConfigurations=$isChangingConfigurations isFinishing=$isFinishing"
        )
        super.onDestroy()
    }

    private fun traceConfiguration(event: String) {
        if (!BuildConfig.DEBUG) return
        LogUtils.d(
            "UiConfiguration",
            "${javaClass.simpleName}@${System.identityHashCode(this)} $event",
        )
    }

}
