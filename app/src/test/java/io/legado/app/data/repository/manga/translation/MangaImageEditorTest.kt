package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.legado.app.domain.gateway.AiImageGateway
import io.legado.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaImageEditorTest {
    private val model = AiModelConfig("model", AiProviderConfig("provider", "Seedream", AiProtocol.SEEDREAM_IMAGES,
        "https://example.invalid/v3", "unused"), "Image", "custom-edit", capabilities = setOf(AiCapability.IMAGE_EDITING))
    private fun png(bitmap: Bitmap) = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    @Test fun `native image gateway output is unpadded and restored to reader geometry`() = runBlocking {
        val source = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val generated = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.YELLOW)
            Canvas(this).drawRect(0f, 0f, 1024f, 2048f, Paint().apply { color = Color.GREEN })
        }
        val bytes = png(generated); generated.recycle()
        var calls = 0
        val editor = MangaImageEditor(object : AiImageGateway {
            override suspend fun generate(request: AiImageRequest): AiImageResult {
                calls++
                assertNull(request.endpointPath) // Do not send the saved OpenAI default to Seedream.
                assertNotNull(request.image)
                return AiImageResult(bytes)
            }
        })
        try {
            val result = editor.edit(source, model, "/images/edits", "translate")
            try {
                assertEquals(64, result.width); assertEquals(128, result.height)
                assertEquals(Color.GREEN, result.getPixel(60, 100))
                assertEquals(Color.BLUE, source.getPixel(10, 10))
                assertEquals(1, calls)
            } finally { result.recycle() }
        } finally { source.recycle() }
    }

    @Test fun `service canvas aspect changes are rejected before composition`() = runBlocking {
        val source = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
        val wrong = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val bytes = png(wrong); wrong.recycle()
        val editor = MangaImageEditor(object : AiImageGateway {
            override suspend fun generate(request: AiImageRequest) = AiImageResult(bytes)
        })
        try {
            try { editor.edit(source, model, "", "translate"); fail("Expected aspect validation") }
            catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("aspect ratio")) }
        } finally { source.recycle() }
    }
}
