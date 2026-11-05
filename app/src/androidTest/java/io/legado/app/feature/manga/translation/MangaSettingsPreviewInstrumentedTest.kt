package io.legado.app.feature.manga.translation

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.book.manga.MangaReaderPageActionsSheet
import io.legado.app.ui.theme.AppTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in visual fixtures; no book, credential, network or translation gateway is accessed. */
@RunWith(AndroidJUnit4::class)
class MangaSettingsPreviewInstrumentedTest {
    @Test fun capturePortraitAndLandscapeFixtures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("mangaSettingsPreview") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val output = File(app.cacheDir, "manga-settings-preview").apply { mkdirs() }
        ActivityScenario.launch<ComponentActivity>(Intent(app, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            for ((name, orientation) in listOf("portrait" to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                "landscape" to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                scenario.onActivity { it.requestedOrientation = orientation }
                SystemClock.sleep(700)
                for (menu in listOf(false, true)) {
                    scenario.onActivity { activity ->
                        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true, chapterPages = 8,
                            models = persistentListOf(MangaTranslationModelUi("fixture", "DeepSeek / DeepSeek Flash", false, vision = true))))
                        activity.setContent { AppTheme(AppUiConfiguration()) {
                            if (menu) MangaReaderPageActionsSheet(false, {}, onTranslate = {}, onRecompose = {})
                            else MangaTranslationSheet(state.value) { intent ->
                                if (intent is MangaTranslationIntent.ChangeSettings)
                                    state.value = state.value.copy(settings = intent.transform(state.value.settings))
                                if (intent is MangaTranslationIntent.ChangeGlobalSettings)
                                    state.value = state.value.copy(globalSettings = intent.transform(state.value.globalSettings))
                            }
                        } }
                    }
                    instrumentation.waitForIdleSync()
                    SystemClock.sleep(600)
                    val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    try { File(output, "$name-${if (menu) "menu" else "settings"}.png").outputStream().use {
                        check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                    } } finally { image.recycle() }
                }
            }
        }
    }
}
