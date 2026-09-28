package io.legado.app.help.coil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import coil3.ImageLoader
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.Flow
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.coil3.coil
import me.saket.telephoto.zoomable.copy
import okio.Path.Companion.toOkioPath

/** 保留 Coil 的格式判断与预览选择，仅补齐原图的读取租约和 EInk 整图边界。 */
@Composable
fun rememberMangaZoomableImageSource(
    request: ImageRequest,
    imageLoader: ImageLoader,
    owner: MangaImageFileOwner,
    wholeImage: Boolean,
): ZoomableImageSource {
    val failed by owner.regionDecodeFailed.collectAsState()
    val usePainter = wholeImage || failed
    return key(owner, usePainter) {
        val delegate = if (usePainter) {
            val painter = rememberAsyncImagePainter(request, imageLoader)
            remember(painter) { PainterImageSource(painter) }
        } else ZoomableImageSource.coil(request, imageLoader)
        remember(delegate, owner, usePainter) {
            if (usePainter) delegate else LeasedImageSource(delegate, owner)
        }
    }
}

@Stable
private class PainterImageSource(private val painter: androidx.compose.ui.graphics.painter.Painter) : ZoomableImageSource {
    @Composable
    override fun resolve(canvasSize: Flow<Size>) =
        ZoomableImageSource.ResolveResult(ZoomableImageSource.PainterDelegate(painter))
}

@Stable
private class LeasedImageSource(
    private val delegate: ZoomableImageSource,
    private val owner: MangaImageFileOwner,
) : ZoomableImageSource {
    @Composable
    override fun resolve(canvasSize: Flow<Size>): ZoomableImageSource.ResolveResult {
        val resolved = delegate.resolve(canvasSize)
        val tiles = resolved.delegate as? ZoomableImageSource.SubSamplingDelegate ?: return resolved
        val recovered = remember(tiles, owner) {
            RememberedTileSource(ZoomableImageSource.SubSamplingDelegate(
                recoveringMangaTileSource(tiles.source, owner),
                tiles.imageOptions,
            ))
        }
        return resolved.copy(delegate = recovered.delegate)
    }
}

/** 本地/content source 不需要在线租约，也必须覆盖区域解码失败。 */
internal fun recoveringMangaTileSource(
    source: SubSamplingImageSource,
    owner: MangaImageFileOwner,
): SubSamplingImageSource {
    val borrowed = owner.borrowForTiles()
    val leased = if (borrowed == null) source else {
        val (file, lease) = borrowed
        SubSamplingImageSource.file(file.toOkioPath(), source.preview) {
            try { source.close() } finally { lease.close() }
        }
    }
    return RecoveringRegionImageSource(leased, owner::onRegionDecodeFailed)
}

/** 正常退出由瓦片解码器关闭；未提交的组合尚无解码器，需要回收已创建的租约。 */
private class RememberedTileSource(val delegate: ZoomableImageSource.SubSamplingDelegate) : RememberObserver {
    override fun onRemembered() = Unit
    override fun onForgotten() = Unit
    override fun onAbandoned() = delegate.source.close()
}
