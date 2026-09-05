package io.legado.app.core.platform

/**
 * Platform-agnostic decoded image wrapper.
 *
 * On Android, wraps [android.graphics.Bitmap]; on Desktop, wraps
 * [java.awt.image.BufferedImage]. Consumers access dimensions via [width]
 * and [height]; platform-specific accessors are provided by the actuals.
 */
expect class DecodedImage {
    val width: Int
    val height: Int
}

/**
 * Decodes raw image bytes (PNG, JPEG, WebP, etc.) into a [DecodedImage].
 *
 * This is the **format decode** step — it does NOT handle source-specific
 * image decryption (that is [io.legado.app.utils.ImageUtils.decode]).
 * The contract exists so that future commonMain code can decode images
 * without depending on coil3 or platform bitmap APIs directly.
 */
interface ImageDecoder {

    /**
     * Decode [bytes] into a [DecodedImage], or null if the bytes are not
     * a recognizable image format or decoding fails.
     */
    fun decode(bytes: ByteArray): DecodedImage?
}
