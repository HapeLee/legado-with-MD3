package io.legado.app.ui.book.knowledge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.io.File
import kotlin.uuid.Uuid
import kotlin.math.ceil
import kotlin.math.max

data class CharacterAvatarCrop(
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
    val viewportSize: Float,
)

suspend fun saveCharacterAvatar(
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
    val file = File(dir, "${Uuid.random()}.jpg")
    file.outputStream().use { stream ->
        output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    }
    if (output !== bitmap) {
        output.recycle()
    }
    // bitmap 不回收：链接头像那一份是 Coil 缓存里的同一个对象，回收它等于把
    // 人物详情/配音页正在显示的那张图抽走（画到就崩）。
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

suspend fun decodeCharacterAvatarBitmap(
    context: Context,
    uri: Uri,
    maxSideSize: Int = 2048,
): Bitmap {
    // 链接头像（人物详情里填的那种）没有流可解，先按封面同一套取图拿回来再裁
    if (uri.scheme == "http" || uri.scheme == "https") {
        return withContext(Dispatchers.IO) {
            runCatching {
                GlobalContext.get().get<ImageLoader>()
                    .execute(
                        ImageRequest.Builder(appCtx)
                            .data(uri)
                            .allowHardware(false)
                            .size(maxSideSize, maxSideSize)
                            .build()
                    )
                    .image?.toBitmap()
            }.getOrNull() ?: error("Failed to decode avatar image")
        }
    }
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
