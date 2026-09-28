package io.legado.app.core.ui.player

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.unit.Density
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import io.legado.app.ui.main.shouldHandleActivityBack
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.text.AppText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
// API 34 的独立 sandbox 避免与胶囊触摸测试共用已被重置的 Choreographer。
@Config(application = Application::class, sdk = [34])
class PlayerMorphHostInteractionTest {
    @Test
    fun backClosesTheModalPlayerAndRestoresUnderlaySemanticsWithEitherRegistrationOrder() {
        // 在同一个 Android 主循环里覆盖两种注册顺序与返回开关，避免 Robolectric
        // 重置 Choreographer 后复用 Compose 的全局主线程调度器。
        verifyBack(false, true)
        verifyBack(false, false)
        verifyBack(true, true)
        verifyBack(false, true, initialProgress = 0f)
    }

    private fun verifyBack(
        predictive: Boolean,
        activityRegistersLast: Boolean,
        initialProgress: Float = 1f
    ) {
        val application = RuntimeEnvironment.getApplication()
        AppConfigStore.init(application)
        stopKoin()
        startKoin {
            modules(module {
                single {
                    CoverAlbumUseCase(
                        CoverAlbumRepository(
                            application,
                            SettingsRepository()
                        )
                    )
                }
            })
        }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        var visible by mutableStateOf(true)
        val morph = ReadAloudMorphState(Animatable(initialProgress), Density(1f))
        var routeBacks = 0
        var dismissals = 0
        try {
            activity.setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    AppTheme(AppUiConfiguration(), applyBackground = false) {
                        val present by remember { derivedStateOf { visible || morph.progress.value > 0f } }
                        val fallback: @Composable () -> Unit = {
                            BackHandler(
                                shouldHandleActivityBack(
                                    predictive,
                                    present
                                )
                            ) { routeBacks++ }
                        }
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .playerUnderlaySemantics(present)
                            ) { AppText("Bookshelf") }
                            if (!activityRegistersLast) fallback()
                            PlayerMorphHost(
                                appearance = PlayerMorphAppearance("Book", "", null, null, 0),
                                playerTheme = null, morph = morph, visible = visible,
                                awaitCapsuleAnchor = true,
                                predictiveBackEnabled = predictive,
                                onDismiss = { dismissals++; visible = false },
                            ) { AppText("Player controls") }
                            if (activityRegistersLast) fallback()
                        }
                    }
                }
            }
            fun frames(count: Int) {
                repeat(count) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                    val decor = activity.window.decorView
                    decor.measure(
                        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
                    )
                    decor.layout(0, 0, 1080, 1920)
                }
            }
            frames(if (initialProgress == 0f) 180 else 5)
            assertEquals(1f, morph.progress.value)
            assertFalse(visibleTexts(activity.window.decorView).contains("Bookshelf"))
            assertTrue(visibleTexts(activity.window.decorView).contains("Player controls"))
            activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(
                    0f,
                    0f,
                    0f,
                    BackEventCompat.EDGE_LEFT
                )
            )
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(
                    0f,
                    0f,
                    0.6f,
                    BackEventCompat.EDGE_LEFT
                )
            )
            frames(5)
            if (predictive) assertTrue(morph.progress.value < 1f) else assertEquals(
                1f,
                morph.progress.value
            )
            activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            frames(90)
            assertEquals(1f, morph.progress.value)
            assertEquals(0, dismissals)
            activity.onBackPressedDispatcher.onBackPressed()
            frames(90)
            assertEquals("The covered route must not receive this back", 0, routeBacks)
            assertEquals(
                "progress=${morph.progress.value} running=${morph.progress.isRunning} visible=$visible",
                1,
                dismissals
            )
            assertFalse(visible)
            assertEquals(0f, morph.progress.value)
            assertTrue(visibleTexts(activity.window.decorView).contains("Bookshelf"))
            assertFalse(visibleTexts(activity.window.decorView).contains("Player controls"))
            activity.onBackPressedDispatcher.onBackPressed()
            if (!predictive) assertEquals(1, routeBacks)
        } finally {
            controller.pause().stop().destroy()
            stopKoin()
        }
    }

    private fun visibleTexts(view: View): List<String> {
        if (view is ViewRootForTest) return view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
            .flatMap {
                it.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .map { text -> text.text }
            }
        return if (view is ViewGroup) (0 until view.childCount).flatMap {
            visibleTexts(
                view.getChildAt(
                    it
                )
            )
        }
        else emptyList()
    }
}
