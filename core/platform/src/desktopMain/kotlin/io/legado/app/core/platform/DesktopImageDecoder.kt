package io.legado.app.core.platform

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual class DecodedImage(
    val image: BufferedImage,
) {
    actual val width: Int get() = image.width
    actual val height: Int get() = image.height
}

/**
 * Desktop [ImageDecoder] backed by [ImageIO.read].
 */
class DesktopImageDecoder : ImageDecoder {
    override fun decode(bytes: ByteArray): DecodedImage? {
        return runCatching {
            ImageIO.read(ByteArrayInputStream(bytes))?.let { DecodedImage(it) }
        }.getOrNull()
    }
}
