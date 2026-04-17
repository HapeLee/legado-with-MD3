package io.legado.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val IMAGE_COLOR_EXTRACT_SIZE_PX = 128
private const val IMAGE_QUANTIZE_BITMAP_MAX_SIZE = 64
private const val IMAGE_MAX_QUANTIZE_COLORS = 64
private const val IMAGE_FALLBACK_SEED_COLOR = 0xFF4285F4.toInt()

suspend fun ImageLoader.extractSeedColor(
    context: Context,
    data: Any,
    configureRequest: ImageRequest.Builder.() -> Unit = {},
): Color? {
    val request = ImageRequest.Builder(context)
        .data(data)
        .allowHardware(false)
        .size(Size(IMAGE_COLOR_EXTRACT_SIZE_PX, IMAGE_COLOR_EXTRACT_SIZE_PX))
        .apply(configureRequest)
        .build()

    val result = withContext(Dispatchers.IO) {
        execute(request)
    } as? SuccessResult ?: return null

    return withContext(Dispatchers.Default) {
        val bitmap = result.drawable.toSafeBitmap(IMAGE_COLOR_EXTRACT_SIZE_PX)
        Color(bitmap.extractSeedColor())
    }
}

@Composable
fun rememberImageSeedColor(
    imageLoader: ImageLoader,
    data: Any?,
    requestKey: Any? = data,
    configureRequest: ImageRequest.Builder.() -> Unit = {},
): Color? {
    val context = LocalContext.current
    val seedColor by produceState<Color?>(initialValue = null, imageLoader, requestKey) {
        value = if (data == null) {
            null
        } else {
            imageLoader.extractSeedColor(
                context = context,
                data = data,
                configureRequest = configureRequest
            )
        }
    }
    return seedColor
}

internal fun Bitmap.extractSeedColor(
    maxColors: Int = IMAGE_MAX_QUANTIZE_COLORS,
    fallbackColorArgb: Int = IMAGE_FALLBACK_SEED_COLOR,
): Int {
    val needsScaling =
        width > IMAGE_QUANTIZE_BITMAP_MAX_SIZE || height > IMAGE_QUANTIZE_BITMAP_MAX_SIZE

    val scaledBitmap = if (needsScaling) {
        val scale = minOf(
            IMAGE_QUANTIZE_BITMAP_MAX_SIZE.toFloat() / width,
            IMAGE_QUANTIZE_BITMAP_MAX_SIZE.toFloat() / height
        )
        scale(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1)
        )
    } else {
        this
    }

    return try {
        val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
        scaledBitmap.getPixels(
            pixels,
            0,
            scaledBitmap.width,
            0,
            0,
            scaledBitmap.width,
            scaledBitmap.height
        )

        val quantized = QuantizerCelebi.quantize(pixels, maxColors)
        Score.score(quantized, 1, fallbackColorArgb, true).first()
    } finally {
        if (scaledBitmap !== this) {
            scaledBitmap.recycle()
        }
    }
}

internal fun Drawable.toSafeBitmap(maxSizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        val rawBitmap = bitmap
        if (rawBitmap.width <= maxSizePx && rawBitmap.height <= maxSizePx) {
            return rawBitmap
        }

        val scale = minOf(
            1f,
            maxSizePx.toFloat() / rawBitmap.width,
            maxSizePx.toFloat() / rawBitmap.height
        )
        return rawBitmap.scale(
            (rawBitmap.width * scale).toInt().coerceAtLeast(1),
            (rawBitmap.height * scale).toInt().coerceAtLeast(1)
        )
    }

    val rawWidth = intrinsicWidth.takeIf { it > 0 } ?: maxSizePx
    val rawHeight = intrinsicHeight.takeIf { it > 0 } ?: maxSizePx
    val scale = minOf(1f, maxSizePx.toFloat() / rawWidth, maxSizePx.toFloat() / rawHeight)

    return toBitmap(
        width = (rawWidth * scale).toInt().coerceAtLeast(1),
        height = (rawHeight * scale).toInt().coerceAtLeast(1)
    )
}
