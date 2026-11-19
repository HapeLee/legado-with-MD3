package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.OxygenGlassScrollBehavior
import io.oxygenx.ui.navigation.TopAppBarState
import io.oxygenx.ui.navigation.rememberTopAppBarState
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
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
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OxygenPullToRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun overlappingTopBarExpandsBeforeAUserPullAndInertiaDoesNotRefresh() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val dispatcher = NestedScrollDispatcher()
        lateinit var density: Density
        lateinit var topBar: TopAppBarState
        var refreshes = 0
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "oxygen"),
                    theme = ThemeSettings(useFlexibleTopAppBar = true),
                ),
                applyBackground = false,
            ) {
                density = LocalDensity.current
                val state = rememberTopAppBarState(initialCollapsedFraction = 1f)
                topBar = state
                val behavior = remember(state) { OxygenGlassScrollBehavior(state) }
                Column(Modifier.size(360.dp, 590.dp)) {
                    GlassMediumFlexibleTopAppBar("Page title", scrollBehavior = behavior)
                    AppPullToRefresh(
                        isRefreshing = false,
                        onRefresh = { refreshes++ },
                        modifier = Modifier.size(240.dp).nestedScroll(behavior.nestedScrollConnection),
                        scrollBehavior = behavior,
                        topPadding = 40.dp,
                    ) {
                        Box(Modifier.size(240.dp).nestedScroll(remember { object : NestedScrollConnection {} }, dispatcher))
                    }
                }
            }
        }
        compose.runOnIdle {
            dispatcher.coroutineScope.launch {
                val pull = Offset(0f, with(density) { 320.dp.toPx() })
                val velocity = Velocity(0f, 3000f)
                dispatcher.dispatchPostScroll(Offset.Zero, pull, NestedScrollSource.UserInput)
                assertEquals(0f, topBar.collapsedFraction, 0f)
                assertEquals(Offset.Zero, dispatcher.dispatchPostScroll(Offset.Zero, pull, NestedScrollSource.SideEffect))
                dispatcher.dispatchPreFling(velocity)
                dispatcher.dispatchPostFling(Velocity.Zero, velocity)
                assertEquals(0, refreshes)
                // Still in the same dispatch batch, before the expanded state can recompose the host.
                assertEquals(pull, dispatcher.dispatchPostScroll(Offset.Zero, pull, NestedScrollSource.UserInput))
                assertEquals(velocity, dispatcher.dispatchPreFling(velocity))
                dispatcher.dispatchPostFling(Velocity.Zero, velocity)
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }
}
