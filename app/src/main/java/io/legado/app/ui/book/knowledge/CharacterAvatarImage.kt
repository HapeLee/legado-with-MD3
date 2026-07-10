package io.legado.app.ui.book.knowledge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

data class CharacterAvatarCrop(
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
    val viewportSize: Float,
)

fun saveCharacterAvatar(
    context: Context,
    sourceUri: Uri,
    crop: CharacterAvatarCrop,
    outputSize: Int = 512,
): String {
    val bitmap = decodeCharacterAvatarBitmap(context, sourceUri)
    val viewportSize = crop.viewportSize.coerceAtLeast(1f)
    val baseScale = max(
        viewportSize / bitmap.width.toFloat(),
        viewportSize / bitmap.height.toFloat(),
    )
    val totalScale = baseScale * crop.zoom.coerceAtLeast(1f)
    val imageLeft = (viewportSize - bitmap.width * totalScale) / 2f + crop.offsetX
    val imageTop = (viewportSize - bitmap.height * totalScale) / 2f + crop.offsetY
    val sourceRect = RectF(
        (0f - imageLeft) / totalScale,
        (0f - imageTop) / totalScale,
        (viewportSize - imageLeft) / totalScale,
        (viewportSize - imageTop) / totalScale,
    )
    sourceRect.offset(
        sourceRect.left.coerceIn(0f, bitmap.width - sourceRect.width()) - sourceRect.left,
        sourceRect.top.coerceIn(0f, bitmap.height - sourceRect.height()) - sourceRect.top,
    )

    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(
        bitmap,
        Rect(
            sourceRect.left.toInt(),
            sourceRect.top.toInt(),
            sourceRect.right.toInt(),
            sourceRect.bottom.toInt(),
        ),
        RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat()),
        null,
    )

    val dir = File(context.filesDir, "character_avatars").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    file.outputStream().use { stream ->
        output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    }
    if (output !== bitmap) {
        output.recycle()
    }
    bitmap.recycle()
    return file.toUri().toString()
}

fun deleteCharacterAvatar(context: Context, avatarUri: String?) {
    if (avatarUri.isNullOrBlank()) return
    val avatarDir = File(context.filesDir, "character_avatars").canonicalFile
    val file = runCatching {
        File(Uri.parse(avatarUri).path.orEmpty()).canonicalFile
    }.getOrNull() ?: return
    if (file.parentFile == avatarDir) {
        file.delete()
    }
}

fun decodeCharacterAvatarBitmap(
    context: Context,
    uri: Uri,
    maxSideSize: Int = 2048,
): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    val maxSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    val sampleSize = ceil(maxSide / maxSideSize.toFloat()).toInt().coerceAtLeast(1)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: error("Failed to decode avatar image")
}
