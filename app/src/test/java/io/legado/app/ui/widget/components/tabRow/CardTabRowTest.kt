package io.legado.app.ui.widget.components.tabRow

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardTabRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun os4KeepsCatalogSegmentedTabsWithAndWithoutBackdrop() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val selected = mutableIntStateOf(0)
        val sampling = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "miuix"),
                    theme = ThemeSettings(useHyperOs4Components = true),
                ),
                applyBackground = false,
            ) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.width(320.dp)) {
                    // Record only the background sibling, never the tabs that sample it.
                    Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(Color.White))
                    CompositionLocalProvider(LocalMiuixBarBackdrop provides if (sampling.value) backdrop else null) {
                        CardTabRow(
                            tabTitles = listOf("Privacy", "Security"),
                            selectedTabIndex = selected.intValue,
                            onTabSelected = { selected.intValue = it; clicks++ },
                            modifier = Modifier.testTag("tabs"),
                        )
                    }
                }
            }
        }
        for (withBackdrop in listOf(false, true, false)) {
            compose.runOnIdle { sampling.value = withBackdrop }
            compose.onNodeWithTag("tabs").assertHeightIsEqualTo(GlassSegmentedTabRowDefaults.Height)
            compose.onNodeWithText("Security").assertHasClickAction().performClick().assertIsSelected()
            compose.onNodeWithText("Security").performClick().assertIsSelected()
            compose.runOnIdle { selected.intValue = 0 }
            compose.onNodeWithText("Privacy").assertIsSelected()
        }
        compose.runOnIdle { assertEquals(6, clicks) }
    }
}
