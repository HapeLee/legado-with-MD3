package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FadingEdgeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun scrollFadeTracksThePhysicalOverflowEdgeInLtrAndRtl() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        lateinit var scroll: ScrollState
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                scroll = rememberScrollState()
                Box(Modifier.width(120.dp).background(Color.White).testTag("viewport")) {
                    Box(Modifier.fadingEdge(scroll, 16.dp).horizontalScroll(scroll)) {
                        Box(Modifier.width(300.dp).height(40.dp).background(Color.Red))
                    }
                }
            }
        }
        fun assertEdges(leftFaded: Boolean, rightFaded: Boolean) {
            val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
            val left = pixels[1, pixels.height / 2].green
            val right = pixels[pixels.width - 2, pixels.height / 2].green
            assertTrue("Left edge: $left, faded=$leftFaded", if (leftFaded) left > 0.8f else left < 0.1f)
            assertTrue("Right edge: $right, faded=$rightFaded", if (rightFaded) right > 0.8f else right < 0.1f)
        }
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = layoutDirection }
            runBlocking { scroll.scrollTo(0) }
            assertEdges(leftFaded = layoutDirection == LayoutDirection.Rtl, rightFaded = layoutDirection == LayoutDirection.Ltr)
            runBlocking { scroll.scrollTo(scroll.maxValue) }
            assertEdges(leftFaded = layoutDirection == LayoutDirection.Ltr, rightFaded = layoutDirection == LayoutDirection.Rtl)
        }
    }
}
