package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import org.junit.Assert.assertEquals
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
    fun lazyRowFadesOnlyOverflowAndTracksRtl() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val itemCount = mutableStateOf(3)
        val itemWidth = mutableStateOf(100.dp)
        lateinit var list: LazyListState
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                list = rememberLazyListState()
                Box(Modifier.width(120.dp).background(Color.White).testTag("viewport")) {
                    LazyRow(Modifier.width(120.dp).fadingEdge(list, 16.dp), state = list) {
                        items(itemCount.value) {
                            Box(Modifier.width(itemWidth.value).height(40.dp).background(Color.Red))
                        }
                    }
                }
            }
        }
        fun assertEdges(leftFaded: Boolean, rightFaded: Boolean) {
            val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
            val left = pixels[1, pixels.height / 2].green
            val right = pixels[pixels.width - 2, pixels.height / 2].green
            assertTrue("Left edge: $left", if (leftFaded) left > 0.8f else left < 0.1f)
            assertTrue("Right edge: $right", if (rightFaded) right > 0.8f else right < 0.1f)
        }
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = layoutDirection }
            runBlocking { list.scrollToItem(0) }
            assertEdges(layoutDirection == LayoutDirection.Rtl, layoutDirection == LayoutDirection.Ltr)
            runBlocking { list.scrollToItem(2) }
            assertEdges(layoutDirection == LayoutDirection.Ltr, layoutDirection == LayoutDirection.Rtl)
        }
        // Both edges remain opaque when the contents exactly fit the viewport.
        compose.runOnIdle { itemCount.value = 1; itemWidth.value = 120.dp }
        assertEdges(leftFaded = false, rightFaded = false)
    }

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

    @Test
    fun lazyRowClipLeavesVerticalRoomForChildShadows() {
        compose.setContent {
            Box(Modifier.width(120.dp).height(80.dp).background(Color.White).testTag("root")) {
                LazyRow(Modifier.height(40.dp)) {
                    item {
                        Box(
                            Modifier.width(60.dp).height(40.dp).drawWithContent {
                                drawContent()
                                drawRect(
                                    color = Color.Red,
                                    topLeft = Offset(0f, size.height),
                                    size = Size(size.width, 12.dp.toPx())
                                )
                            }
                        )
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("root").captureToImage().toPixelMap()
        val x = with(compose.density) { 20.dp.roundToPx() }
        val y = with(compose.density) { 46.dp.roundToPx() }
        assertEquals(Color.Red, pixels[x, y])
    }

    @Test
    fun bleedFadeOnLazyRowKeepsChildShadowsWhileScrolling() {
        val bleed = mutableStateOf(12.dp)
        lateinit var list: LazyListState
        compose.setContent {
            list = rememberLazyListState()
            Box(Modifier.width(120.dp).height(80.dp).background(Color.White).testTag("root")) {
                Box(Modifier.width(120.dp).height(40.dp).fadingEdge(list, 16.dp, bleed.value)) {
                    LazyRow(state = list) {
                        items(3) {
                            Box(
                                Modifier.width(60.dp).height(40.dp).drawWithContent {
                                    drawContent()
                                    drawRect(
                                        color = Color.Red,
                                        topLeft = Offset(0f, size.height),
                                        size = Size(size.width, 12.dp.toPx())
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        fun shadowPixel(): Color {
            val x = with(compose.density) { 20.dp.roundToPx() }
            val y = with(compose.density) { 46.dp.roundToPx() }
            return compose.onNodeWithTag("root").captureToImage().toPixelMap()[x, y]
        }
        // 静止时（无渐隐）没有离屏层，LazyRow 自己的纵向裁剪也已放行。
        assertEquals(Color.Red, shadowPixel())
        // 滚动时渐隐生效，外扩的层仍保住节点下方的投影。
        runBlocking { list.scrollToItem(2) }
        assertEquals(Color.Red, shadowPixel())
        // 旧实现（bleed = 0）的 Offscreen 层会把节点外的投影切平。
        compose.runOnIdle { bleed.value = 0.dp }
        assertEquals(Color.White, shadowPixel())
    }

    @Test
    fun bleedKeepsContentOutsideTheNodeBoundsUntilAnEdgeActuallyFades() {
        val leftAlpha = mutableStateOf(0f)
        val rightAlpha = mutableStateOf(0f)
        val bleed = mutableStateOf(16.dp)
        compose.setContent {
            Box(Modifier.width(120.dp).height(80.dp).background(Color.White).testTag("root")) {
                Box(
                    Modifier.width(120.dp).height(40.dp)
                        .fadingEdge(leftAlpha, rightAlpha, 16.dp, bleed.value)
                ) {
                    Box(
                        Modifier.fillMaxSize().drawWithContent {
                            drawContent()
                            drawRect(Color.Red)
                            // 子级画在自身边界下方 12dp，相当于主体外的投影或外溢装饰。
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(0f, size.height),
                                size = Size(size.width, 12.dp.toPx())
                            )
                        }
                    )
                }
            }
        }
        fun colorAt(x: Int, y: Int): Color {
            val px = with(compose.density) { x.dp.roundToPx() to y.dp.roundToPx() }
            return compose.onNodeWithTag("root").captureToImage().toPixelMap()[px.first, px.second]
        }
        compose.runOnIdle { bleed.value = 16.dp; leftAlpha.value = 0f; rightAlpha.value = 0f }
        assertEquals(Color.Red, colorAt(60, 20))
        assertEquals(Color.Red, colorAt(60, 44))
        // 只有渐隐的一侧收回到节点边界，另一侧的绘制空间不受影响。
        compose.runOnIdle { leftAlpha.value = 1f }
        assertEquals(Color.Red, colorAt(60, 44))
        assertTrue("左边缘应已渐隐", colorAt(1, 20) != Color.Red)
        // 未声明 bleed 时仍按节点边界裁剪，滚动容器的旧行为不变。
        compose.runOnIdle { leftAlpha.value = 0f; bleed.value = 0.dp }
        assertEquals(Color.Red, colorAt(60, 20))
        assertEquals(Color.White, colorAt(60, 44))
    }
}
