package io.legado.app.help.coil

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import coil3.BitmapImage
import coil3.request.SuccessResult
import coil3.request.crossfadeMillis
import coil3.toAndroidUri
import coil3.transform.Transformation
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.reader.MangaRegionDecoder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.floor

/** 原图区域与用于消除双线性采样接缝的边缘像素；单块解码不超过 1026 × 1026。 */
@Stable
internal data class MangaWebtoonTile(
    val bounds: IntRect,
    val decodeBounds: IntRect,
    val sampleSize: Int,
)

/** 只选择实际视口及前后一行；长图总高度不会增加活动位图数量。 */
internal fun mangaWebtoonTiles(
    sourceSize: IntSize,
    layoutSize: IntSize,
    position: Offset,
    viewport: Rect,
): ImmutableList<MangaWebtoonTile> {
    if (sourceSize.width <= 0 || sourceSize.height <= 0 ||
        layoutSize.width <= 0 || layoutSize.height <= 0 || viewport.isEmpty
    ) return persistentListOf()
    val visible = viewport.translate(-position).intersect(
        Rect(0f, 0f, layoutSize.width.toFloat(), layoutSize.height.toFloat()),
    )
    if (visible.isEmpty) return persistentListOf()
    val ratioX = sourceSize.width.toDouble() / layoutSize.width
    val ratioY = sourceSize.height.toDouble() / layoutSize.height
    var sample = 1
    while (sample * 2.0 <= minOf(ratioX, ratioY)) sample *= 2
    val edge = 1024 * sample
    val left = floor(visible.left * ratioX / edge).toInt().coerceAtLeast(0)
    val right = ceil(visible.right * ratioX / edge).toInt()
        .coerceAtMost(ceil(sourceSize.width.toDouble() / edge).toInt())
    val top = (floor(visible.top * ratioY / edge).toInt() - 1).coerceAtLeast(0)
    val bottom = (ceil(visible.bottom * ratioY / edge).toInt() + 1)
        .coerceAtMost(ceil(sourceSize.height.toDouble() / edge).toInt())
    return buildList {
        // 可见块优先于前后预热块。
        for (y in top until bottom) for (x in left until right) {
            val bounds = IntRect(
                x * edge, y * edge,
                minOf((x + 1) * edge, sourceSize.width), minOf((y + 1) * edge, sourceSize.height)
            )
            add(
                MangaWebtoonTile(
                    bounds, IntRect(
                        (bounds.left - sample).coerceAtLeast(0),
                        (bounds.top - sample).coerceAtLeast(0),
                        (bounds.right + sample).coerceAtMost(sourceSize.width),
                        (bounds.bottom + sample).coerceAtMost(sourceSize.height),
                    ), sample
                )
            )
        }
    }.sortedBy { tile ->
        val centerY = (tile.bounds.top + tile.bounds.bottom) / 2.0
        kotlin.math.abs(centerY - visible.center.y * ratioY)
    }.toImmutableList()
}

/** 滑动复用重叠块，离开窗口即释放；取消不发布迟到结果，也不触发错误降级。 */
internal suspend fun loadMangaWebtoonTiles(
    plans: Flow<ImmutableList<MangaWebtoonTile>>,
    decode: suspend (MangaWebtoonTile) -> Painter,
    publish: (ImmutableMap<MangaWebtoonTile, Painter>) -> Unit,
) {
    val cached = mutableMapOf<MangaWebtoonTile, Painter>()
    plans.distinctUntilChanged().collectLatest { plan ->
        cached.keys.retainAll(plan.toSet())
        publish(cached.toImmutableMap())
        for (tile in plan) {
            if (tile in cached) continue
            val painter = decode(tile)
            currentCoroutineContext().ensureActive()
            cached[tile] = painter
            publish(cached.toImmutableMap())
        }
    }
}

/**
 * 条漫的高清绘制层。Coil 整图仅作为有界预览；直接画原图分块，不创建整页缩放图层。
 * 外部 LazyColumn 继续拥有滚动/缩放，原图租约覆盖解码器的整个使用期。
 */
@Composable
internal fun MangaWebtoonTiles(
    result: SuccessResult?,
    owner: MangaImageFileOwner,
    layoutSize: IntSize,
    position: Offset,
    viewport: Rect,
    colorFilter: ColorFilter?,
    backgroundColor: Color,
    transformations: ImmutableList<Transformation>,
) {
    val bitmap = (result?.image as? BitmapImage)?.bitmap ?: return
    // 原尺寸已满足显示的短图沿用 AsyncImage；长图和放大后的低分辨率预览补区域细节。
    if (layoutSize.height <= 4096 && bitmap.width >= layoutSize.width) return
    val context = LocalContext.current
    val currentLayout by rememberUpdatedState(layoutSize)
    val currentPosition by rememberUpdatedState(position)
    val currentViewport by rememberUpdatedState(viewport)
    var sourceSize by remember(result) { mutableStateOf(IntSize.Zero) }
    var tiles by remember(result) {
        mutableStateOf<ImmutableMap<MangaWebtoonTile, Painter>>(persistentMapOf())
    }
    var hasLoadedTiles by remember(result) { mutableStateOf(false) }
    val tileAlpha by animateFloatAsState(
        targetValue = if (hasLoadedTiles) 1f else 0f,
        animationSpec = tween(result.request.crossfadeMillis),
        label = "Webtoon tile fade",
    )
    LaunchedEffect(result, owner, transformations) {
        var source: MangaAndroidRegionSource? = null
        var decoder: MangaRegionDecoder<android.graphics.Bitmap>? = null
        try {
            // 在线预览命中时 result.request.data 可能是预览 PNG；始终从 owner 借原图。
            source = mangaWebtoonTileSource(result, owner, context)
            val activeSource = source ?: return@LaunchedEffect
            // 获取资源时先接管再响应取消，避免 suspend 返回边界丢失解码器/租约。
            val activeDecoder = withContext(NonCancellable) {
                activeSource.open()
            }
            decoder = activeDecoder
            currentCoroutineContext().ensureActive()
            val imageSize = IntSize(activeDecoder.imageSize.width, activeDecoder.imageSize.height)
            sourceSize = imageSize
            loadMangaWebtoonTiles(
                plans = snapshotFlow {
                    mangaWebtoonTiles(imageSize, currentLayout, currentPosition, currentViewport)
                },
                decode = { tile ->
                    val bounds = tile.decodeBounds
                    val bitmap = activeDecoder.decodeRegion(
                        MangaImageRegion(bounds.left, bounds.top, bounds.right, bounds.bottom),
                        tile.sampleSize,
                    )
                    val decoded = BitmapPainter(bitmap.asImageBitmap())
                    // 缓存预览的结果请求已移除变换；使用原始展示请求的配置。
                    transformMangaWebtoonTile(decoded, tile, transformations)
                },
                publish = {
                    tiles = it
                    if (it.isNotEmpty()) hasLoadedTiles = true
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 格式/设备区域解码失败保留已显示的预览，不重取原图。
            owner.onRegionDecodeFailed(error)
            tiles = persistentMapOf()
        } finally {
            try {
                decoder?.close()
            } finally {
                source?.close()
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        drawMangaWebtoonTiles(sourceSize, tiles, tileAlpha, colorFilter, backgroundColor)
    }
}

/** 与设备 Canvas 相同的浮点边界绘制路径，允许验证缩放后的接缝和滤镜。 */
internal fun DrawScope.drawMangaWebtoonTiles(
    sourceSize: IntSize,
    tiles: ImmutableMap<MangaWebtoonTile, Painter>,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    backgroundColor: Color = Color.Transparent,
) {
    if (sourceSize.width <= 0 || sourceSize.height <= 0) return
    val scaleX = size.width / sourceSize.width
    val scaleY = size.height / sourceSize.height
    tiles.forEach { (tile, painter) ->
        val bounds = tile.bounds
        val decoded = tile.decodeBounds
        // 相邻逻辑边界共用浮点坐标；额外边缘像素防止采样时透出底色。
        clipRect(
            bounds.left * scaleX,
            bounds.top * scaleY,
            bounds.right * scaleX,
            bounds.bottom * scaleY
        ) {
            // 透明 PNG 不能在同一幅预览上叠画两遍；先用阅读背景替换该区域。
            drawRect(backgroundColor, alpha = alpha)
            translate(decoded.left * scaleX, decoded.top * scaleY) {
                with(painter) {
                    draw(
                        Size(decoded.width * scaleX, decoded.height * scaleY),
                        alpha = alpha, colorFilter = colorFilter
                    )
                }
            }
        }
    }
}

/** 在线、文件、content 共用项目管理的 Android 区域解码，不依赖 Telephoto。 */
internal fun mangaWebtoonTileSource(
    result: SuccessResult,
    owner: MangaImageFileOwner,
    context: android.content.Context,
): MangaAndroidRegionSource? {
    owner.borrowForTiles()?.let { (file, lease) ->
        return MangaAndroidRegionSource.file(file, lease)
    }
    val uri = when (val data = result.request.data) {
        is File -> Uri.fromFile(data)
        is Uri -> data
        is coil3.Uri -> data.toAndroidUri()
        is String -> data.toUri()
        else -> return null
    }
    return MangaAndroidRegionSource.uri(context, uri)
}

/** 展示变换作用在原图块上；不让原色块覆盖墨水屏二值化预览。 */
internal suspend fun transformMangaWebtoonTile(
    painter: Painter,
    tile: MangaWebtoonTile,
    transformations: List<Transformation>,
): Painter {
    if (transformations.isEmpty()) return painter
    return withContext(Dispatchers.Default) {
        val width = ceil(tile.decodeBounds.width.toDouble() / tile.sampleSize).toInt()
        val height = ceil(tile.decodeBounds.height.toDouble() / tile.sampleSize).toInt()
        val input = createBitmap(width, height)
        var output = input
        try {
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(input.asImageBitmap()),
                Size(width.toFloat(), height.toFloat())
            ) {
                with(painter) { draw(size) }
            }
            for (transformation in transformations) {
                val previous = output
                output = transformation.transform(previous, coil3.size.Size(width, height))
                if (previous !== input && previous !== output) previous.recycle()
            }
            BitmapPainter(output.asImageBitmap())
        } finally {
            if (output !== input) input.recycle()
        }
    }
}
