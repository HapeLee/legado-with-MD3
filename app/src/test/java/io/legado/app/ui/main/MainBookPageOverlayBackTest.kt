package io.legado.app.ui.main

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class MainBookPageOverlayBackTest {
    @Test
    fun `predictive cancellation keeps stack and commit closes only the top overlay`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val reader = MainRouteReadBook("book")
        val toc = MainRouteToc("book")
        val rules = MainRouteReplaceRules("book")
        val stack = mutableStateListOf<NavKey>(MainRouteHome, reader, toc, rules)
        var closes = 0
        val close: () -> Unit = { closes++; stack.removeLast() }
        try {
            activity.setContent {
                NavDisplay(
                    backStack = stack,
                    onBack = close,
                    sceneStrategies = listOf(
                        remember {
                            ModalOverlaySceneStrategy(isTopEntry = { contentKey ->
                                stack.lastOrNull()
                                    ?.let { NavEntry(it) {}.contentKey == contentKey } == true
                            })
                        },
                        SinglePaneSceneStrategy(),
                    ),
                    entryProvider = entryProvider {
                        entry<MainRouteHome> { BasicText("Home") }
                        entry<MainRouteReadBook>(metadata = ModalOverlaySceneStrategy.modalOverlay()) {
                            BasicText("Reader")
                        }
                        entry<MainRouteToc>(metadata = ModalOverlaySceneStrategy.pageSlide(close)) {
                            BasicText("Toc")
                        }
                        entry<MainRouteReplaceRules>(
                            metadata = ModalOverlaySceneStrategy.pageSlide(
                                close
                            )
                        ) {
                            BasicText("Rules")
                        }
                    },
                )
            }
            fun frames() {
                repeat(40) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                    val decor = activity.window.decorView
                    decor.measure(
                        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                    )
                    decor.layout(0, 0, 1080, 1920)
                }
            }

            fun gesture() {
                activity.onBackPressedDispatcher.dispatchOnBackStarted(
                    BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)
                )
                activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                    BackEventCompat(0f, 0f, 0.6f, BackEventCompat.EDGE_LEFT)
                )
            }
            frames()
            gesture()
            activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            frames()
            assertEquals(listOf(MainRouteHome, reader, toc, rules), stack.toList())
            assertEquals(0, closes)

            gesture()
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertEquals(listOf(MainRouteHome, reader, toc), stack.toList())
            assertEquals(1, closes)

            gesture()
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertEquals(listOf(MainRouteHome, reader), stack.toList())
            assertEquals(2, closes)
            assertFalse(activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
