package io.legado.app.feature.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.repository.manga.translation.MangaOcrModelRepository
import io.legado.app.data.repository.manga.translation.MangaPaddleOcr
import io.legado.app.domain.model.manga.MangaOcrModelPhase
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaOcrOptions
import io.legado.app.domain.model.manga.MangaOcrDetectionProfile
import io.legado.app.data.repository.manga.translation.MangaPaddleModelCatalog
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in for a selected model set, or hash-checked local fixtures. No paid AI calls. */
@RunWith(AndroidJUnit4::class)
class MangaOcrInstrumentedTest {
    @Test fun optionalModelDownloadsRecognizesAndReleasesFiles() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val fixtures = args.getString("mangaPaddleFixtures")?.let(::File)
        assumeTrue(args.getString("mangaDownloadPaddle") == "true" || fixtures != null)
        val engine = args.getString("mangaPaddleEngine")?.let(MangaLocalOcrEngine::valueOf) ?: MangaLocalOcrEngine.PADDLE_V5
        val app = ApplicationProvider.getApplicationContext<Application>()
        val root = File(app.cacheDir, "manga-ocr-instrumentation-${UUID.randomUUID()}")
        val models = if (fixtures == null) MangaOcrModelRepository(app, root) else MangaOcrModelRepository(app, root, fetch = { spec, target, progress ->
            File(fixtures, spec.name).inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            progress(target.length())
        })
        val awake = app.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Legado:manga-ocr-test")
        awake.acquire(120_000)
        try { ActivityScenario.launch(ComponentActivity::class.java).use { host ->
            host.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= 27) { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            }
            try {
                models.download(engine)
                assertEquals(MangaOcrModelPhase.READY, models.state.value.getValue(engine).phase)
                data class Case(val text: String, val language: String, val angle: Float)
                val cases = when (engine) {
                    MangaLocalOcrEngine.PADDLE_V6_MANGA -> listOf(Case("こんにちは", "ja", 0f), Case("こんにちは", "ja", 12f))
                    MangaLocalOcrEngine.PADDLE_V6_TINY -> listOf(Case("HELLO WORLD", "en", 0f), Case("你好世界", "zh", 0f))
                    MangaLocalOcrEngine.PADDLE_V5_KOREAN -> listOf(Case("안녕하세요", "ko", 0f), Case("HELLO WORLD", "en", 0f))
                    MangaLocalOcrEngine.PADDLE_V5_ESLAV -> listOf(Case("Привет мир", "ru", 0f), Case("Привіт світ", "uk", 0f), Case("HELLO WORLD", "en", 0f))
                    else -> listOf(Case("HELLO WORLD", "en", 0f), Case("こんにちは", "ja", 0f), Case("HELLO WORLD", "en", 12f)) +
                        if (engine != MangaLocalOcrEngine.PADDLE_V5) listOf(Case("你好世界", "zh", 0f)) else emptyList()
                }
                for (case in cases) {
                    val image = Bitmap.createBitmap(800, 320, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(image).apply {
                            drawColor(Color.WHITE)
                            rotate(case.angle, 400f, 160f)
                            drawText(case.text, 70f, 170f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 60f })
                        }
                        val started = SystemClock.elapsedRealtime()
                        val regions = MangaPaddleOcr(models).detect(image, case.language, engine)
                        val text = regions.joinToString(" ") { it.original }
                        Log.i("MangaOcrTest", "engine=$engine language=${case.language} angle=${case.angle} ms=${SystemClock.elapsedRealtime() - started} text=$text")
                        assertEquals("Native OCR ${case.language}/${case.angle}", case.text, text)
                        assertTrue(regions.flatMap { it.sourceLines }.all { it.quad != null })
                        if (case.angle != 0f) assertTrue(regions.flatMap { it.sourceLines }.any { line ->
                            val p = line.quad!!.points
                            kotlin.math.abs(p[1].y - p[0].y) > 20
                        })
                    } finally { image.recycle() }
                }
                if (args.getString("mangaAdvancedOcr") == "true") {
                    val image = Bitmap.createBitmap(1800, 900, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(image).apply {
                            drawColor(Color.WHITE)
                            drawText("HELLO WORLD", 250f, 450f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f })
                        }
                        val before = IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }.contentHashCode()
                        val runtime = MangaPaddleOcr(models)
                        val base = runtime.detect(image, "en", engine)
                        val options = MangaOcrOptions(detectionProfile = MangaOcrDetectionProfile.ACCURATE,
                            probabilityThreshold = .25f, boxScoreThreshold = .5f, unclipRatio = 1.8f, upscale2x = true, binarize = true)
                        val enhanced = runtime.detect(image, "en", engine, options)
                        assertEquals("HELLO WORLD", enhanced.joinToString(" ") { it.original })
                        assertEquals(base.minOf { it.textBox.left }, enhanced.minOf { it.textBox.left }, 20f)
                        assertEquals(base.maxOf { it.textBox.right }, enhanced.maxOf { it.textBox.right }, 20f)
                        assertTrue(enhanced.flatMap { it.sourceLines }.all { it.quad != null })
                        assertTrue(runtime.detect(image, "en", engine, options.copy(boxScoreThreshold = 1f)).isEmpty())
                        val after = IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }.contentHashCode()
                        assertEquals(before, after)
                        Log.i("MangaOcrTest", "advanced profile=ACCURATE upsample=true otsu=true customThresholds=true sourceUnchanged=true")
                    } finally { image.recycle() }
                }
            } finally {
                withContext(NonCancellable) { models.delete(engine); File(root, MangaPaddleModelCatalog.directory(engine)).delete(); root.delete() }
            }
        } } finally { if (awake.isHeld) awake.release() }
    }
}
