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
import androidx.core.os.LocaleListCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import io.legado.app.R
import io.legado.app.BuildConfig
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.AppUiConfigurationDiff
import io.legado.app.domain.model.settings.diffFrom
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.applyOpenTint
import io.legado.app.utils.applyTint
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.isNightMode
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.LogUtils
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.themeColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.windowSize
import java.io.File
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject


abstract class BaseActivity<VB : ViewBinding>(
    val fullScreen: Boolean = true,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true
) : AppCompatActivity() {

    protected open val legacyUiConfigurationPolicy =
        LegacyUiConfigurationPolicy.ControlledRecreate

    private val appLocaleGateway by inject<AppLocaleGateway>()
    private val appUiConfigurationGateway by inject<AppUiConfigurationGateway>()
    private var lastUiConfiguration: AppUiConfiguration? = null
    private var lastPlatformConfiguration: Configuration? = null
    private var pendingControlledRecreate = false
    private var recreatePosted = false
    private var hasWindowBackgroundImage = false

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
        AppContextWrapper.applyFont(this)
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
        lastPlatformConfiguration = Configuration(resources.configuration)
        lastUiConfiguration = appUiConfigurationGateway.currentConfiguration
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S)
            enableEdgeToEdge()
        else{
            setupSystemBar()
        }
        //setupSystemBar()
        setContentView(binding.root)
        upBackgroundImage()
        findViewById<AppBarLayout>(R.id.title_bar)
        //?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)


        observeLiveBus()
        observeAppUiConfiguration()
        traceConfiguration("onCreate")
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
        val isDarkTheme = AppConfig.isNightTheme
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDarkTheme
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val previous = lastPlatformConfiguration
        appUiConfigurationGateway.synchronizeSystemDarkTheme(newConfig.isNightMode)
        super.onConfigurationChanged(newConfig)
        lastPlatformConfiguration = Configuration(newConfig)
        appLocaleGateway.synchronizeFromPlatform()

        val platformDiff = AppUiConfigurationDiff(
            localeChanged = previous != null && previous.locales != newConfig.locales,
            themeChanged = hasPlatformNightModeChanged(
                themeMode = appUiConfigurationGateway.currentConfiguration.appShell.themeMode,
                previousUiMode = previous?.uiMode,
                newUiMode = newConfig.uiMode,
            ),
        )
        if (platformDiff.hasChanges) {
            handleAppUiConfiguration(
                appUiConfigurationGateway.currentConfiguration,
                platformDiff,
            )
        }
        traceConfiguration("onConfigurationChanged", platformDiff)
    }

    override fun onLocalesChanged(locales: LocaleListCompat) {
        super.onLocalesChanged(locales)
        appLocaleGateway.synchronizeFromPlatform()
    }

    override fun onResume() {
        super.onResume()
        postControlledRecreateIfNeeded()
    }

    override fun onDestroy() {
        traceConfiguration("onDestroy")
        super.onDestroy()
    }



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
                DynamicColors.applyToActivityIfAvailable(this)
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
                val colorImagePath = getPrefString(PreferKey.colorImage)
                var colorImageApplied = false
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

                            DynamicColors.applyToActivityIfAvailable(this, options)
                            bitmap.recycle()
                            colorImageApplied = true
                        }
                    }
                }
                if (!colorImageApplied) {
                    // 取色图片缺失或解码失败时回退到种子色，
                    // 否则该 Activity 完全拿不到动态配色，与 Compose 界面不一致
                    DynamicColors.applyToActivityIfAvailable(
                        this,
                        DynamicColorsOptions.Builder()
                            .setContentBasedSource(application.primaryColor)
                            .build()
                    )
                }

                // 必须在动态取色之后应用，否则会被动态配色的 surface/background 覆盖
                if (AppConfig.customMode == "accent")
                    setTheme(R.style.ThemeOverlay_WhiteBackground)
            }

            "13" -> setTheme(R.style.AppTheme_Transparent)
        }

        if (AppConfig.pureBlack)
            setTheme(R.style.ThemeOverlay_PureBlack)
    }

    open fun upBackgroundImage() {
        if (imageBg) {
            try {
                val background = ThemeConfigStore.getBgImage(this, windowManager.windowSize)
                if (background != null) {
                    hasWindowBackgroundImage = true
                    window.decorView.background = background.toDrawable(resources)
                } else if (hasWindowBackgroundImage) {
                    hasWindowBackgroundImage = false
                    window.decorView.background = themeColor(
                        com.google.android.material.R.attr.colorSurface
                    ).toDrawable()
                }
            } catch (e: OutOfMemoryError) {
                toastOnUi("背景图片太大,内存溢出")
            } catch (e: Exception) {
                AppLog.put("加载背景出错\n${e.localizedMessage}", e)
            }
        }
    }

    open fun observeLiveBus() {
        // Legacy feature events are registered by subclasses. App-wide UI configuration
        // is collected separately through observeAppUiConfiguration().
    }

    protected open fun applyLegacyUiConfiguration(
        configuration: AppUiConfiguration,
        diff: AppUiConfigurationDiff,
    ) = Unit

    protected open fun rebindLegacyViewTree(
        configuration: AppUiConfiguration,
        diff: AppUiConfigurationDiff,
    ): Boolean = false

    private fun observeAppUiConfiguration() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appUiConfigurationGateway.configuration.collect { configuration ->
                    val previous = lastUiConfiguration
                    lastUiConfiguration = configuration
                    if (previous == null) return@collect
                    val diff = configuration.diffFrom(previous)
                    if (diff.hasChanges) {
                        handleAppUiConfiguration(configuration, diff)
                    }
                }
            }
        }
    }

    private fun handleAppUiConfiguration(
        configuration: AppUiConfiguration,
        diff: AppUiConfigurationDiff,
    ) {
        traceConfiguration("apply", diff)
        if (diff.themeChanged || diff.windowChanged) {
            setupSystemBar()
            upBackgroundImage()
        }
        if (!diff.requiresLegacyContentRefresh) return
        when (legacyUiConfigurationPolicy) {
            LegacyUiConfigurationPolicy.HotApply -> {
                if (diff.fontScaleChanged) {
                    AppContextWrapper.applyFont(this)
                }
                applyLegacyUiConfiguration(configuration, diff)
            }
            LegacyUiConfigurationPolicy.RebindViewTree -> {
                if (diff.fontScaleChanged) {
                    AppContextWrapper.applyFont(this)
                }
                if (!rebindLegacyViewTree(configuration, diff)) {
                    requestControlledRecreate()
                }
            }
            LegacyUiConfigurationPolicy.ControlledRecreate -> requestControlledRecreate()
            LegacyUiConfigurationPolicy.ApplyOnNextOpen -> Unit
        }
    }

    private fun requestControlledRecreate() {
        pendingControlledRecreate = true
        postControlledRecreateIfNeeded()
    }

    private fun postControlledRecreateIfNeeded() {
        if (!pendingControlledRecreate || recreatePosted || isFinishing || isDestroyed) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        recreatePosted = true
        window.decorView.post {
            recreatePosted = false
            if (pendingControlledRecreate && !isFinishing && !isDestroyed &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                pendingControlledRecreate = false
                traceConfiguration("controlledRecreate")
                recreate()
            }
        }
    }

    private fun traceConfiguration(
        event: String,
        diff: AppUiConfigurationDiff? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        LogUtils.d(
            "UiConfiguration",
            "${javaClass.simpleName}@${System.identityHashCode(this)} $event" +
                (diff?.let { " diff=$it" } ?: "")
        )
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
