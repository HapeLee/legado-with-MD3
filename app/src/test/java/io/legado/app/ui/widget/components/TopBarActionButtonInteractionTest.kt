package io.legado.app.ui.widget.components

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getAllSemanticsNodes
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalLegadoThemeColors
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarActionsRow
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
@Config(application = Application::class, sdk = [35])
class TopBarActionButtonInteractionTest {
    @Test
    fun `long press consumes the click and exposes its action in every button path`() {
        verifyButton("material", "tonal", merged = false)
        verifyButton("material", "tonal", merged = true)
        verifyButton("miuix", "plain", merged = false)
    }

    private fun verifyButton(engine: String, style: String, merged: Boolean) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        var clicks = 0
        var longClicks = 0
        var bounds = Rect.Zero
        try {
            activity.setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    AppTheme(
                        AppUiConfiguration(theme = ThemeSettings(
                            topBarButtonStyle = style,
                            mergeTopBarActions = merged,
                        )),
                        applyBackground = false,
                    ) {
                        CompositionLocalProvider(
                            LocalLegadoThemeColors provides
                                LocalLegadoThemeColors.current.copy(composeEngine = engine),
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                TopBarActionsRow {
                                    TopBarActionButton(
                                        onClick = { clicks++ },
                                        onLongClick = { longClicks++ },
                                        onLongClickLabel = "Filter bookshelf",
                                        imageVector = AppIcons.Search,
                                        contentDescription = "Search",
                                        modifier = Modifier.onGloballyPositioned {
                                            bounds = it.boundsInWindow()
                                        },
                                    )
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
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                )
                decor.layout(0, 0, 1080, 1920)
            }
            assertFalse("$engine/$style/$merged must be laid out", bounds.isEmpty)
            assertTrue(longClickLabels(decor).contains("Filter bookshelf"))

            fun press(holdMillis: Long) {
                val downTime = SystemClock.uptimeMillis()
                MotionEvent.obtain(
                    downTime, downTime, MotionEvent.ACTION_DOWN,
                    bounds.center.x, bounds.center.y, 0,
                ).also {
                    activity.dispatchTouchEvent(it)
                    it.recycle()
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                if (holdMillis > 0) Thread.sleep(holdMillis)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(holdMillis))
                MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                    bounds.center.x, bounds.center.y, 0,
                ).also {
                    activity.dispatchTouchEvent(it)
                    it.recycle()
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            }

            press(0)
            assertEquals(1, clicks)
            assertEquals(0, longClicks)
            press(ViewConfiguration.getLongPressTimeout().toLong() + 200)
            assertEquals("long press must not also click: $engine/$style/$merged", 1, clicks)
            assertEquals(1, longClicks)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun longClickLabels(view: View): List<String> {
        if (view is ViewRootForTest) {
            return view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).mapNotNull {
                it.config.getOrElseNullable(SemanticsActions.OnLongClick) { null }?.label
            }
        }
        return if (view is ViewGroup) {
            (0 until view.childCount).flatMap { longClickLabels(view.getChildAt(it)) }
        } else emptyList()
    }
}
