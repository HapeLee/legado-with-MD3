package io.legado.app.base

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.ThemeSyncer
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.themeColor
import io.legado.app.utils.windowSize

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

        setupSystemBar()
        // Compose 入口
        setContent {
            AppTheme {
                Surface() {
                    Content()
                }
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
    }

    open fun upBackgroundImage() {
        try {
            ThemeConfig.getBgImage(this, windowManager.windowSize)?.let {
                window.setBackgroundDrawable(it.toDrawable(resources))
            }
        } catch (_: Exception) {}
    }

    open fun observeLiveBus() {
        observeEvent<String>(EventBus.RECREATE) {
            ThemeSyncer.syncAll()
            recreate()
        }
    }

}
