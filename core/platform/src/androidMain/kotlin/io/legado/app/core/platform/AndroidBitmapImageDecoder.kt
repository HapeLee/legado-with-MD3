package io.legado.app.core.platform

import android.graphics.BitmapFactory

actual class DecodedImage(
    val bitmap: android.graphics.Bitmap,
) {
    actual val width: Int get() = bitmap.width
    actual val height: Int get() = bitmap.height
}

/**
 * Android [ImageDecoder] backed by [BitmapFactory.decodeByteArray].
 */
class AndroidBitmapImageDecoder : ImageDecoder {
    override fun decode(bytes: ByteArray): DecodedImage? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return bitmap?.let { DecodedImage(it) }
    }
}
