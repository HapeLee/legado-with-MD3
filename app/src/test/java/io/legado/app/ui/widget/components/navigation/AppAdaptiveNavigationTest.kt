package io.legado.app.ui.widget.components.navigation

import androidx.compose.ui.unit.IntSize
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAdaptiveNavigationTest {
    @Test
    fun autoPanesUse840DpBoundaryInEitherOrientation() {
        assertFalse(shouldUseListDetail("auto", WindowSizeClass(839f, 1200f), portraitPx))
        assertTrue(shouldUseListDetail("auto", WindowSizeClass(840f, 1200f), portraitPx))
        assertTrue(shouldUseListDetail("auto", expandedLandscape, landscapePx))
    }

    @Test
    fun landscapePanesRequireBothWideWindowAndLandscapeOrientation() {
        assertFalse(shouldUseListDetail("landscape", expandedLandscape, portraitPx))
        assertTrue(shouldUseListDetail("landscape", expandedLandscape, landscapePx))
        assertFalse(shouldUseListDetail("landscape", tabletNarrowWindow, landscapePx))
        assertFalse(shouldUseListDetail("landscape", expandedLandscape, IntSize(1000, 1000)))
    }

    @Test
    fun alwaysPanesAllowSmallerWindowsButNeverSqueezeBelowMinimum() {
        assertFalse(shouldUseListDetail("always", WindowSizeClass(743f, 1000f), portraitPx))
        assertTrue(shouldUseListDetail("always", WindowSizeClass(744f, 1000f), portraitPx))
        assertTrue(shouldUseListDetail("always", tabletPortrait, portraitPx))
        assertFalse(shouldUseListDetail("auto", tabletPortrait, portraitPx))
    }

    @Test
    fun disabledPanesStayOffAndUnknownModesUseAuto() {
        assertFalse(shouldUseListDetail("off", expandedLandscape, landscapePx))
        assertTrue(shouldUseListDetail("unknown", expandedLandscape, portraitPx))
        assertFalse(shouldUseListDetail("unknown", tabletNarrowWindow, landscapePx))
        assertEquals(MultiPaneMode.Auto, MultiPaneMode.parse(null))
    }

    // WindowSizeClass 的公开构造接收 dp（float），其余构造是内部/伴随工厂。
    private val phonePortrait = WindowSizeClass(widthDp = 411f, heightDp = 891f)
    private val tabletNarrowWindow = WindowSizeClass(widthDp = 560f, heightDp = 700f)
    private val tabletPortrait = WindowSizeClass(widthDp = 800f, heightDp = 1200f)
    private val expandedLandscape = WindowSizeClass(widthDp = 1100f, heightDp = 700f)
    private val portraitPx = IntSize(width = 1080, height = 2400)
    private val landscapePx = IntSize(width = 2400, height = 1080)

    private fun useRail(mode: String, sizeClass: WindowSizeClass, px: IntSize): Boolean =
        shouldUseRail(tabletInterface = mode, windowSizeClass = sizeClass, windowSize = px)

    @Test
    fun mediumWidthBoundaryMatchesTheLegacyDpThreshold() {
        val threshold = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        assertEquals(600, threshold)
        assertFalse(WindowSizeClass(599f, 900f).isWidthAtLeastBreakpoint(threshold))
        assertTrue(WindowSizeClass(600f, 900f).isWidthAtLeastBreakpoint(threshold))
    }

    @Test
    fun autoModeUsesRailOnlyWhenTheCurrentWindowIsAtLeastMediumWidth() {
        assertFalse(useRail("auto", phonePortrait, portraitPx))
        assertFalse(useRail("auto", tabletNarrowWindow, portraitPx))
        assertTrue(useRail("auto", tabletPortrait, portraitPx))
        assertTrue(useRail("auto", expandedLandscape, landscapePx))
    }

    @Test
    fun autoModeFollowsTheWindowNotTheDevice() {
        // 设备是平板，但自由窗口被缩到手机宽度：旧实现按设备最小宽度会给出侧栏。
        assertFalse(useRail("auto", tabletNarrowWindow, portraitPx))
    }

    @Test
    fun landscapeModeAlsoRequiresEnoughSidebarContentSpace() {
        assertFalse(useRail("landscape", phonePortrait, portraitPx))
        assertFalse(useRail("landscape", phonePortrait, landscapePx))
        assertTrue(useRail("landscape", expandedLandscape, landscapePx))
    }

    @Test
    fun alwaysHonorsMinimumSpaceAndOffStaysOff() {
        assertFalse(useRail("always", phonePortrait, portraitPx))
        assertTrue(useRail("always", expandedLandscape, landscapePx))
        assertFalse(useRail("off", tabletPortrait, portraitPx))
        assertFalse(useRail("off", expandedLandscape, landscapePx))
    }

    @Test
    fun shortOrNarrowWindowsNeverForceSidebar() {
        assertFalse(useRail("auto", WindowSizeClass(1000f, 479f), landscapePx))
        assertFalse(useRail("always", WindowSizeClass(455f, 700f), portraitPx))
        assertTrue(useRail("always", WindowSizeClass(456f, 480f), portraitPx))
        assertTrue(useRail("auto", WindowSizeClass(600f, 480f), landscapePx))
    }

    @Test
    fun unknownPreferenceValueKeepsBottomNavigation() {
        assertFalse(useRail("legacy-value", tabletPortrait, portraitPx))
        assertFalse(useRail("", tabletPortrait, portraitPx))
    }
}
