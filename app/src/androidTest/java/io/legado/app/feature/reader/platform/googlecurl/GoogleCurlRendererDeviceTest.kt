package io.legado.app.feature.reader.platform.googlecurl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import io.legado.app.feature.reader.core.transition.googlecurl.GoogleCurlGeometry
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android's actual bitmap-mesh rasterizer, including the reverse incoming-sheet wave. */
@RunWith(AndroidJUnit4::class)
class GoogleCurlRendererDeviceTest {
    @Test fun shadowFadesThroughoutTheTurnAndRetracesInBothDirections() {
        var normalDensity: List<Int>? = null
        for (density in listOf(1, 3)) {
            val width = 320 * density
            val height = 480 * density
            val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.eraseColor(Color.WHITE)
            try {
                val profiles = ReaderTurnDirection.entries.map { direction ->
                    GoogleCurlRenderer().use { renderer ->
                        renderer.prepare(page, page)
                        val canvas = Canvas(output)
                        val geometry = GoogleCurlGeometry(.08f)
                        val point = FloatArray(2)
                        fun darkness(sheetProgress: Float): Int {
                            val progress = if (direction == ReaderTurnDirection.NEXT) sheetProgress else 1f - sheetProgress
                            assertTrue(renderer.draw(canvas, direction, progress,
                                width.toFloat(), height.toFloat(), density.toFloat()))
                            geometry.update(sheetProgress)
                            geometry.project(1f, .5f, width.toFloat(), height.toFloat(), point, 0)
                            // Four dp outside the rightmost mesh vertex: only cast shadow, no paper shading.
                            val x = (point[0] + 4 * density).roundToInt()
                            assertTrue(x in 0 until width)
                            assertEquals(Color.WHITE, output.getPixel(width - 1, height / 2))
                            return 255 - Color.red(output.getPixel(x, height / 2))
                        }
                        val profile = listOf(.25f, .5f, .75f, .95f).map(::darkness)
                        profile.zipWithNext().forEach { (earlier, later) ->
                            assertTrue("Shadow must progressively fade: $direction / density=$density / $profile",
                                earlier > later + 3)
                        }
                        // Reversing a drag must restore the same shadow at the same paper position.
                        assertEquals(profile[1], darkness(.5f))
                        profile
                    }
                }
                profiles[0].zip(profiles[1]).forEach { (previous, next) ->
                    assertEquals("The same visible paper must cast the same shadow in both directions",
                        previous.toDouble(), next.toDouble(), 1.0)
                }
                normalDensity?.zip(profiles[0])?.forEach { (normal, scaled) ->
                    assertEquals("Shadow strength must not depend on display density",
                        normal.toDouble(), scaled.toDouble(), 2.0)
                }
                normalDensity = profiles[0]
            } finally {
                page.recycle()
                output.recycle()
            }
        }
    }

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
