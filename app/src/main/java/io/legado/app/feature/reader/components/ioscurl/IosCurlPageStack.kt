package io.legado.app.feature.reader.components.ioscurl

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.feature.reader.PageSnapshotBitmapPool
import io.legado.app.feature.reader.PageSnapshotKey
import io.legado.app.feature.reader.PageSnapshotLayer
import io.legado.app.feature.reader.drawPageSnapshot
import io.legado.app.feature.reader.core.transition.ioscurl.layout.CurlLayout
import io.legado.app.feature.reader.core.transition.ioscurl.control.CurlControl
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlColor
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMaterial
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMesh
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMeshInput
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlConfig
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import io.legado.app.feature.reader.platform.ioscurl.IosCurlRenderer
import io.legado.app.feature.reader.platform.ioscurl.IosCurlCanvasRenderer
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** Page rasterization stays outside the animation loop; only mesh and compositing change per frame. */
@Composable
internal fun IosCurlPageStack(
    pages: ReaderPageWindow,
    direction: ReaderTurnDirection?,
    turn: IosCurlTurn,
    redrawSignal: State<Long>,
    snapshots: PageSnapshotBitmapPool,
    baseLayer: GraphicsLayer,
    revealLayer: GraphicsLayer,
    background: Color,
    snapshotKey: (ReaderPage, Int, Int) -> PageSnapshotKey,
    settled: (ReaderPage) -> Boolean,
    pageContent: @Composable (ReaderPage) -> Unit,
) {
    val current = pages.current ?: return
    // One retained mesh/EGL/output set for as long as this mode stays in composition, including
    // the idle time between turns. prepare() and attach() already rebuild the output bitmap and
    // window binding for a changed viewport; replacing the holder on a page-size change would
    // only orphan the TextureView callbacks and in-flight captures. Leaving this mode or the
    // reader disposes it.
    val graphics = remember { IosCurlGraphics() }
    val currentGraphics = rememberUpdatedState(graphics)
    DisposableEffect(graphics) { onDispose { graphics.close() } }
    if (direction == null) {
        pageContent(current)
        return
    }
    val target = (if (direction == ReaderTurnDirection.NEXT) pages.next else pages.previous)
    if (target == null) {
        pageContent(current)
        SideEffect { turn.rendererReady() }
        return
    }
    val revision = redrawSignal.value
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val height = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val currentKey = snapshotKey(current, width, height)
        val targetKey = snapshotKey(target, width, height)
        var currentBitmap by remember(currentKey, revision) { mutableStateOf(snapshots.get(currentKey)) }
        var targetBitmap by remember(targetKey, revision) { mutableStateOf(snapshots.get(targetKey)) }
        val currentSettled = settled(current)
        val targetSettled = settled(target)
        var prepared by remember(currentKey, targetKey, revision) { mutableStateOf(false) }
        LaunchedEffect(currentKey, targetKey, revision, currentSettled, targetSettled, graphics) {
            // 换页或图片加载完成都会重启本 effect。旧纹理必须等新的两页捕获完成后再替换：
            // 否则动画进行中会先掉回静态快照（闪烁），等重新捕获完才恢复卷曲。
            try {
                // Cached pages are ready; only newly recorded layers need a frame before capture.
                if (currentBitmap == null) {
                    withFrameNanos { }
                    currentBitmap = snapshots.capture(baseLayer, currentKey, currentSettled)
                        ?: baseLayer.toImageBitmap()
                }
                if (targetBitmap == null) {
                    withFrameNanos { }
                    targetBitmap = snapshots.capture(revealLayer, targetKey, targetSettled)
                        ?: revealLayer.toImageBitmap()
                }
                val first = currentBitmap
                val second = targetBitmap
                prepared = false
                if (first != null && second != null) {
                    graphics.prepare(first.asAndroidBitmap(), second.asAndroidBitmap(), background.alpha == 1f)
                    prepared = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                Timber.tag("IosCurl").w(e, "Page capture unavailable; using recorded page layers")
            } catch (e: OutOfMemoryError) {
                Timber.tag("IosCurl").w(e, "Page capture exceeded available memory; using recorded page layers")
            }
            turn.rendererReady()
        }
        if (currentBitmap == null) PageSnapshotLayer(baseLayer, redrawSignal) { pageContent(current) }
        if (targetBitmap == null) PageSnapshotLayer(revealLayer, redrawSignal) { pageContent(target) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    isOpaque = false
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    // factory runs once; callbacks must read the current holder, not this lambda's capture.
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            currentGraphics.value.attach(this@apply, texture, width, height)
                        }
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                            currentGraphics.value.attach(this@apply, texture, width, height)
                        }
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            currentGraphics.value.detach(this@apply)
                            return true
                        }
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                            currentGraphics.value.presented(this@apply)
                        }
                    }
                }
            },
            onRelease = {
                currentGraphics.value.detach(it)
                it.surfaceTextureListener = null
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            val config = turn.config
            val values = turn.values // Read animation state only in the draw phase.
            if (!prepared || config == null || !graphics.draw(drawContext.canvas.nativeCanvas, config, values, background)) {
                // Loading images or unavailable GL resources must not expose a blank surface.
                val endpoints = config?.let(CurlControl::endpoints)
                val progress = endpoints?.let { (values.time - it.from.time) / (it.to.time - it.from.time) } ?: 0.0
                val showTarget = progress >= .5
                drawPageSnapshot(if (showTarget) targetBitmap else currentBitmap,
                    if (showTarget) revealLayer else baseLayer, width, height)
            }
        }
    }
}

internal class IosCurlGraphics : AutoCloseable {
    private var renderer: IosCurlRenderer? = null
    private var canvasRenderer: IosCurlCanvasRenderer? = null
    private var canvasAvailable = Build.VERSION.SDK_INT >= 34
    private var output: Bitmap? = null
    private val mesh = CurlMesh()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val outputRect = RectF()
    private var failed = false
    private var view: TextureView? = null
    private var window: Surface? = null
    private var windowReady by mutableStateOf(false)
    private var windowPresented by mutableStateOf(false)
    private var submittedValues: CurlValues? = null
    private var submittedConfig: CurlConfig? = null
    private var submittedBackground: Color? = null
    private val main = Handler(Looper.getMainLooper())
    private var windowGeneration = 0L
    private var closed = false

    fun attach(next: TextureView, texture: SurfaceTexture, width: Int, height: Int) {
        if (closed) return
        if (width <= 0 || height <= 0) {
            detach(next)
            return
        }
        try {
            detachWindow()
            view = next
            texture.setDefaultBufferSize(width, height)
            window = Surface(texture)
            bindWindow()
        } catch (error: RuntimeException) {
            unavailableWindow(error)
        } catch (error: OutOfMemoryError) {
            unavailableWindow(error)
        }
    }

    private fun unavailableWindow(error: Throwable) {
        Timber.tag("IosCurl").w(error, "Window attachment unavailable; using bitmap output")
        detachWindow()
        view?.alpha = 0f
    }

    fun detach(previous: TextureView) {
        if (view !== previous) return
        detachWindow()
        view = null
    }

    fun presented(source: TextureView) {
        // An older queued buffer must not uncover a newly captured page pair.
        if (view === source && windowReady && submittedValues != null) windowPresented = true
    }

    private fun bindWindow() {
        windowGeneration++
        windowReady = canvasRenderer == null && window?.let { renderer?.bindWindow(it) } == true
        windowPresented = false
        submittedValues = null
        view?.alpha = if (canvasRenderer != null) 0f else if (windowReady || renderer == null) 1f else 0f
    }

    private fun detachWindow() {
        windowGeneration++
        renderer?.bindWindow(null)
        window?.release()
        window = null
        windowReady = false
        windowPresented = false
        submittedValues = null
    }

    fun prepare(current: Bitmap, target: Bitmap, opaquePageBackground: Boolean) {
        if (closed) return
        canvasRenderer = null
        // Background alpha is a per-frame content condition, not a device capability: the branch
        // below re-checks it on every prepare(). Latching it into canvasAvailable would keep the
        // retained holder off the Canvas fast path for the rest of the mode.
        outputRect.set(0f, 0f, current.width.toFloat(), current.height.toFloat())
        if (opaquePageBackground && canvasAvailable && Build.VERSION.SDK_INT >= 34) {
            try {
                renderer?.bindWindow(null)
                canvasRenderer = IosCurlCanvasRenderer(current, target)
                bindWindow()
                failed = false
                return
            } catch (error: RuntimeException) {
                unavailableCanvas(error)
            } catch (error: OutOfMemoryError) {
                unavailableCanvas(error)
            }
        }
        prepareGl(current, target)
    }

    private fun prepareGl(current: Bitmap, target: Bitmap) {
        try {
            val next = renderer ?: IosCurlRenderer().also { renderer = it }
            next.upload(current, target)
            if (output?.width != current.width || output?.height != current.height) {
                val bitmap = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
                output = bitmap
                next.bindOutput(bitmap)
            }
            outputRect.set(0f, 0f, current.width.toFloat(), current.height.toFloat())
            bindWindow()
            failed = false
        } catch (e: RuntimeException) {
            fail(e)
        } catch (e: OutOfMemoryError) {
            fail(e)
        }
    }

    fun draw(canvas: android.graphics.Canvas, config: CurlConfig, inputValues: CurlValues, background: Color): Boolean {
        if (closed || failed) return false
        var direct = windowReady && renderer?.hasWindow == true
        val values = inputValues.renderValues()
        // TextureView invalidations must not become a second animation clock.
        if (direct && submittedValues == values && submittedConfig == config && submittedBackground == background) {
            return windowPresented
        }
        try {
            val layout = CurlLayout.build(config)
            val texture = if (layout.passes.single().source.nativeValue < 2) 0 else 1
            val material = CurlMaterial().copy(
                back = CurlColor(background.red.toDouble(), background.green.toDouble(),
                    background.blue.toDouble(), .8),
                shadow = CurlColor(0.0, 0.0, 0.0, .55),
            )
            val scale = (outputRect.width() / config.width).coerceIn(.1, 4.0)
            val frame = mesh.build(CurlMeshInput(values, config.width, config.height,
                scale, material = material), coalesceTriangleStrips = true)
            val underlays = layout.underlays.map { (if (it.source.nativeValue < 2) 0 else 1) to it.frame }
            val native = canvasRenderer
            if (Build.VERSION.SDK_INT >= 34 && native != null) {
                if (canvas.isHardwareAccelerated && native.canDraw(config.width, config.height,
                        underlays, background.alpha == 1f)) {
                    try {
                        // ReaderPageCanvas records a full background before its content.
                        native.draw(canvas, frame, config.width, config.height, texture,
                            1 - texture, underlays, opaquePageBackground = background.alpha == 1f)
                        return true
                    } catch (error: RuntimeException) {
                        unavailableCanvas(error)
                    } catch (error: OutOfMemoryError) {
                        unavailableCanvas(error)
                    }
                } else {
                    unavailableCanvas()
                }
                prepareGl(native.current, native.target)
                if (failed) return false
                direct = windowReady && renderer?.hasWindow == true
            }
            val renderer = renderer ?: return false
            val bitmap = output ?: return false
            if (direct) {
                val generation = windowGeneration
                renderer.renderAsync(frame, config.width.toFloat(), config.height.toFloat(), texture,
                    1 - texture, underlays) { error ->
                    if (error != null) main.post {
                        if (generation == windowGeneration && this.renderer === renderer) {
                            unavailableWindow(error)
                        }
                    }
                }
                submittedValues = values
                submittedConfig = config
                submittedBackground = background
                // Keep the snapshot above the window until its first buffer is latched.
                return windowPresented
            }
            renderer.render(frame, config.width.toFloat(), config.height.toFloat(), texture,
                1 - texture, underlays)
            val saved = canvas.save()
            try {
                canvas.translate(0f, outputRect.height())
                canvas.scale(1f, -1f)
                canvas.drawBitmap(bitmap, null, outputRect, paint)
            } finally {
                canvas.restoreToCount(saved)
            }
            return true
        } catch (e: RuntimeException) {
            fail(e)
        } catch (e: OutOfMemoryError) {
            fail(e)
        }
        return false
    }

    private fun unavailableCanvas(error: Throwable? = null) {
        if (error != null) {
            Timber.tag("IosCurl").w(error, "Canvas mesh unavailable; using GLES")
            // Only a real driver/construction failure disables the capability. A per-frame
            // underlay or software-canvas condition must stay retryable on the next prepare().
            canvasAvailable = false
        }
        canvasRenderer = null
    }

    private fun fail(error: Throwable) {
        Timber.tag("IosCurl").w(error, "Curl rendering unavailable; using the page snapshot")
        // A failed frame stays recoverable: release resources but let the next prepare() rebuild.
        release()
        failed = true
    }

    override fun close() {
        if (closed) return
        closed = true
        release()
    }

    private fun release() {
        canvasRenderer = null
        detachWindow()
        view?.alpha = 0f
        view = null
        renderer?.close()
        renderer = null
        // Canvas may still reference the last output; let Bitmap lifetime follow its references.
        output = null
    }
}
