package io.legado.app.feature.reader.components.googlecurl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import io.legado.app.feature.reader.PageSnapshotBitmapPool
import io.legado.app.feature.reader.PageSnapshotKey
import io.legado.app.feature.reader.PageSnapshotLayer
import io.legado.app.feature.reader.drawPageSnapshot
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import io.legado.app.feature.reader.platform.googlecurl.GoogleCurlRenderer
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import kotlin.math.roundToInt

/** Shares the reader's bounded page cache; animation frames only update the wave mesh. */
@Composable
internal fun GoogleCurlPageStack(
    pages: ReaderPageWindow,
    direction: ReaderTurnDirection,
    progress: () -> Float,
    redrawSignal: State<Long>,
    snapshots: PageSnapshotBitmapPool,
    baseLayer: GraphicsLayer,
    revealLayer: GraphicsLayer,
    snapshotKey: (ReaderPage, Int, Int) -> PageSnapshotKey,
    settled: (ReaderPage) -> Boolean,
    onReady: () -> Unit,
    pageContent: @Composable (ReaderPage) -> Unit,
) {
    val current = pages.current ?: return
    val target = if (direction == ReaderTurnDirection.NEXT) pages.next else pages.previous
    if (target == null) {
        pageContent(current)
        SideEffect(onReady)
        return
    }
    val revision = redrawSignal.value
    val latestOnReady by rememberUpdatedState(onReady)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val height = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val currentKey = snapshotKey(current, width, height)
        val targetKey = snapshotKey(target, width, height)
        val currentSettled = settled(current)
        val targetSettled = settled(target)
        var currentBitmap by remember(currentKey, revision, currentSettled) { mutableStateOf(snapshots.get(currentKey)) }
        var targetBitmap by remember(targetKey, revision, targetSettled) { mutableStateOf(snapshots.get(targetKey)) }
        val renderer = remember { GoogleCurlRenderer() }
        var prepared by remember(currentKey, targetKey, revision) { mutableStateOf(false) }
        DisposableEffect(renderer) { onDispose { renderer.close() } }
        LaunchedEffect(currentKey, targetKey, revision, currentSettled, targetSettled) {
            // 换页或图片加载完成都会重启本 effect。旧的网格状态必须等新的两页捕获完成后再清：
            // 否则动画进行中会先掉回静态快照（闪烁），等重新捕获完才恢复波浪。
            try {
                withFrameNanos { }
                if (currentBitmap == null) currentBitmap = snapshots.capture(baseLayer, currentKey, currentSettled)
                    ?: baseLayer.toImageBitmap()
                withFrameNanos { }
                if (targetBitmap == null) targetBitmap = snapshots.capture(revealLayer, targetKey, targetSettled)
                    ?: revealLayer.toImageBitmap()
                val first = currentBitmap
                val second = targetBitmap
                renderer.close()
                prepared = false
                if (first != null && second != null) {
                    renderer.prepare(first.asAndroidBitmap(), second.asAndroidBitmap())
                    prepared = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                renderer.close()
                Timber.tag("GoogleCurl").w(e, "Page capture unavailable; using recorded page layers")
            } catch (e: OutOfMemoryError) {
                renderer.close()
                Timber.tag("GoogleCurl").w(e, "Page capture exceeded available memory; using recorded page layers")
            }
            latestOnReady()
        }
        if (currentBitmap == null) PageSnapshotLayer(baseLayer, redrawSignal) { pageContent(current) }
        if (targetBitmap == null) PageSnapshotLayer(revealLayer, redrawSignal) { pageContent(target) }
        Canvas(Modifier.fillMaxSize()) {
            val p = progress().coerceIn(0f, 1f)
            val drawn = if (prepared) try {
                renderer.draw(drawContext.canvas.nativeCanvas, direction, p,
                    width.toFloat(), height.toFloat(), density.density)
            } catch (e: RuntimeException) {
                Timber.tag("GoogleCurl").w(e, "Wave drawing unavailable; using recorded page layers")
                renderer.close()
                false
            } catch (e: OutOfMemoryError) {
                Timber.tag("GoogleCurl").w(e, "Wave drawing exceeded available memory; using recorded page layers")
                renderer.close()
                false
            } else false
            if (!drawn) {
                // Keep the same commit/cancel transaction when rasterization is unavailable.
                if (direction == ReaderTurnDirection.NEXT) {
                    drawPageSnapshot(targetBitmap, revealLayer, width, height)
                    clipRect(right = width * (1f - p)) {
                        translate(left = -width * p) {
                            drawPageSnapshot(currentBitmap, baseLayer, width, height)
                        }
                    }
                } else {
                    drawPageSnapshot(currentBitmap, baseLayer, width, height)
                    translate(left = width * (p - 1f)) {
                        drawPageSnapshot(targetBitmap, revealLayer, width, height)
                    }
                }
            }
        }
    }
}
