package io.legado.app.ui.widget.components.icon

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.AppTheme
import io.oxygenx.icons.material.MaterialSymbols
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppIconsOxygenTest {
    @get:Rule val compose = createComposeRule()

    private val actionNames = ("search more_vert edit delete close arrow_back_ios_new filter_alt settings " +
            "bug_report my_location location_searching history replay more_horiz check add info sell " +
            "skip_previous skip_next speed list fullscreen fullscreen_exit picture_in_picture_alt open_in_new expand_more").split(' ')
    private val destinationNames = listOf("home", "library_books", "explore", "rss_feed", "person")

    @Composable
    private fun actions(): List<ImageVector> = listOf(
        AppIcons.Search, AppIcons.MoreVert, AppIcons.Edit, AppIcons.Delete,
        AppIcons.Close, AppIcons.Back, AppIcons.Filter, AppIcons.Settings,
        AppIcons.BugReport, AppIcons.PrecisionSearch, AppIcons.UnPrecisionSearch,
        AppIcons.History, AppIcons.Replay, AppIcons.MoreCircle, AppIcons.Check,
        AppIcons.Add, AppIcons.Info, AppIcons.Group, AppIcons.Previous, AppIcons.Next,
        AppIcons.Speed, AppIcons.Episodes, AppIcons.Fullscreen, AppIcons.FullscreenExit,
        AppIcons.PictureInPicture, AppIcons.MiniWindow, AppIcons.ExpandMore,
    )

    @Test
    fun everyActionAndDestinationUsesOutlinedSymbolsAndReactsToEngineChanges() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val engine = mutableStateOf("oxygen")
        var actual = emptyList<ImageVector>()
        var destinations = emptyList<ImageVector>()
        var selected = emptyList<ImageVector>()
        var leading: ImageVector? = null
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine.value)), applyBackground = false) {
                val icons = actions()
                val normal = MainDestination.mainDestinations.map { AppIcons.mainDestination(it, false) }
                val active = MainDestination.mainDestinations.map { AppIcons.mainDestination(it, true) }
                val start = AppIcons.leadingOrNull(icons.first())
                SideEffect { actual = icons; destinations = normal; selected = active; leading = start }
            }
        }
        for (id in listOf("oxygen", "material", "miuix", "coui", "mochi", "oxygen")) {
            compose.runOnIdle { engine.value = id }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(27, actual.size)
                if (id == "oxygen") {
                    assertEquals(actionNames, actual.map { it.name })
                    assertTrue(actual.all { icon -> MaterialSymbols.All.any { it === icon } })
                    assertEquals(destinationNames, destinations.map { it.name })
                    destinations.zip(selected).forEach { (normal, active) -> assertSame(normal, active) }
                    assertTrue(actual[5].autoMirror)
                    assertTrue(actual[21].autoMirror)
                    assertTrue(destinations[1].autoMirror)
                    assertFalse(actual[18].autoMirror)
                    assertFalse(actual[19].autoMirror)
                    assertSame(actual.first(), leading)
                } else {
                    assertTrue(actual.none { icon -> MaterialSymbols.All.any { it === icon } })
                    if (id == "coui") assertEquals(null, leading) else assertSame(actual.first(), leading)
                    if (id == "material" || id == "mochi") assertTrue(actual.first().name.contains("Search"))
                }
            }
        }
    }

    @Test
    fun actualAppIconRendersTheCollectionAndMirrorsDirectionalVectorsInRtl() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "oxygen")), applyBackground = false) {
                Column(Modifier.background(Color.White).testTag("sheet").padding(8.dp)) {
                    val all = actions() + MainDestination.mainDestinations.map { AppIcons.mainDestination(it, false) }
                    all.chunked(6).forEach { row ->
                        Row {
                            row.forEach { vector ->
                                AppIcon(vector, vector.name, Modifier.testTag("glyph_${vector.name}").padding(8.dp), tint = Color.Black)
                            }
                        }
                    }
                    Row {
                        AppIcon(AppIcons.Back, "Back", Modifier.testTag("ltr"), tint = Color.Black)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            AppIcon(AppIcons.Back, "Back", Modifier.testTag("rtl"), tint = Color.Black)
                        }
                    }
                }
            }
        }
        (actionNames + destinationNames).forEach { name ->
            compose.onNodeWithTag("glyph_$name").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
        }
        val ltr = compose.onNodeWithTag("ltr").captureToImage().toPixelMap()
        val rtl = compose.onNodeWithTag("rtl").captureToImage().toPixelMap()
        var ink = 0
        for (y in 0 until ltr.height) for (x in 0 until ltr.width) {
            assertEquals(ltr[x, y], rtl[ltr.width - 1 - x, y])
            if (ltr[x, y] != Color.White) ink++
        }
        assertTrue("Back glyph must draw visible paths", ink > 0)
        val output = File("build/outputs/oxygenx-app-icons.png")
        checkNotNull(output.parentFile).mkdirs()
        output.outputStream().use {
            compose.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
