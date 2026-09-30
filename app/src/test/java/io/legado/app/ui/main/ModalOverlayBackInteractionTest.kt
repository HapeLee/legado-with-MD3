package io.legado.app.ui.main

import android.app.Application
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ModalOverlayBackInteractionTest {
    @Test
    fun readerBackThenInfoPredictiveBackReturnsToEachEntryPointWithoutReplacingInfoLifecycleOwner() {
        verifyBack(MainRouteHome)
        verifyBack(MainRouteSearch(key = "Book"))
        verifyBack(MainRouteExploreShow("Explore", "source-url", "explore-url"))
    }

    private fun verifyBack(parent: NavKey) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val info = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(bookUrl = "book-url")
        val parentStack =
            if (parent == MainRouteHome) listOf(parent) else listOf(MainRouteHome, parent)
        val backStack = mutableStateListOf<NavKey>().apply { addAll(parentStack) }
        MainNavigator.navigateToRoute(backStack, info)
        assertEquals(parentStack + info, backStack.toList())
        var infoOwner: LifecycleOwner? = null
        val handledBacks = mutableListOf<NavKey>()
        val tappedRoutes = mutableListOf<NavKey>()
        try {
            activity.setContent {
                NavDisplay(
                    backStack = backStack,
                    sceneStrategies = listOf(
                        ModalOverlaySceneStrategy(),
                        SinglePaneSceneStrategy()
                    ),
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<MainRouteHome> { BasicText("Home") }
                        entry<MainRouteSearch> { BasicText("Search") }
                        entry<MainRouteExploreShow> { BasicText("Explore") }
                        entry<MainRouteBookInfo>(metadata = ModalOverlaySceneStrategy.modalOverlay()) { route ->
                            val owner = LocalLifecycleOwner.current
                            SideEffect { infoOwner = owner }
                            PredictiveBackHandler(enabled = backStack.lastOrNull() == route) { events ->
                                events.collect { }
                                handledBacks.add(route)
                                backStack.removeLastOrNull()
                            }
                            Box(Modifier
                                .fillMaxSize()
                                .clickable { tappedRoutes.add(route) }) {
                                BasicText("Info")
                            }
                        }
                        entry<MainRouteReadBook>(metadata = ModalOverlaySceneStrategy.modalOverlay()) { route ->
                            PredictiveBackHandler(enabled = backStack.lastOrNull() == route) { events ->
                                events.collect { }
                                handledBacks.add(route)
                                backStack.removeLastOrNull()
                            }
                            Box(Modifier
                                .fillMaxSize()
                                .clickable { tappedRoutes.add(route) }) {
                                BasicText("Reader")
                            }
                        }
                    },
                )
            }
            fun frames() {
                repeat(30) {
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
                frames()
            }

            fun assertTopRoute(expected: NavKey) {
                val decor = activity.window.decorView
                val time = android.os.SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 540f, 960f, 0)
                val up = MotionEvent.obtain(time, time + 16, MotionEvent.ACTION_UP, 540f, 960f, 0)
                try {
                    decor.dispatchTouchEvent(down)
                    decor.dispatchTouchEvent(up)
                } finally {
                    down.recycle()
                    up.recycle()
                }
                frames()
                assertEquals(
                    "The visible, tappable route must match the stack top",
                    expected,
                    tappedRoutes.lastOrNull()
                )
            }
            frames()
            assertTopRoute(info)
            val originalInfoOwner = requireNotNull(infoOwner)
            MainNavigator.navigateToRoute(backStack, reader)
            assertEquals(parentStack + info + reader, backStack.toList())
            frames()
            assertSame(originalInfoOwner, infoOwner)
            assertTopRoute(reader)
            startGesture()
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertEquals(parentStack + info, backStack.toList())
            assertSame(originalInfoOwner, infoOwner)
            assertTopRoute(info)
            assertEquals(listOf(reader), handledBacks)

            startGesture()
            activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            frames()
            assertEquals(parentStack + info, backStack.toList())
            assertEquals(listOf(reader), handledBacks)
            startGesture()
            activity.onBackPressedDispatcher.onBackPressed()
            frames()
            assertEquals(parentStack, backStack.toList())
            assertEquals(listOf(reader, info), handledBacks)
            assertFalse(activity.isFinishing)
            if (parent != MainRouteHome) {
                // The restored Search/Explore scene must still have Home as its parent.
                startGesture()
                activity.onBackPressedDispatcher.onBackPressed()
                frames()
                assertEquals(listOf(MainRouteHome), backStack.toList())
                assertFalse(activity.isFinishing)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
