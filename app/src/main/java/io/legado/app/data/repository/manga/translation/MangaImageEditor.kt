package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import io.legado.app.data.repository.ai.AiImageRepository
import io.legado.app.domain.gateway.AiImageGateway
import io.legado.app.domain.model.AiImageInput
import io.legado.app.domain.model.AiImageModels
import io.legado.app.domain.model.AiImageRequest
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Preserve reader geometry while the shared image gateway owns native provider protocols. */
internal class MangaImageEditor(private val gateway: AiImageGateway = AiImageRepository()) {

    suspend fun edit(input: Bitmap, model: AiModelConfig, path: String, prompt: String): Bitmap {
        // A square canvas gives the image service a supported aspect ratio. Padding is removed
        // afterwards; page dimensions and the reader's geometry never depend on model output size.
        val side = max(input.width, input.height)
        val square = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val scale = 1024f / side
        Canvas(square).apply {
            drawColor(Color.WHITE)
            scale(scale, scale)
            drawBitmap(input, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val png = try { ByteArrayOutputStream().also { square.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { square.recycle() }
        val protocol = AiImageModels.editProtocol(model.provider.protocol)
        // Old per-book settings stored OpenAI's default path. Native providers use their own default.
        val endpoint = path.takeIf { it.isNotBlank() && (it != "/images/edits" || protocol == AiProtocol.OPENAI_IMAGES) }
        val bytes = gateway.generate(AiImageRequest(model,
            "Edit only the comic in the top-left ${input.width} by ${input.height} portion of an original $side by $side square canvas. Preserve the square framing and padding. $prompt",
            AiImageInput("image/png", Base64.encodeToString(png, Base64.NO_WRAP)), endpoint)).bytes
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..4096 && options.outHeight in 1..4096 && kotlin.math.abs(options.outWidth.toFloat() / options.outHeight - 1) < 0.02f) {
            "Image editor changed the canvas aspect ratio"
        }
        val edited = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Cannot decode edited image")
        return try {
            Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).also { output ->
                Canvas(output).drawBitmap(edited,
                    Rect(0, 0, (input.width.toFloat() / side * edited.width).toInt().coerceAtLeast(1), (input.height.toFloat() / side * edited.height).toInt().coerceAtLeast(1)),
                    Rect(0, 0, input.width, input.height), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } finally { edited.recycle() }
    }

}
