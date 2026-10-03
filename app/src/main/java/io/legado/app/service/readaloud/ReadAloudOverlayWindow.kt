package io.legado.app.service.readaloud

import android.app.AppOpsManager
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.legado.app.base.BaseService
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.gateway.PlaybackCapsuleGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.feature.readaloud.overlay.capsulePresentationOffsetX
import io.legado.app.feature.readaloud.overlay.capsuleWindowY
import io.legado.app.help.LifecycleHelp
import io.legado.app.ui.book.read.ReadAloudCapsule
import io.legado.app.ui.main.MainIntent
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import kotlin.math.roundToInt

/**
 * Android 平台窗口适配器。随现有朗读或有声书前台服务创建/销毁，不另建服务或播放状态。
 * 只在本应用没有可见 Activity 且获准悬浮时挂载 WRAP_CONTENT 窗口。
 */
class ReadAloudOverlayWindow(
    private val service: BaseService,
    private val ownerSource: PlaybackCapsuleSource = PlaybackCapsuleSource.ReadAloud,
) : AutoCloseable {
    private val scope = service.lifecycleScope
    private val settingsGateway: ReadAloudSettingsGateway =
        get(ReadAloudSettingsGateway::class.java)
    private val configurationGateway: AppUiConfigurationGateway =
        get(AppUiConfigurationGateway::class.java)
    private val player: PlaybackCapsuleGateway = get(PlaybackCapsuleGateway::class.java)
    private var settings by mutableStateOf(settingsGateway.currentSettings)
    private var playerState by mutableStateOf(player.state.value)
    private var windowOffsetX = settings.capsuleOffsetX
    private var windowOffsetY = settings.capsuleOffsetY
    private var windowExpansion = 1f
    private var view: ComposeView? = null
    private var owner: OverlayViewOwner? = null
    private var windowManager: WindowManager? = null
    private var parameters: WindowManager.LayoutParams? = null
    private var visibilityJob: Job? = null
    private var closed = false
    private val jobs = mutableListOf<Job>()
    private val appOps = service.getSystemService(AppOpsManager::class.java)
    private val permissionListener = AppOpsManager.OnOpChangedListener { operation, packageName ->
        if (operation == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && packageName == service.packageName) {
            scope.launch { refreshVisibility() }
        }
    }

    init {
        appOps.startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            service.packageName,
            permissionListener
        )
        jobs += scope.launch {
            settingsGateway.settings.collect { updated ->
                settings = updated
                refreshVisibility()
            }
        }
        jobs += scope.launch { player.state.collect { playerState = it; refreshVisibility() } }
        jobs += scope.launch { LifecycleHelp.appVisible.collect { refreshVisibility() } }
    }

    private fun shouldShow(): Boolean = !closed && shouldShowExternalCapsule(
        settings.showReadAloudCapsule,
        LifecycleHelp.appVisible.value,
        Settings.canDrawOverlays(service),
        ownerSource,
        playerState.source,
    )

    private fun refreshVisibility() {
        if (!shouldShow()) {
            visibilityJob?.cancel()
            visibilityJob = null
            removeWindow()
        } else if (view == null && visibilityJob?.isActive != true) {
            // Activity 重建的短暂不可见不应闪出应用外窗口。
            visibilityJob = scope.launch {
                delay(150)
                if (shouldShow() && view == null) addWindow()
            }
        }
    }

    private fun addWindow() {
        val display =
            service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                ?: return
        val displayContext = service.createDisplayContext(display)
        val context = if (Build.VERSION.SDK_INT >= 30) {
            displayContext.createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                null
            )
        } else displayContext
        val manager = context.getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            setTitle("ReadAloudCapsule")
        }
        val viewOwner = OverlayViewOwner()
        val capsuleView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(viewOwner)
            setViewTreeSavedStateRegistryOwner(viewOwner)
            setViewTreeViewModelStoreOwner(viewOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val configuration by configurationGateway.configuration.collectAsStateWithLifecycle()
                AppTheme(configuration, applyBackground = false) {
                    ReadAloudCapsule(
                        bookName = playerState.bookName,
                        author = playerState.author,
                        coverPath = playerState.coverPath,
                        sourceOrigin = playerState.sourceOrigin,
                        isPaused = playerState.isPaused,
                        offsetXDp = settings.capsuleOffsetX,
                        offsetYDp = settings.capsuleOffsetY,
                        progress = playerState.progress,
                        autoCollapse = settings.capsuleAutoCollapse,
                        onPositionChanged = ::savePosition,
                        onTogglePause = { playerState.source?.let(player::togglePause) },
                        onStop = { playerState.source?.let(player::stop) },
                        onOpenPlayer = {
                            val intent =
                                if (playerState.source == PlaybackCapsuleSource.AudioBook) {
                                    MainIntent.createAudioPlayIntent(
                                        service,
                                        playerState.bookUrl,
                                        playerState.inBookshelf
                                    )
                                } else {
                                    // 换图标变体会禁用 MainActivity 组件，显式 Intent 会抛
                                    // ActivityNotFoundException；必须解析当前启用的启动器组件。
                                    MainIntent.createLauncherIntent(service).apply {
                                        putExtra(MainIntent.EXTRA_OPEN_READ_ALOUD_PLAYER, true)
                                    }
                                }
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            service.startActivity(intent)
                        },
                        onWindowPositionChanged = ::moveWindow,
                    )
                }
            }
        }
        windowManager = manager
        parameters = params
        owner = viewOwner
        view = capsuleView
        applySavedPosition(updateWindow = false)
        try {
            manager.addView(capsuleView, params)
            capsuleView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    // 折叠/展开只改变窗口大小，保持屏幕上的中心坐标。
                    applyPosition()
                }
            }
        } catch (error: RuntimeException) {
            // 权限被撤销或系统拒绝窗口时保留应用内路径，并明确记录平台失败。
            LogUtils.d("ReadAloudOverlay", "Unable to attach overlay: $error")
            removeWindow()
        }
    }

    private fun applySavedPosition(updateWindow: Boolean = true) {
        windowOffsetX = settings.capsuleOffsetX
        windowOffsetY = settings.capsuleOffsetY
        applyPosition(updateWindow)
    }

    private fun applyPosition(updateWindow: Boolean = true) {
        val params = parameters ?: return
        val density = service.resources.displayMetrics.density
        val bounds =
            if (Build.VERSION.SDK_INT >= 30) windowManager?.maximumWindowMetrics?.bounds else null
        val screenWidth = bounds?.width() ?: service.resources.displayMetrics.widthPixels
        params.x = (capsulePresentationOffsetX(
            windowOffsetX, screenWidth / density,
            (view?.width ?: 0) / density, windowExpansion
        ) * density).roundToInt()
        params.y = capsuleWindowY(windowOffsetY, density, view?.height ?: 0)
        if (updateWindow) updatePosition()
    }

    private fun moveWindow(xDp: Float, yDp: Float, expansion: Float) {
        windowOffsetX = xDp
        windowOffsetY = yDp
        windowExpansion = expansion
        applyPosition()
    }

    private fun updatePosition() {
        val capsule = view ?: return
        val params = parameters ?: return
        if (!capsule.isAttachedToWindow) return
        try {
            windowManager?.updateViewLayout(capsule, params)
        } catch (error: RuntimeException) {
            LogUtils.d("ReadAloudOverlay", "Unable to move overlay: $error")
            removeWindow()
        }
    }

    private fun savePosition(x: Float, y: Float) {
        // 保存完整封面的中心，不能用半封面窗口的中心反推，否则切回应用会跳位。
        scope.launch { settingsGateway.update { it.copy(capsuleOffsetX = x, capsuleOffsetY = y) } }
    }

    fun onConfigurationChanged() {
        removeWindow()
        refreshVisibility()
    }

    private fun removeWindow() {
        val capsule = view
        view = null
        if (capsule != null) {
            try {
                if (capsule.isAttachedToWindow) windowManager?.removeViewImmediate(capsule)
            } catch (error: RuntimeException) {
                LogUtils.d("ReadAloudOverlay", "Unable to detach overlay: $error")
            }
            capsule.disposeComposition()
        }
        owner?.close()
        owner = null
        parameters = null
        windowManager = null
    }

    override fun close() {
        closed = true
        visibilityJob?.cancel()
        jobs.forEach { it.cancel() }
        appOps.stopWatchingMode(permissionListener)
        removeWindow()
    }
}

internal fun shouldShowExternalCapsule(
    enabled: Boolean,
    appVisible: Boolean,
    permissionGranted: Boolean,
    ownerSource: PlaybackCapsuleSource? = null,
    selectedSource: PlaybackCapsuleSource? = null,
): Boolean =
    enabled && !appVisible && permissionGranted && ownerSource == selectedSource

private class OverlayViewOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner,
    AutoCloseable {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun close() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
