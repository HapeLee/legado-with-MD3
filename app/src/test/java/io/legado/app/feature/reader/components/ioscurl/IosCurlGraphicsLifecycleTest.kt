package io.legado.app.feature.reader.components.ioscurl

import android.app.Application
import android.graphics.Bitmap
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The retained holder outlives a turn and the TextureView callbacks. Once the composition disposes
 * it, a late capture or surface callback must not recreate a GL worker that nobody will close.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class IosCurlGraphicsLifecycleTest {

    @Test
    fun prepareAfterDisposeDoesNotResurrectGlResources() {
        val graphics = IosCurlGraphics()
        graphics.close()

        val page = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        graphics.prepare(page, page, opaquePageBackground = false)

        assertNull("renderer", graphics.field("renderer"))
        assertNull("output", graphics.field("output"))
        assertNull("canvasRenderer", graphics.field("canvasRenderer"))
    }

    @Test
    fun disposeIsIdempotent() {
        val graphics = IosCurlGraphics()
        graphics.close()
        graphics.close()
        assertNull(graphics.field("renderer"))
    }

    private fun IosCurlGraphics.field(name: String): Any? =
        IosCurlGraphics::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .get(this)
}
