package io.legado.app.feature.reader.components.ioscurl

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
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
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import io.legado.app.feature.reader.platform.ioscurl.IosCurlRenderer
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** Page rasterization stays outside the animation loop; only mesh and compositing change per frame. */
@Composable
internal fun IosCurlPageStack(
    pages: ReaderPageWindow,
    direction: ReaderTurnDirection,
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
        val graphics = remember { IosCurlGraphics() }
        var prepared by remember(currentKey, targetKey, revision) { mutableStateOf(false) }
        DisposableEffect(graphics) { onDispose { graphics.close() } }
        LaunchedEffect(currentKey, targetKey, revision, currentSettled, targetSettled) {
            // 换页或图片加载完成都会重启本 effect。旧渲染器必须等新的两页捕获完成后再关：
            // 否则动画进行中会先掉回静态快照（闪烁），等重新捕获完才恢复卷曲。
            try {
                withFrameNanos { }
                if (currentBitmap == null) currentBitmap = snapshots.capture(baseLayer, currentKey, currentSettled)
                    ?: baseLayer.toImageBitmap()
                withFrameNanos { }
                if (targetBitmap == null) targetBitmap = snapshots.capture(revealLayer, targetKey, targetSettled)
                    ?: revealLayer.toImageBitmap()
                val first = currentBitmap
                val second = targetBitmap
                graphics.close()
                prepared = false
                if (first != null && second != null) {
                    graphics.prepare(first.asAndroidBitmap(), second.asAndroidBitmap())
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
        Canvas(Modifier.fillMaxSize()) {
            val config = turn.config
            val values = turn.values // Read animation state only in the draw phase.
            if (!prepared || config == null || !graphics.draw(drawContext.canvas.nativeCanvas, turn, background)) {
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

private class IosCurlGraphics : AutoCloseable {
    private var renderer: IosCurlRenderer? = null
    private var output: Bitmap? = null
    private val mesh = CurlMesh()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val outputRect = RectF()
    private var failed = false

    fun prepare(current: Bitmap, target: Bitmap) {
        try {
            val next = IosCurlRenderer()
            renderer = next
            // GraphicsLayer snapshots may be hardware-backed; GLUtils needs CPU-readable pixels.
            var first: Bitmap? = null
            var second: Bitmap? = null
            try {
                first = if (current.config == Bitmap.Config.HARDWARE)
                    current.copy(Bitmap.Config.ARGB_8888, false) else current
                second = if (target.config == Bitmap.Config.HARDWARE)
                    target.copy(Bitmap.Config.ARGB_8888, false) else target
                next.upload(requireNotNull(first), requireNotNull(second))
            } finally {
                if (first !== current) first?.recycle()
                if (second !== target) second?.recycle()
            }
            val bitmap = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
            output = bitmap
            next.bindOutput(bitmap)
            outputRect.set(0f, 0f, current.width.toFloat(), current.height.toFloat())
            failed = false
        } catch (e: RuntimeException) {
            fail(e)
        } catch (e: OutOfMemoryError) {
            fail(e)
        }
    }

    fun draw(canvas: android.graphics.Canvas, turn: IosCurlTurn, background: Color): Boolean {
        if (failed) return false
        val renderer = renderer ?: return false
        val bitmap = output ?: return false
        val config = turn.config ?: return false
        try {
            val layout = CurlLayout.build(config)
            val texture = if (layout.passes.single().source.nativeValue < 2) 0 else 1
            val material = CurlMaterial().copy(
                back = CurlColor(background.red.toDouble(), background.green.toDouble(),
                    background.blue.toDouble(), .8),
                shadow = CurlColor(0.0, 0.0, 0.0, .55),
            )
            val scale = (bitmap.width / config.width).coerceIn(.1, 4.0)
            val frame = mesh.build(CurlMeshInput(turn.values.renderValues(), config.width, config.height,
                scale, material = material))
            renderer.render(frame, config.width.toFloat(), config.height.toFloat(), texture,
                1 - texture, layout.underlays.map {
                    (if (it.source.nativeValue < 2) 0 else 1) to it.frame
                })
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

    private fun fail(error: Throwable) {
        Timber.tag("IosCurl").w(error, "Curl rendering unavailable; using the page snapshot")
        close()
        failed = true
    }

    override fun close() {
        renderer?.close()
        renderer = null
        // Canvas may still reference the last output; let Bitmap lifetime follow its references.
        output = null
    }
}
