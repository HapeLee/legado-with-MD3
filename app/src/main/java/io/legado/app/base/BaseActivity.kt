package io.legado.app.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.window.OnBackInvokedDispatcher
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.theme.ThemeSyncer
import io.legado.app.utils.applyOpenTint
import io.legado.app.utils.applyTint
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.themeColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.windowSize
import java.io.File


abstract class BaseActivity<VB : ViewBinding>(
    val fullScreen: Boolean = true,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true
) : AppCompatActivity() {

    protected abstract val binding: VB

    val isInMultiWindow: Boolean
        @SuppressLint("ObsoleteSdkInt")
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isInMultiWindowMode
            } else {
                false
            }
        }


    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
//        if (AppConst.menuViewNames.contains(name) && parent?.parent is FrameLayout) {
//            (parent.parent as View).setBackgroundColor(backgroundColor)
//        }
        return super.onCreateView(parent, name, context, attrs)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        window.decorView.disableAutoFill()
        AppContextWrapper.applyLocaleAndFont(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val enable = !AppConfig.isPredictiveBackEnabled
            if (enable) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT
                ) {
                    onBackPressedDispatcher.onBackPressed()
                }
            } else {
                //不注册才是启用
            }
        }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S)
            enableEdgeToEdge()
        else{
            setupSystemBar()
        }
        window.setNavigationBarColorAuto(themeColor(com.google.android.material.R.attr.colorSurface))
        //setupSystemBar()
        setContentView(binding.root)
        upBackgroundImage()
        findViewById<AppBarLayout>(R.id.title_bar)
        //?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)


        observeLiveBus()
        //onActivityCreated(savedInstanceState)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)

//        findViewById<TitleBar>(R.id.title_bar)
//            ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        //setupSystemBar()
    }

    open fun setupSystemBar() {
        if (fullScreen && !isInMultiWindow) {
            fullScreen()
        }
        setStatusBarColorAuto(
            themeColor(com.google.android.material.R.attr.colorSurface),
            true,
            fullScreen
        )
        val isDarkTheme = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO,
            Configuration.UI_MODE_NIGHT_UNDEFINED -> false
            else -> false
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDarkTheme
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        //findViewById<TitleBar>(R.id.title_bar)
//        //Log.d("Config", "uiMode = ${newConfig.uiMode}")
//            //?.onMultiWindowModeChanged(isInMultiWindow, fullScreen)
//        //setupSystemBar()
//    }



    final override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val bool = onCompatCreateOptionsMenu(menu)
        menu.applyTint(this, toolBarTheme)
        return bool
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.applyOpenTint(this)
        return super.onMenuOpened(featureId, menu)
    }

    open fun onCompatCreateOptionsMenu(menu: Menu) = super.onCreateOptionsMenu(menu)

    final override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            supportFinishAfterTransition()
            return true
        }
        return onCompatOptionsItemSelected(item)
    }

    open fun onCompatOptionsItemSelected(item: MenuItem) = super.onOptionsItemSelected(item)

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

    open fun upBackgroundImage() {
        if (imageBg) {
            try {
                ThemeConfig.getBgImage(this, windowManager.windowSize)?.let {
                    window.decorView.background = it.toDrawable(resources)
                }
            } catch (e: OutOfMemoryError) {
                toastOnUi("背景图片太大,内存溢出")
            } catch (e: Exception) {
                AppLog.put("加载背景出错\n${e.localizedMessage}", e)
            }
        }
    }

    open fun observeLiveBus() {
        observeEvent<String>(EventBus.RECREATE) {
            ThemeSyncer.syncAll()
            recreate()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            false
        }
    }

    override fun finish() {
        currentFocus?.hideSoftInput()
        super.finish()
    }
}