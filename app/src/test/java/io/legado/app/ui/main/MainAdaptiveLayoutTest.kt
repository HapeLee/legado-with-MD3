@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package io.legado.app.ui.main

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.ui.widget.components.navigation.MultiPaneMode
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainAdaptiveLayoutTest {
    @Test
    fun startupAndTinyWindowsNeverProduceNegativeContentDimensions() {
        assertEquals(Rect.Zero, mainContentBounds(DpSize.Zero, 20.dp, 24.dp, 20.dp, 24.dp))
        val bounds = mainContentBounds(DpSize(40.dp, 40.dp), 24.dp, 24.dp, 24.dp, 24.dp)
        assertEquals(0f, bounds.width)
        assertEquals(0f, bounds.height)
    }

    @Test
    fun strategyRejectsInsufficientSpaceAndRestoresSameEntriesAfterResize() {
        var env = environment(width = 900f, railWidth = 240f)
        val home =
            NavEntry<NavKey>(MainRouteHome, metadata = ListDetailSceneStrategy.containerPane()) {}
        val settings = NavEntry<NavKey>(
            MainRouteSettings,
            metadata = ListDetailSceneStrategy.listAndDetailPane()
        ) {}
        val entries = listOf(home, settings)
        val strategy = ListDetailSceneStrategy(isEnabled = { true }, environment = { env })
        val scope = SceneStrategyScope<NavKey>()
        assertNull(with(strategy) { scope.calculateScene(entries) })
        env = environment(width = 1100f, railWidth = 240f)
        val scene =
            checkNotNull(with(strategy) { scope.calculateScene(entries) } as? ListDetailScene)
        assertEquals(entries, scene.entries)
        assertNotNull(scene.metadata[NavDisplay.TransitionKey])
        assertNotNull(scene.metadata[NavDisplay.PopTransitionKey])
        assertNotNull(scene.metadata[NavDisplay.PredictivePopTransitionKey])
        assertTrue(checkNotNull(scene.layout).listBounds.width >= 600f)
    }

    private fun environment(
        width: Float = 1000f,
        height: Float = 1000f,
        mode: MultiPaneMode = MultiPaneMode.Auto,
        railWidth: Float = 0f,
        bounds: Rect = Rect(0f, 0f, width, height),
        vararg hinges: HingeInfo,
    ) = MainAdaptiveLayoutEnvironment(
        bounds, DpSize(width.dp, height.dp),
        hinges.toList().toImmutableList(), mode, railWidth.dp
    )

    private fun fold(
        bounds: Rect,
        vertical: Boolean,
        separating: Boolean = true,
        occluding: Boolean = false
    ) =
        HingeInfo(bounds, false, vertical, separating, occluding)

    @Test
    fun expandedSidebarMustLeaveEnoughActualContentSpace() {
        assertNull(environment(width = 900f, railWidth = 240f).paneLayout(true))
        val layout = checkNotNull(environment(width = 1000f, railWidth = 240f).paneLayout(true))
        assertTrue(layout.listBounds.width - 240f >= MainPaneMinWidth)
        assertTrue(layout.detailBounds.width >= MainPaneMinWidth)
        assertNotNull(environment(width = 900f, railWidth = 240f).paneLayout(false))
    }

    @Test
    fun alwaysModeCannotOverrideMinimumContentOrHeight() {
        assertNull(
            environment(
                width = 839f,
                railWidth = 96f,
                mode = MultiPaneMode.Always
            ).paneLayout(true)
        )
        assertNotNull(
            environment(
                width = 840f,
                railWidth = 96f,
                mode = MultiPaneMode.Always
            ).paneLayout(true)
        )
        assertNull(environment(height = 319f, mode = MultiPaneMode.Always).paneLayout(false))
        assertNotNull(environment(height = 320f, mode = MultiPaneMode.Always).paneLayout(false))
    }

    @Test
    fun insetsAreExcludedFromMinimumSpaceAndRetainedInCoordinates() {
        assertNull(environment(width = 860f, bounds = Rect(24f, 0f, 836f, 1000f)).paneLayout(false))
        val layout =
            checkNotNull(environment(bounds = Rect(24f, 40f, 976f, 980f)).paneLayout(false))
        assertEquals(24f, layout.listBounds.left)
        assertEquals(40f, layout.listBounds.top)
        assertEquals(980f, layout.detailBounds.bottom)
    }

    @Test
    fun verticalSeparatingHingePlacesPanesOnPhysicalSides() {
        val hinge = fold(Rect(490f, 0f, 510f, 1000f), true, occluding = true)
        val env = environment(hinges = arrayOf(hinge))
        val layout = checkNotNull(env.paneLayout(false))
        assertTrue(layout.usesHinge)
        assertTrue(layout.listBounds.right < hinge.bounds.left)
        assertTrue(layout.detailBounds.left > hinge.bounds.right)
        assertEquals(478f, layout.listBounds.width)
    }

    @Test
    fun zeroWidthFoldStillSeparatesRegions() {
        val env =
            environment(width = 800f, hinges = arrayOf(fold(Rect(400f, 0f, 400f, 1000f), true)))
        assertNotNull(env.paneLayout(false))
        assertEquals(2, env.safeRegions().size)
    }

    @Test
    fun tabletopUsesTopAndBottomRegions() {
        val hinge = fold(Rect(0f, 490f, 1000f, 510f), false)
        val layout = checkNotNull(environment(hinges = arrayOf(hinge)).paneLayout(false))
        assertTrue(layout.listBounds.bottom < hinge.bounds.top)
        assertTrue(layout.detailBounds.top > hinge.bounds.bottom)
        assertEquals(1000f, layout.detailBounds.width)
    }

    @Test
    fun narrowHingeSideAndShortTabletopFallBackToLargestSafeRegion() {
        val env = environment(hinges = arrayOf(fold(Rect(300f, 0f, 320f, 1000f), true)))
        assertNull(env.paneLayout(false))
        assertEquals(Rect(332f, 0f, 1000f, 1000f), env.singlePaneBounds())
        val short =
            environment(height = 600f, hinges = arrayOf(fold(Rect(0f, 300f, 1000f, 300f), false)))
        assertNull(short.paneLayout(false))
    }

    @Test
    fun flatNonSeparatingFoldDoesNotChangeLayout() {
        val hinge = fold(Rect(500f, 0f, 500f, 1000f), true, separating = false)
        val env = environment(hinges = arrayOf(hinge))
        assertEquals(1, env.safeRegions().size)
        assertFalse(checkNotNull(env.paneLayout(false)).usesHinge)
    }

    @Test
    fun offAndLandscapePreferencesStillApplyWhenFolded() {
        val hinge = fold(Rect(500f, 0f, 500f, 1200f), true)
        assertNull(
            environment(
                height = 1200f,
                mode = MultiPaneMode.Landscape,
                hinges = arrayOf(hinge)
            ).paneLayout(false)
        )
        assertNull(environment(mode = MultiPaneMode.Off, hinges = arrayOf(hinge)).paneLayout(false))
    }

    @Test
    fun multipleHingesNeverCreatePanesAcrossOcclusion() {
        val env = environment(
            width = 1500f, hinges = arrayOf(
                fold(Rect(490f, 0f, 510f, 1000f), true),
                fold(Rect(990f, 0f, 1010f, 1000f), true),
            )
        )
        assertEquals(3, env.safeRegions().size)
        val layout = checkNotNull(env.paneLayout(false))
        assertTrue(layout.listBounds.right <= 478f)
        assertTrue(layout.detailBounds.left >= 522f)
        assertTrue(layout.detailBounds.right <= 978f)
    }
}
