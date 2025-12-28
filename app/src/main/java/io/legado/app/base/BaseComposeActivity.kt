package io.legado.app.base

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.getPrefString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.themeColor
import io.legado.app.utils.windowSize
import java.io.File

abstract class BaseComposeActivity(
    val fullScreen: Boolean = true,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true
) : AppCompatActivity() {

    @Composable
    protected abstract fun Content()

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        window.decorView.disableAutoFill()
        AppContextWrapper.applyLocaleAndFont(this)

        super.onCreate(savedInstanceState)

        setupSystemBar()
        // Compose 入口
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
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
            recreate()
        }
    }

    open fun initTheme() {
        when (getPrefString("app_theme", "0")) {
            "0" -> {
                DynamicColors.applyToActivitiesIfAvailable(application)
            }
            "1" -> setTheme(R.style.Theme_Base_GR)
            "2" -> setTheme(R.style.Theme_Base_Lemon)
            "3" -> setTheme(R.style.Theme_Base_WH)
            "4" -> setTheme(R.style.Theme_Base_Elink)
            "5" -> setTheme(R.style.Theme_Base_Sora)
            "6" -> setTheme(R.style.Theme_Base_August)
            "7" -> setTheme(R.style.Theme_Base_Carlotta)
            "8" -> setTheme(R.style.Theme_Base_Koharu)
            "9" -> setTheme(R.style.Theme_Base_Yuuka)
            "10" -> setTheme(R.style.Theme_Base_Phoebe)
            "11" -> setTheme(R.style.Theme_Base_Mujika)
            "12" -> {
                if (AppConfig.customMode == "accent")
                    setTheme(R.style.ThemeOverlay_WhiteBackground)

                val colorImagePath = getPrefString(PreferKey.colorImage)
                if (!colorImagePath.isNullOrBlank()) {
                    val file = File(colorImagePath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            val colorAccuracy = true
                            val targetWidth = if (colorAccuracy) (bitmap.width / 4).coerceAtMost(256) else 16
                            val targetHeight = if (colorAccuracy) (bitmap.height / 4).coerceAtMost(256) else 16
                            val scaledBitmap = bitmap.scale(targetWidth, targetHeight, false)

                            val options = DynamicColorsOptions.Builder()
                                .setContentBasedSource(scaledBitmap)
                                .build()

                            DynamicColors.applyToActivitiesIfAvailable(application, options)
                            bitmap.recycle()
                        }
                    }
                }else{
                    DynamicColors.applyToActivitiesIfAvailable(
                        application,
                        DynamicColorsOptions.Builder()
                            .setContentBasedSource(application.primaryColor)
                            .build()
                    )
                }
            }

            "13" -> setTheme(R.style.AppTheme_Transparent)
        }

        if (AppConfig.pureBlack)
            setTheme(R.style.ThemeOverlay_PureBlack)
    }

}
