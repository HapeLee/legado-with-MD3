package io.legado.app.help.coil

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** 保留预览至整图接管；区域解码失败不删除原图，也不触发重新下载。 */
internal class RecoveringRegionImageSource(
    private val source: SubSamplingImageSource,
    private val onFailure: (Exception) -> Unit,
) : SubSamplingImageSource by source {
    private val failed = AtomicBoolean()

    private fun recover(error: Exception) {
        if (error is CancellationException) throw error
        if (error !is IOException && error !is RuntimeException) throw error
        if (failed.compareAndSet(false, true)) onFailure(error)
    }

    private fun transparent() = ImageRegionDecoder.DecodeResult(ColorPainter(Color.Transparent), false)

    override suspend fun decoder(): ImageRegionDecoder.Factory = ImageRegionDecoder.Factory { params ->
        val decoder = try {
            source.decoder().create(params)
        } catch (error: Exception) {
            recover(error)
            return@Factory object : ImageRegionDecoder {
                override val imageSize = source.preview?.let { IntSize(it.width, it.height) } ?: IntSize(1, 1)
                override suspend fun decodeRegion(region: IntRect, sampleSize: Int) = transparent()
            }
        }
        object : ImageRegionDecoder by decoder {
            override suspend fun decodeRegion(region: IntRect, sampleSize: Int): ImageRegionDecoder.DecodeResult {
                if (failed.get()) return transparent()
                return try {
                    decoder.decodeRegion(region, sampleSize)
                } catch (error: Exception) {
                    recover(error)
                    transparent()
                }
            }
        }
    }
}
