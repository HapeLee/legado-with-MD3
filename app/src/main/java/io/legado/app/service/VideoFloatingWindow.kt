package io.legado.app.service

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.ui.PlayerView
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.legado.app.model.VideoPlay
import io.legado.app.ui.main.MainActivity

/**
 * 视频悬浮小窗：应用不在前台时把画面搬到系统窗口上继续看。
 *
 * 与播放页互斥地绑定同一个 ExoPlayer：播放页在前台时由其 PlayerView 渲染，
 * 离开应用后小窗接管（播放页在 ON_STOP 时会解绑，见 VideoPlayerScreen）。
 */
class VideoFloatingWindow(private val service: VideoPlayService) {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayViewOwner? = null

    private var offsetX = 24
    private var offsetY = 120

    val isShowing: Boolean get() = floatingView != null

    fun toggle() {
        if (isShowing) close() else show()
    }

    @SuppressLint("InflateParams")
    fun show() {
        if (isShowing) return
        if (!Settings.canDrawOverlays(service)) return
        val display = service.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val displayContext = service.createDisplayContext(display)
        val context = if (Build.VERSION.SDK_INT >= 30) {
            displayContext.createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null
            )
        } else {
            displayContext
        }
        val manager = context.getSystemService(WindowManager::class.java)
        val layoutParams = WindowManager.LayoutParams(
            FLOATING_WIDTH_PX,
            FLOATING_HEIGHT_PX,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = offsetX
            y = offsetY
            setTitle("VideoFloatingWindow")
        }
        val viewOwner = OverlayViewOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(viewOwner)
            setViewTreeSavedStateRegistryOwner(viewOwner)
            setViewTreeViewModelStoreOwner(viewOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    VideoFloatingContent(
                        onClose = { close() },
                        onExpand = { expandToPlayPage() },
                        onDrag = ::move,
                    )
                }
            }
        }
        manager.addView(composeView, layoutParams)
        windowManager = manager
        floatingView = composeView
        params = layoutParams
        owner = viewOwner
    }

    fun close() {
        val view = floatingView ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
        runCatching { owner?.close() }
        windowManager = null
        floatingView = null
        params = null
        owner = null
    }

    private fun move(dx: Float, dy: Float) {
        val layoutParams = params ?: return
        layoutParams.x += dx.toInt()
        layoutParams.y += dy.toInt()
        offsetX = layoutParams.x
        offsetY = layoutParams.y
        runCatching { windowManager?.updateViewLayout(floatingView, layoutParams) }
    }

    private fun expandToPlayPage() {
        service.startActivity(
            MainActivity.createVideoPlayIntent(service, VideoPlay.book?.bookUrl)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @Composable
    private fun VideoFloatingContent(
        onClose: () -> Unit,
        onExpand: () -> Unit,
        onDrag: (Float, Float) -> Unit,
    ) {
        Box(
            modifier = Modifier
                .size(FLOATING_WIDTH.dp, FLOATING_HEIGHT.dp)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier.size(FLOATING_WIDTH.dp, FLOATING_HEIGHT.dp),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = VideoPlay.player
                        setOnClickListener { onExpand() }
                    }
                },
                onRelease = { it.player = null },
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp),
            ) {
                Text(text = "×", color = Color.White)
            }
        }
    }

    companion object {
        private const val FLOATING_WIDTH_PX = 320
        private const val FLOATING_HEIGHT_PX = 180
        private const val FLOATING_WIDTH = 160f
        private const val FLOATING_HEIGHT = 90f
    }
}
