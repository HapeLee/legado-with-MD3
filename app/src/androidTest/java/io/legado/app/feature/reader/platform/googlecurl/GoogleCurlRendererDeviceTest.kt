package io.legado.app.feature.reader.platform.googlecurl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android's actual bitmap-mesh rasterizer, including the reverse incoming-sheet wave. */
@RunWith(AndroidJUnit4::class)
class GoogleCurlRendererDeviceTest {
    @Test fun endpointsMidWaveAndCancellationAcrossPortraitAndLandscape() {
        for ((width, height) in listOf(320 to 480, 480 to 320)) {
            val current = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            current.eraseColor(Color.rgb(240, 190, 70))
            target.eraseColor(Color.rgb(30, 80, 220))
            try {
                for (direction in ReaderTurnDirection.entries) {
                    GoogleCurlRenderer().use { renderer ->
                        renderer.prepare(current, target)
                        val canvas = Canvas(output)
                        fun draw(progress: Float) = renderer.draw(canvas, direction, progress,
                            width.toFloat(), height.toFloat(), 1f)
                        assertTrue(draw(0f))
                        assertTrue(output.sameAs(current))
                        assertTrue(draw(.5f))
                        var orange = 0
                        var blue = 0
                        for (y in 0 until height step 8) {
                            for (x in 0 until width step 8) {
                                val color = output.getPixel(x, y)
                                assertEquals(255, Color.alpha(color))
                                if (Color.red(color) > Color.blue(color)) orange++ else blue++
                            }
                        }
                        assertTrue("both pages must be visible at mid-wave", orange > 0 && blue > 0)
                        assertTrue(draw(0f))
                        assertTrue("cancel returns exactly to the outgoing page", output.sameAs(current))
                        assertTrue(draw(1f))
                        assertTrue("completion shows exactly the target", output.sameAs(target))
                        renderer.close()
                        assertFalse(draw(.5f))
                    }
                }
            } finally {
                current.recycle()
                target.recycle()
                output.recycle()
            }
        }
    }
}
