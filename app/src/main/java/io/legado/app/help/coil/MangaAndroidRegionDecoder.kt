package io.legado.app.help.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import io.legado.app.domain.reader.MangaImageOrientation
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.reader.MangaImageSize
import io.legado.app.domain.reader.MangaRegionDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Android-only original-image access. A source owns its lease, independently of the request. */
internal class MangaAndroidRegionSource private constructor(
    private val input: () -> InputStream,
    private val lease: Closeable,
) : Closeable {
    private val closed = AtomicBoolean()

    // The caller acquires this resource in NonCancellable and closes it in finally.
    suspend fun open(): MangaRegionDecoder<Bitmap> = withContext(Dispatchers.IO) {
        check(!closed.get()) { "Image source is closed" }
        val orientation = try {
            input().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        } catch (_: IOException) {
            1
        }
        val native = input().use {
            if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(it)
            else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(it, false)
            }
        } ?: throw IOException("Image does not support region decoding")
        try {
            MangaAndroidRegionDecoder(object : MangaAndroidRegionBackend {
                override val size = MangaImageSize(native.width, native.height)
                override fun decode(region: MangaImageRegion, sampleSize: Int): Bitmap =
                    native.decodeRegion(
                        Rect(region.left, region.top, region.right, region.bottom),
                        BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }) ?: throw IOException("Region decoding returned no bitmap")

                override fun close() = native.recycle()
            }, orientation)
        } catch (error: Throwable) {
            native.recycle()
            throw error
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) lease.close()
    }

    companion object {
        fun file(file: File, lease: Closeable = Closeable {}) =
            MangaAndroidRegionSource(file::inputStream, lease)

        fun uri(context: Context, uri: Uri): MangaAndroidRegionSource? = when (uri.scheme) {
            "file" -> uri.path?.let { path ->
                if (path.startsWith("/android_asset/")) MangaAndroidRegionSource({
                    context.assets.open(path.removePrefix("/android_asset/"))
                }, Closeable {}) else file(File(path))
            }

            null -> uri.path?.takeIf { it.startsWith('/') }?.let { file(File(it)) }
            "content", "android.resource" -> MangaAndroidRegionSource({
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open image URI")
            }, Closeable {})

            else -> null
        }
    }
}

/** The native seam is injectable for lifecycle/EXIF tests without desktop JNI region decoding. */
internal interface MangaAndroidRegionBackend : Closeable {
    val size: MangaImageSize
    fun decode(region: MangaImageRegion, sampleSize: Int): Bitmap
}

internal class MangaAndroidRegionDecoder(
    private val backend: MangaAndroidRegionBackend,
    private val orientationValue: Int,
) : MangaRegionDecoder<Bitmap> {
    private val orientation = MangaImageOrientation(orientationValue)
    private val lock = Any()
    private var closed = false
    override val imageSize = orientation.displayedSize(backend.size)

    override suspend fun decodeRegion(region: MangaImageRegion, sampleSize: Int): Bitmap =
        withContext(Dispatchers.IO) {
            // Cancellation cannot release the native decoder while decodeRegion is running.
            synchronized(lock) {
                check(!closed) { "Image decoder is closed" }
                require(sampleSize > 0 && sampleSize.countOneBits() == 1)
                require(
                    region.left >= 0 && region.top >= 0 && region.right <= imageSize.width &&
                            region.bottom <= imageSize.height && region.width > 0 && region.height > 0
                )
                val raw = backend.decode(orientation.rawRegion(region, backend.size), sampleSize)
                orientMangaRegion(raw, orientationValue).also { it.prepareToDraw() }
            }
        }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            backend.close()
        }
    }
}

/** Reorient only the bounded tile. The returned bitmap belongs to the renderer, not the decoder. */
internal fun orientMangaRegion(raw: Bitmap, orientation: Int): Bitmap {
    if (orientation !in 2..8) return raw
    val values = when (orientation) {
        2 -> floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        3 -> floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
        4 -> floatArrayOf(1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
        5 -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        6 -> floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        7 -> floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        else -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
    }
    try {
        val result = Bitmap.createBitmap(
            raw, 0, 0, raw.width, raw.height,
            Matrix().apply { setValues(values) }, false
        )
        if (result !== raw) raw.recycle()
        return result
    } catch (error: Throwable) {
        raw.recycle()
        throw error
    }
}
