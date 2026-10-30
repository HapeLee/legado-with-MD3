package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import io.legado.app.domain.model.manga.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaAiFramesTest {
    @Test fun `echoed frames must retain supplied width height and identity while legacy replies remain usable`() {
        val frame = MangaAiFrame("page", 800, 1200)
        validateMangaAiFrame("""{"frame":{"id":"page","width":800,"height":1200}}""", frame)
        validateMangaAiFrame("""{"regions":[]}""", frame)
        for (bad in listOf("""{"id":"page","width":1200,"height":800}""", """{"id":"crop-0","width":800,"height":1200}""")) {
            assertThrows(IllegalArgumentException::class.java) { validateMangaAiFrame("""{"frame":$bad}""", frame) }
        }
    }


}
