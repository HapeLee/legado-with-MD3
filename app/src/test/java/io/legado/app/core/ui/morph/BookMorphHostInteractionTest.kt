package io.legado.app.core.ui.morph

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
// Keep the Compose frame dispatcher separate from the Navigation 3 tests' API 34 sandbox.
@Config(application = Application::class, sdk = [35])
class BookMorphHostInteractionTest {
    @Test
    fun rejectedDismissRestoresContentForButtonAndGestureAndAllowsRetry() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        var morph: BookMorphState? = null
        var collapse: (() -> Unit)? = null
        var attempts = 0
        var allowPop = false
        var requests = 0
        var requestApproval by mutableStateOf(false)
        try {
            activity.setContent {
                key(requestApproval) {
                    BookMorphHost(
                        anchorKey = null,
                        backgroundColor = Color.White,
                        onDismiss = { attempts++; allowPop },
                        onBackRequested = if (requestApproval) ({ requests++ }) else null,
                    ) { onCollapse ->
                        val state = LocalBookMorph.current
                        SideEffect { morph = state; collapse = onCollapse }
                        BasicText("Reader")
                    }
                }
            }
            fun frames() {
                repeat(90) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                    val decor = activity.window.decorView
                    decor.measure(
                        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                    )
                    decor.layout(0, 0, 1080, 1920)
                }
            }

            fun startGesture() {
                activity.onBackPressedDispatcher.dispatchOnBackStarted(
                    BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)
                )
                activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                    BackEventCompat(0f, 0f, 0.6f, BackEventCompat.EDGE_LEFT)
                )
            }
            frames()
            assertTrue(requireNotNull(morph).expanded)
            requireNotNull(collapse).invoke()
            frames()
            assertEquals(1, attempts)
            assertTrue(
                "Rejected button dismissal must restore content",
                requireNotNull(morph).expanded
            )

            startGesture()
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertEquals(2, attempts)
            assertTrue(
                "Rejected predictive dismissal must restore content",
                requireNotNull(morph).expanded
            )

            startGesture()
            activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            frames()
            assertEquals(2, attempts)
            assertTrue(requireNotNull(morph).expanded)

            allowPop = true
            startGesture()
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertEquals(3, attempts)
            assertEquals(0f, requireNotNull(morph).progress.value)
            assertFalse(activity.isFinishing)

            // Readers request permission first. Finish invokes collapse separately, so
            // final navigation neither repeats the close command nor runs book deletion.
            requestApproval = true
            frames()
            startGesture()
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertEquals(1, requests)
            assertEquals("A back request must wait for Finish", 3, attempts)
            requireNotNull(collapse).invoke()
            frames()
            assertEquals(4, attempts)
            assertEquals("Committing Finish must not request close again", 1, requests)
            assertEquals(0f, requireNotNull(morph).progress.value)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
