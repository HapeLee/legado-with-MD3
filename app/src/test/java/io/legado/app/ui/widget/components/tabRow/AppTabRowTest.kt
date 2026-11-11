package io.legado.app.ui.widget.components.tabRow

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx
import top.yukonga.miuix.kmp.glass.GlassTabRowDefaults

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w800dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppTabRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fourFittingTabsReserveShadowSpaceAtBothHorizontalEdges() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val scrollable = mutableStateOf(true)
        val horizontalPadding = mutableStateOf(0.dp)
        val width = mutableStateOf(320.dp)
        val selected = mutableIntStateOf(0)
        val titles = listOf("A", "B", "C", "D")
        compose.setContent {
            AppTheme(configuration, applyBackground = false) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    AppTabRow(titles, selected.intValue, { selected.intValue = it },
                        modifier = Modifier.width(width.value).testTag("tabs"),
                        isScrollable = scrollable.value, horizontalPadding = horizontalPadding.value)
                }
            }
        }
        fun expectedInset() = with(compose.density) { maxOf(8.dp, horizontalPadding.value).toPx() }
        fun assertEdgeSpace() {
            val inset = expectedInset()
            val row = compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot
            val first = compose.onNodeWithText(titles.first()).fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithText(titles.last()).fetchSemanticsNode().boundsInRoot
            val left = if (direction.value == LayoutDirection.Ltr) first.left else last.left
            val right = if (direction.value == LayoutDirection.Ltr) last.right else first.right
            assertEquals(inset, left - row.left, 1f)
            assertEquals(inset, row.right - right, 1f)
        }
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (allowScroll in listOf(true, false)) {
                compose.runOnIdle { direction.value = layoutDirection; scrollable.value = allowScroll }
                assertEdgeSpace()
            }
        }
        // Explicit content margins include the shadow allowance rather than adding to it.
        compose.runOnIdle { horizontalPadding.value = 12.dp }
        assertEdgeSpace()
        // Exercise returning from real overflow to a row where all four tabs fit.
        compose.runOnIdle { direction.value = LayoutDirection.Ltr; scrollable.value = true; width.value = 240.dp; selected.intValue = 3 }
        compose.onNodeWithText(titles.last()).assertIsDisplayed().assertIsSelected()
        val narrow = compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText(titles.last()).fetchSemanticsNode().boundsInRoot
        assertEquals(expectedInset(), narrow.right - last.right, 1f)
        compose.runOnIdle { width.value = 320.dp }
        assertEdgeSpace()
    }

    @Test
    fun overflowingTabsKeepCatalogStyleAndRevealExternallySelectedTab() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val selected = mutableIntStateOf(0)
        val titles = List(8) { "Page $it" }
        compose.setContent {
            AppTheme(configuration, applyBackground = false) {
                AppTabRow(
                    tabTitles = titles,
                    selectedTabIndex = selected.intValue,
                    onTabSelected = { selected.intValue = it },
                    modifier = Modifier.width(320.dp).testTag("tabs"),
                )
            }
        }
        // Keep the neutral pills and reserve 8dp on each side inside the scroll/fade layer.
        compose.onNodeWithTag("tabs").assertHeightIsEqualTo(GlassTabRowDefaults.NeutralHeight + 16.dp)
        val row = compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot
        val tab = compose.onNodeWithText(titles.first()).fetchSemanticsNode().boundsInRoot
        val inset = with(compose.density) { 8.dp.toPx() }
        assertEquals(inset, tab.top - row.top, 1f)
        assertEquals(inset, row.bottom - tab.bottom, 1f)
        compose.onNodeWithText(titles[1]).performClick().assertIsSelected()
        compose.runOnIdle { selected.intValue = titles.lastIndex }
        compose.onNodeWithText(titles.last()).assertIsSelected().assertIsDisplayed()
        compose.runOnIdle { selected.intValue = 0 }
        compose.onNodeWithText(titles.first()).assertIsSelected().assertIsDisplayed()
    }

    @Test
    fun selectedTabStaysVisibleAcrossResizeFontScaleAndRtlChanges() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val width = mutableStateOf(640.dp)
        val fontScale = mutableStateOf(1f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val selected = mutableIntStateOf(7)
        val titles = List(8) { "Reading page $it" }
        compose.setContent {
            AppTheme(configuration, applyBackground = false) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
                    LocalLayoutDirection provides direction.value,
                ) {
                    Box(Modifier.width(width.value)) {
                        AppTabRow(titles, selected.intValue, { selected.intValue = it },
                            modifier = Modifier.testTag("tabs"),
                            trailingAction = { Box(Modifier.width(40.dp).height(36.dp).testTag("action")) })
                    }
                }
            }
        }
        compose.onNodeWithText(titles.last()).assertIsDisplayed().assertIsSelected()
        assertEquals(
            compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot.right,
            compose.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot.right,
            1f,
        )
        compose.runOnIdle {
            width.value = 280.dp
            fontScale.value = 1.5f
            direction.value = LayoutDirection.Rtl
        }
        compose.onNodeWithText(titles.last()).assertIsDisplayed().assertIsSelected()
        assertEquals(
            compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot.left,
            compose.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot.left,
            1f,
        )
        compose.runOnIdle { selected.intValue = 0 }
        compose.onNodeWithText(titles.first()).assertIsDisplayed().assertIsSelected()
    }

    private val configuration = AppUiConfiguration(
        appShell = AppShellSettings(composeEngine = "miuix"),
        theme = ThemeSettings(useHyperOs4Components = true),
    )
}
