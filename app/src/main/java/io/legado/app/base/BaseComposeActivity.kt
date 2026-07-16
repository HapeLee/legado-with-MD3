package io.legado.app.base

import android.os.Bundle
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.themeColor
import io.legado.app.utils.toggleSystemBar
import io.legado.app.utils.windowSize
import top.yukonga.miuix.kmp.theme.MiuixTheme

abstract class BaseComposeActivity(
    val fullScreen: Boolean = true,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true
) : AppCompatActivity() {

    @Composable
    protected abstract fun Content()

    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.disableAutoFill()
        AppContextWrapper.applyLocaleAndFont(this)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setupSystemBar()
        // Compose 入口
        setContent {
            AppTheme {
                SyncWindowBackground()
                Content()
            }
        }

        if (imageBg) {
            upBackgroundImage()
        }

        observeLiveBus()
    }

    open fun setupSystemBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (fullScreen) fullScreen()

        setStatusBarColorAuto(
            themeColor(com.google.android.material.R.attr.colorSurface),
            true,
            fullScreen
        )

        toggleSystemBar(AppConfig.showStatusBar)
    }

    open fun upBackgroundImage() {
        try {
            ThemeConfigStore.getBgImage(this, windowManager.windowSize)?.let {
                hasWindowBgImage = true
                window.setBackgroundDrawable(it.toDrawable(resources))
            }
        } catch (_: Exception) {}
    }

    private var hasWindowBgImage = false

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
                window.setBackgroundDrawable(backgroundColor.toArgb().toDrawable())
            }
        }
    }

    open fun observeLiveBus() {
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            setupSystemBar()
        }
    }

}
