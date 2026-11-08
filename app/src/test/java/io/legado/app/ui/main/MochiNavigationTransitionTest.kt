package io.legado.app.ui.main

import android.app.Application
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.NavigationAnimationStyle
import io.legado.app.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MochiNavigationTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sidePushAndPredictiveBackDrawBothPagesAndKeepCancellationOnTheSameRoute() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val first = MainRouteSettings
        val second = MainRouteSettingsTheme
        val stack = mutableStateListOf<NavKey>(first)
        val displayLeft = mutableFloatStateOf(0f)
        val decorator = NavigationPageSceneDecorator(displayLeft)
        val input = DirectNavigationEventInput()
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
        compose.runOnUiThread { owner.navigationEventDispatcher.addInput(input) }
        val style = mutableStateOf(NavigationAnimationStyle.Default)
        val tracker = MainNavRouteTracker()
        var parentX = 0f
        var childX = 0f
        compose.setContent {
            val currentStyle = style.value
            val configuration = AppUiConfiguration(
                appShell = AppShellSettings(
                    composeEngine = "mochi",
                    navigationAnimationStyle = currentStyle,
                )
            )
            AppTheme(configuration, darkTheme = false, applyBackground = false) {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    SharedTransitionLayout {
                        val direction = LocalLayoutDirection.current
                        NavDisplay(
                            backStack = stack,
                            modifier = Modifier.onGloballyPositioned {
                                displayLeft.floatValue = it.positionInRoot().x
                            },
                            sharedTransitionScope = this,
                            sceneStrategies = listOf(
                                ModalOverlaySceneStrategy(),
                                ListDetailSceneStrategy(isEnabled = { false }),
                                SinglePaneSceneStrategy(),
                            ),
                            sceneDecoratorStrategies = listOf(decorator),
                            transitionSpec = {
                                navigationPreset(currentStyle, true, direction)
                                    ?: (EnterTransition.None togetherWith ExitTransition.None)
                            },
                            popTransitionSpec = {
                                navigationPreset(currentStyle, false, direction)
                                    ?: (EnterTransition.None togetherWith ExitTransition.None)
                            },
                            predictivePopTransitionSpec = { edge ->
                                navigationPreset(currentStyle, false, direction, edge)
                                    ?: (EnterTransition.None togetherWith ExitTransition.None)
                            },
                            entryProvider = mainPaneEntryProvider(stack, false, tracker, {}) {
                                entry<MainRouteSettings> {
                                    Box(Modifier.fillMaxSize().background(Color.Green)
                                        .onGloballyPositioned { parentX = it.positionInRoot().x })
                                }
                                entry<MainRouteSettingsTheme> {
                                    Box(Modifier.fillMaxSize().background(Color.Red)
                                        .onGloballyPositioned { childX = it.positionInRoot().x })
                                }
                            },
                        )
                    }
                }
            }
        }
        fun frame() = compose.onRoot().captureToImage().toPixelMap()
        fun advance(frames: Int) {
            repeat(frames) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        fun event(progress: Float) = NavigationEvent(
            swipeEdge = NavigationEvent.EDGE_LEFT,
            progress = progress,
        )
        fun redStart(row: Int): Int {
            val pixels = frame()
            return (0 until pixels.width).firstOrNull { pixels[it, row].red > 0.95f } ?: pixels.width
        }

        val initial = frame()
        assertEquals(Color.Green, initial[0, 0])
        val row = initial.height / 2
        compose.runOnIdle { style.value = NavigationAnimationStyle.Miuix }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle { stack.add(second) }
        advance(10)
        val entryEdge = redStart(row)
        assertTrue("Child enters from the right, x=$entryEdge", entryEdge in 1 until initial.width)
        assertTrue("Parent retreats beneath child, parent=$parentX child=$childX", parentX < 0f && childX > 0f)
        assertTrue(
            "Parent and child move together, parent=$parentX child=$childX",
            abs(parentX + (initial.width - childX) * 0.25f) <= 2f,
        )
        assertTrue("Push dims the exposed parent", frame()[4, row].green in 0.8f..0.99f)
        advance(48)
        assertEquals(Color.Red, frame()[0, 0])

        compose.runOnIdle { input.backStarted(event(0f)) }
        advance(4)
        compose.runOnIdle { input.backProgressed(event(0.25f)) }
        advance(5)
        val firstEdge = redStart(row)
        val quarterWidth = initial.width / 4
        assertTrue("Quarter gesture puts the page edge at x=$firstEdge", firstEdge in quarterWidth - 2..quarterWidth + 2)
        assertTrue("Moving page has rounded corners", redStart(0) > firstEdge + 4)
        val preview = frame()
        assertTrue("Underlying page is dimmed", preview[4, row].green in 0.75f..0.93f)
        assertTrue("Moving edge casts a shadow", preview[firstEdge - 2, row].green < preview[4, row].green)

        compose.runOnIdle { input.backProgressed(event(0.6f)) }
        advance(5)
        assertTrue(redStart(row) > firstEdge)
        compose.runOnIdle { input.backCancelled() }
        advance(30)
        assertEquals(listOf(first, second), stack.toList())
        assertEquals(Color.Red, frame()[0, 0])

        compose.runOnIdle { input.backStarted(event(0f)) }
        advance(4)
        compose.runOnIdle { input.backProgressed(event(0.5f)) }
        advance(5)
        compose.runOnIdle { input.backCompleted() }
        advance(36)
        assertEquals(listOf(first), stack.toList())
        assertEquals(Color.Green, frame()[0, 0])

        compose.runOnIdle { stack.add(second) }
        advance(48)
        compose.runOnIdle { stack.removeLastOrNull() }
        advance(10)
        assertTrue(redStart(row) in 1 until initial.width)
        advance(48)
        assertEquals(Color.Green, frame()[0, 0])

        compose.runOnIdle { style.value = NavigationAnimationStyle.Scale }
        advance(3)
        compose.runOnIdle { stack.add(second) }
        advance(48)
        assertEquals(Color.Red, frame()[0, 0])

        compose.runOnIdle { input.backStarted(event(0f)) }
        advance(4)
        compose.runOnIdle { input.backProgressed(event(0.15f)) }
        advance(5)
        val early = frame()
        val left = (0 until early.width).first { early[it, row] == Color.Red }
        val top = (0 until early.height).first { early[early.width / 2, it] == Color.Red }
        assertEquals("Scale corner starts rounding gradually", Color.Red, early[left + 3, top + 3])
        compose.runOnIdle { input.backProgressed(event(0.5f)) }
        advance(5)
        val gestureFrame = frame()
        val width = (0 until initial.width).count { gestureFrame[it, row] == Color.Red }
        assertTrue("Scale shrinks the outgoing page", width in 1 until initial.width)
        assertEquals("Scale stays opaque halfway through the gesture", Color.Red, gestureFrame[initial.width / 2, row])

        compose.runOnIdle { input.backProgressed(event(0.75f)) }
        advance(5)
        val fade = frame()[initial.width / 2, row]
        assertTrue("Scale fades gradually in the final stretch: $fade", fade.red in 0.55f..0.98f && fade.green > 0.02f)
        compose.runOnIdle { input.backCancelled() }
        advance(40)
        assertEquals(listOf(first, second), stack.toList())
        assertEquals(Color.Red, frame()[0, 0])

        compose.runOnIdle { input.backStarted(event(0f)) }
        advance(4)
        compose.runOnIdle { input.backProgressed(event(0.5f)) }
        advance(5)
        compose.runOnIdle { input.backCompleted() }
        advance(50)
        assertEquals(listOf(first), stack.toList())
        assertEquals(Color.Green, frame()[0, 0])
        compose.mainClock.autoAdvance = true
    }
}
