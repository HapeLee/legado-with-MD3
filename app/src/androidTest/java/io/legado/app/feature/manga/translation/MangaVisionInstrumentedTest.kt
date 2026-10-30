package io.legado.app.feature.manga.translation

import android.app.Application
import android.graphics.*
import android.os.SystemClock
import android.os.PowerManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.repository.manga.translation.MangaBubbleDetector
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MangaVisionInstrumentedTest {
    @Test fun bundledInt8DetectorRunsColdAndWarmOnCpuWithoutNetwork() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val power = app.getSystemService(PowerManager::class.java)
        val awake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Legado:manga-vision-test")
        val metrics = File(app.cacheDir, "manga-vision-device-timings.txt").apply { writeText("") }
        fun record(value: String) { metrics.appendText(value + "\n"); Log.i("MangaVisionTest", value) }
        awake.acquire(120_000)
        try {
            // Reuse the existing debug UI-test host: reader-like foreground scheduling, no book/UI state.
            ActivityScenario.launch(ComponentActivity::class.java).use { host ->
                host.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= 27) { activity.setShowWhenLocked(true); activity.setTurnScreenOn(true) }
                }
                val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.LTGRAY)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    for (x in listOf(240f, 750f)) {
                        val bounds = RectF(x - 210, 70f, x + 210, 620f)
                        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                        canvas.drawOval(bounds, paint)
                        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Color.BLACK
                        canvas.drawOval(bounds, paint)
                        paint.style = Paint.Style.FILL; paint.textSize = 42f
                        canvas.drawText("HELLO!", x - 95, 260f, paint)
                        canvas.drawText("THIS IS OUR", x - 135, 330f, paint)
                        canvas.drawText("COMIC STORY.", x - 155, 400f, paint)
                    }
                    val started = SystemClock.elapsedRealtime()
                    record("Starting cold detector call; interactive=${power.isInteractive}")
                    val cold = MangaBubbleDetector.detect(app, bitmap)
                    val coldMs = SystemClock.elapsedRealtime() - started
                    val warmStarted = SystemClock.elapsedRealtime()
                    val warm = MangaBubbleDetector.detect(app, bitmap)
                    val warmMs = SystemClock.elapsedRealtime() - warmStarted
                    record("coldMs=$coldMs warmMs=$warmMs bubbles=${cold.count { it.kind == 0 }}")
                    assertTrue("Expected separate physical bubbles: $cold", cold.count { it.kind == 0 } >= 2)
                    assertEquals(cold, warm)
                    // Check that the idle-release path can reopen the same model without losing geometry.
                    delay(31_100)
                    val reopenStarted = SystemClock.elapsedRealtime()
                    val reopened = MangaBubbleDetector.detect(app, bitmap)
                    record("idleReopenMs=${SystemClock.elapsedRealtime() - reopenStarted}")
                    assertEquals(warm, reopened)
                    for (region in cold) {
                        assertTrue(region.bounds.left >= 0 && region.bounds.top >= 0)
                        assertTrue(region.bounds.right <= bitmap.width && region.bounds.bottom <= bitmap.height)
                        assertTrue(region.confidence.isFinite())
                    }
                } finally { bitmap.recycle() }
            }
        } finally { if (awake.isHeld) awake.release() }
    }
}
