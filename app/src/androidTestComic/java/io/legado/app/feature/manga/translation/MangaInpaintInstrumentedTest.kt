package io.legado.app.feature.manga.translation

import android.app.Application
import android.content.Intent
import android.graphics.*
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.repository.manga.translation.*
import io.legado.app.domain.model.manga.*
import io.legado.app.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit native calibration. Downloads only with an additional opt-in flag; no AI or book changes. */
@RunWith(AndroidJUnit4::class)
class MangaInpaintInstrumentedTest {
    @Test fun verifyInstalledModelOnMaskedPixels() = runBlocking<Unit> {
        val name = InstrumentationRegistry.getArguments().getString("mangaInpaintCalibration")
        assumeTrue("Manual model calibration requires MI_GAN or LAMA", name in setOf("MI_GAN", "LAMA"))
        val engine = MangaInpaintEngine.valueOf(checkNotNull(name))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val folder = File(app.cacheDir, "manga-inpaint-calibration").apply { mkdirs() }
        val models = MangaInpaintModelRepository(app, File(folder, "weights"))
        val liveDownload = InstrumentationRegistry.getArguments().getString("mangaInpaintDownload") == "true"
        val downloadStarted = SystemClock.elapsedRealtime()
        if (liveDownload) {
            models.refresh()
            assertEquals(MangaInpaintModelPhase.MISSING, models.state.value.getValue(engine).phase)
            models.download(engine)
        }
        val downloadMillis = if (liveDownload) SystemClock.elapsedRealtime() - downloadStarted else 0L
        models.refresh()
        assertEquals(MangaInpaintModelPhase.READY, models.state.value.getValue(engine).phase)
        val runtime = MangaLocalInpainter(app, models)
        val loading = SystemClock.elapsedRealtime(); runtime.requireReady(engine)
        val loadMillis = SystemClock.elapsedRealtime() - loading
        val source = File(folder, "source.png")
        val external = source.isFile
        val bitmap = if (external) checkNotNull(BitmapFactory.decodeFile(source.path)).copy(Bitmap.Config.ARGB_8888, true)
            else Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(235, 240, 246))
                Canvas(this).drawText("TEST 123", 155f, 190f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 32f })
            }
        try {
            val plan = if (external) MangaTranslationPlan.json.decodeFromString<MangaTranslationPlan>(File(folder, "plan.json").readText()).validate()
                else MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "TEST 123", "测试", MangaTextBox(280f, 390f, 650f, 530f),
                    typesetting = MangaAiTypesetting(listOf("测试"), 80f))))
            val before = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
            val mask = MangaPageComposer.erasureMask(bitmap, plan)
            assertTrue("Source text mask must not be empty", mask.any { it })
            fun png(name: String, bmp: Bitmap) = File(folder, name).outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            png("before.png", bitmap)
            val local = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            try { MangaPageComposer.erase(local, plan); png("after-LOCAL.png", local) } finally { local.recycle() }
            val maskBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            try { maskBitmap.setPixels(IntArray(mask.size) { if (mask[it]) Color.WHITE else Color.BLACK }, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height); png("mask.png", maskBitmap) }
            finally { maskBitmap.recycle() }
            val started = SystemClock.elapsedRealtime(); runtime.erase(bitmap, mask, engine)
            val inferenceMillis = SystemClock.elapsedRealtime() - started
            val after = IntArray(before.size).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
            assertTrue("Model must alter some source lettering", before.indices.any { mask[it] && before[it] != after[it] })
            assertTrue("Every unmasked pixel must remain bit-exact", before.indices.all { mask[it] || before[it] == after[it] })
            png("after-${engine.name}.png", bitmap)
            val summary = buildJsonObject {
                put("engine", engine.name); put("loadMillis", loadMillis); put("inferenceMillis", inferenceMillis)
                put("downloadRequested", liveDownload); put("downloadMillis", downloadMillis)
                put("width", bitmap.width); put("height", bitmap.height); put("maskPixels", mask.count { it })
                put("changedPixels", before.indices.count { before[it] != after[it] }); put("unmaskedUnchanged", true); put("aiRequests", 0)
            }
            File(folder, "summary-${engine.name}.json").writeText(summary.toString())
        } finally { bitmap.recycle() }
    }
}
