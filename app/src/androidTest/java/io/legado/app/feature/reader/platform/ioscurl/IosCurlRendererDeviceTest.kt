package io.legado.app.feature.reader.platform.ioscurl

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.EGL14
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.feature.reader.core.transition.ioscurl.control.CurlControl
import io.legado.app.feature.reader.core.transition.ioscurl.layout.CurlLayout
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMesh
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMeshInput
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlConfig
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlDirection
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlKind
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual GLES shader compilation, mesh rasterization, readback and context restoration. */
@RunWith(AndroidJUnit4::class)
class IosCurlRendererDeviceTest {
    @Test fun bothDirectionsRevealTheTargetAndRestoreTheCallingContext() {
        for ((width, height) in listOf(320 to 480, 480 to 320)) {
            for (direction in CurlDirection.entries) {
                val current = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                current.eraseColor(Color.rgb(240, 190, 70))
                target.eraseColor(Color.rgb(30, 80, 220))
                val previousContext = EGL14.eglGetCurrentContext()
                try {
                    IosCurlRenderer().use { renderer ->
                        renderer.upload(current, target)
                        renderer.bindOutput(output)
                        val c = CurlConfig(width.toDouble(), height.toDouble(),
                            direction = direction, kind = CurlKind.Automatic)
                        val layout = CurlLayout.build(c)
                        val texture = if (layout.passes.single().source.nativeValue < 2) 0 else 1
                        val mesh = CurlMesh()
                        fun render(values: CurlValues) {
                            renderer.render(mesh.build(CurlMeshInput(values, c.width, c.height)),
                                width.toFloat(), height.toFloat(), texture, 1 - texture,
                                layout.underlays.map { (if (it.source.nativeValue < 2) 0 else 1) to it.frame })
                            assertEquals(previousContext, EGL14.eglGetCurrentContext())
                        }
                        val begin = CurlControl.begin(c)
                        render(begin.from)
                        val start = output.getPixel(width / 2, height / 2)
                        assertTrue("start must show the outgoing sheet", Color.red(start) > Color.blue(start))
                        render(CurlValues(.5, width * .12, .2))
                        assertTrue("mid-curl must produce visible pixels", Color.alpha(output.getPixel(width / 2, height / 2)) > 0)
                        render(begin.to)
                        val end = output.getPixel(width / 2, height / 2)
                        assertTrue("finish must show the incoming sheet", Color.blue(end) > Color.red(end))
                    }
                    assertEquals(previousContext, EGL14.eglGetCurrentContext())
                } finally {
                    current.recycle()
                    target.recycle()
                    output.recycle()
                }
            }
        }
    }
}
