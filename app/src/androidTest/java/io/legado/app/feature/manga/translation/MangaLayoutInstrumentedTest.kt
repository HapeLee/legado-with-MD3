package io.legado.app.feature.manga.translation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.repository.manga.translation.MangaContainerGeometry
import io.legado.app.data.repository.manga.translation.MangaOverlayTypesetter
import io.legado.app.domain.model.manga.MangaBubbleShape
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MangaLayoutInstrumentedTest {
    @Test fun defaultPatchFitsCompleteDialogueAndNeverChangesProtectedPixels() {
        ActivityScenario.launch(ComponentActivity::class.java).use { host ->
            var artifact: File? = null
            host.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= 27) { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                artifact = File(it.cacheDir, "manga-no-fit-regression.png")
            }
            val width = 180; val height = 100
            val pixels = BooleanArray(width * height) { i ->
                val x = (i % width + .5 - width / 2) / (width / 2 - 2)
                val y = (i / width + .5 - height / 2) / (height / 2 - 2)
                x * x + y * y < 1
            }
            val runs = mutableListOf<Int>()
            var start = -1
            for (i in 0..pixels.size) {
                if (i < pixels.size && pixels[i]) { if (start < 0) start = i }
                else if (start >= 0) { runs += start; runs += i - start; start = -1 }
            }
            val box = MangaTextBox(200f, 250f, 800f, 750f)
            val shape = MangaBubbleShape("test-bubble", box, width, height, runs).also { it.validate() }
            val texts = listOf("这是较长的中文译文需要完整显示在有限的气泡区域内。", "supercalifragilistic", "她说：“")
            for ((index, text) in texts.withIndex()) {
                val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
                try {
                    val region = MangaTranslatedRegion("r0", "SOURCE", text, box, bubbleShape = shape)
                    MangaOverlayTypesetter.draw(bitmap, region, 100f, 1f, true, Color.WHITE)
                    val allowed = MangaContainerGeometry.raster(shape, 300, 200, Rect(0, 0, 300, 200))
                    var dark = 0
                    for (i in allowed.indices) {
                        val color = bitmap.getPixel(i % 300, i / 300)
                        if (!allowed[i]) assertEquals("Protected pixel $i changed", Color.LTGRAY, color)
                        if (Color.red(color) < 100) { dark++; assertTrue(allowed[i]) }
                    }
                    assertTrue("No rendered glyphs for case $index", dark > 0)
                    if (index == 0) artifact!!.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
            }
        }
    }
}
