package io.legado.app.ui.widget.components

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.text.AppText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FloatingBottomBarInteractionTest {
    @Test
    fun physicalMenuTapRestoresNavigationInsteadOfHittingTheTransparentTabs() =
        verifyDockTap(1f, 0.5f, expectedRestores = 1, expectedTabClicks = 0)

    @Test
    fun menuTapCanReverseAnOpeningAnimation() =
        verifyDockTap(0.6f, 0.5f, expectedRestores = 1, expectedTabClicks = 0)

    @Test
    fun normalNavigationStillHandlesOneTapAfterRemovingSamplingInteractions() =
        verifyDockTap(0f, 0.375f, expectedRestores = 0, expectedTabClicks = 1)

    private fun verifyDockTap(
        compactProgress: Float,
        targetFraction: Float,
        expectedRestores: Int,
        expectedTabClicks: Int,
    ) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        var restores = 0
        var tabClicks = 0
        var bounds = Rect.Zero
        try {
            activity.setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    AppTheme(AppUiConfiguration(), applyBackground = false) {
                        Box(Modifier.fillMaxSize()) {
                            FloatingBottomBar(
                                modifier = Modifier
                                    .width((312f - 248f * compactProgress).dp)
                                    .onGloballyPositioned { bounds = it.boundsInWindow() },
                                selectedIndex = { 0 },
                                onSelected = { tabClicks++ },
                                tabsCount = 4,
                                backdrop = rememberLayerBackdrop(),
                                isBlurEnabled = false,
                                compactProgress = compactProgress,
                                expandedWidth = 312.dp,
                                onRestoreNavigation = { restores++ },
                            ) {
                                repeat(4) {
                                    FloatingBottomBarItem(
                                        onClick = { tabClicks++ },
                                        enabled = compactProgress == 0f
                                    ) {
                                        AppText("Tab $it")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val decor = activity.window.decorView
            repeat(5) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
                )
                decor.layout(0, 0, 1080, 1920)
            }
            assertFalse(
                "The real dock must be laid out before sending touch events",
                bounds.isEmpty
            )
            val downTime = SystemClock.uptimeMillis()
            MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN,
                bounds.left + bounds.width * targetFraction, bounds.center.y, 0
            ).also {
                activity.dispatchTouchEvent(it)
                it.recycle()
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                bounds.left + bounds.width * targetFraction, bounds.center.y, 0
            ).also {
                activity.dispatchTouchEvent(it)
                it.recycle()
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            assertEquals(expectedRestores, restores)
            assertEquals(expectedTabClicks, tabClicks)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
