package io.legado.app.core.platform

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class DesktopImageDecoderContractTest : ImageDecoderContractTest() {
    override fun createDecoder(): ImageDecoder = DesktopImageDecoder()

    override val validPng: ByteArray by lazy {
        val img = BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color.RED
        g.fillRect(0, 0, 2, 3)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "PNG", out)
        out.toByteArray()
    }
    override val expectedWidth = 2
    override val expectedHeight = 3
}
