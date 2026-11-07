package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalMochiBackdropRegistry
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuLazy
import io.legado.app.ui.widget.components.mochi.AppMochiNavigationBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.M3GlassScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.topbar.TopBarActionsRow
import io.legado.app.ui.widget.components.icon.AppIcons
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.glass.LocalMochiBackdrop

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MochiOverlaysAndBarsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(glass: Boolean = false, merged: Boolean = false, content: @Composable () -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "mochi"),
                theme = ThemeSettings(topBarButtonStyle = if (glass) "liquid" else "tonal", mergeTopBarActions = merged),
            ), applyBackground = false) { Box(Modifier.fillMaxSize()) { content() } }
        }
    }

    @Test
    @Config(sdk = [26, 35])
    fun dialogKeepsValidationAndDoesNotAutoDismissAfterConfirm() {
        val enabled = mutableStateOf(false)
        val visible = mutableStateOf(true)
        var confirmed = 0
        var cancelled = 0
        var dismissed = 0
        var host: android.view.View? = null
        show {
            host = LocalView.current
            AppAlertDialog(
                show = visible.value, onDismissRequest = { dismissed++; visible.value = false },
                title = "Form", text = "Selectable message", confirmText = "Save", dismissText = "Cancel",
                confirmEnabled = enabled.value, onConfirm = { confirmed++ }, onDismiss = { cancelled++ },
            )
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, host!!.importantForAccessibility) }
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Form").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(1, confirmed); assertEquals(1, cancelled); assertEquals(0, dismissed)
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissed); assertNotEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, host!!.importantForAccessibility) }
    }

    @Test
    fun anchoredMenuKeepsSingleSelectionDisabledStateAndDismissCallback() {
        val expanded = mutableStateOf(true)
        var selections = 0
        show {
            Box(Modifier.size(80.dp)) {
                RoundDropdownMenu(expanded.value, { expanded.value = false }) { dismiss ->
                    RoundDropdownMenuItem("Chosen", isSelected = true, onClick = { selections++; dismiss() })
                    RoundDropdownMenuItem("Disabled", enabled = false, onClick = { selections += 100 })
                }
            }
        }
        compose.onNodeWithText("Chosen").assertIsSelected()
        compose.onNodeWithText("Disabled").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText("Chosen").performClick()
        compose.runOnIdle { assertEquals(1, selections); assertFalse(expanded.value) }
        compose.onNodeWithText("Chosen").assertDoesNotExist()
        compose.runOnIdle { expanded.value = true }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.Escape) }
        compose.runOnIdle { assertFalse(expanded.value); assertEquals(1, selections) }
    }

    @Test
    fun longMenuRemainsLazyAndCanReachTheLastEntry() {
        val expanded = mutableStateOf(true)
        var selected = -1
        show {
            Box(Modifier.size(48.dp)) {
                RoundDropdownMenuLazy(expanded.value, { expanded.value = false }, maxHeight = 240.dp) { dismiss ->
                    items(150) { index ->
                        RoundDropdownMenuItem("Entry $index", onClick = { selected = index; dismiss() })
                    }
                }
            }
        }
        compose.onAllNodesWithText("Entry 149").assertCountEquals(0)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(149)
        compose.onNodeWithText("Entry 149").performClick()
        compose.runOnIdle { assertEquals(149, selected) }
    }

    @Test
    fun nativeTabsKeepNamesWithoutLabelsAndHandleReselectPointerAndKeyboard() {
        val selected = mutableStateOf(0)
        var calls = 0
        show {
            AppMochiNavigationBar(
                persistentListOf(
                    MochiTabItem("Books", icon = { Text("B") }),
                    MochiTabItem("Settings", icon = { Text("S") }),
                ), selected.value, { selected.value = it; calls++ },
                Modifier.testTag("tabs"), showLabel = false,
            )
        }
        compose.onNodeWithContentDescription("Books").assertIsSelected().performClick()
        compose.onNodeWithContentDescription("Settings").performTouchInput { click() }
        compose.onNodeWithContentDescription("Settings").assertIsSelected()
        compose.onNodeWithTag("tabs").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("tabs").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithContentDescription("Books").assertIsSelected()
        compose.runOnIdle { assertEquals(3, calls) }
    }

    @Test
    fun samplingDisabledPagesMaskTheRegistryAndLocalSource() {
        val disabled = mutableStateOf(false)
        var localSource = false
        var registeredSource = false
        show(glass = true) {
            val registry = LocalMochiBackdropRegistry.current
            AppScaffold(disableContentSampling = disabled.value, topBar = {
                GlassTopAppBar("Title", actions = { TopBarActionButton({}, AppIcons.Search, "Search") })
            }) {
                val source = LocalMochiBackdrop.current
                SideEffect { localSource = source != null }
            }
            val source = registry?.current
            SideEffect { registeredSource = source != null }
        }
        compose.runOnIdle { assertTrue(localSource); assertTrue(registeredSource); disabled.value = true }
        compose.runOnIdle { assertFalse(localSource); assertFalse(registeredSource) }
    }

    @Test
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    fun compactLargeFontTitleReservesActionSlotsAndLargeTitleCollapses() {
        lateinit var behavior: io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
        var host: android.view.View? = null
        var clicks = 0
        show {
            host = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.8f)) {
                behavior = GlassTopAppBarDefaults.defaultScrollBehavior()
                GlassMediumFlexibleTopAppBar(
                    "A long bookshelf title", Modifier.width(320.dp).testTag("header"),
                    scrollBehavior = behavior,
                    navigationIcon = { TopBarNavigationButton({ clicks++ }, contentDescription = "Back") },
                    actions = {
                        TopBarActionButton({ clicks++ }, AppIcons.Search, "Search")
                        TopBarActionButton({ clicks++ }, AppIcons.Search, "More")
                    },
                )
            }
        }
        compose.onNodeWithText("A long bookshelf title").assertIsDisplayed()
        compose.runOnIdle {
            val bars = WindowInsetsCompat.Type.statusBars()
            host!!.dispatchApplyWindowInsets(WindowInsetsCompat.Builder()
                .setInsets(bars, Insets.of(20, 24, 28, 0))
                .setInsetsIgnoringVisibility(bars, Insets.of(20, 24, 28, 0))
                .setVisible(bars, true).build().toWindowInsets()!!)
        }
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithContentDescription("More").fetchSemanticsNode().boundsInRoot
        assertEquals("start inset plus the Catalog's 16dp margin", 36f, back.left - header.left, 0.5f)
        assertEquals("end inset plus the Catalog's 16dp margin", 44f, header.right - more.right, 0.5f)
        val expanded = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle {
            val state = (behavior as M3GlassScrollBehavior).m3Behavior.state
            assertTrue(state.heightOffsetLimit < 0)
            state.heightOffset = state.heightOffsetLimit
        }
        val collapsed = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot.height
        assertTrue(expanded > collapsed)
        val title = compose.onNodeWithText("A long bookshelf title").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        assertTrue(title.width > 0)
        assertTrue(title.right <= action.left)
        compose.onNodeWithContentDescription("More").performClick()
        // A tap just outside the native 44dp surface still uses Compose's expanded hit target.
        compose.onNodeWithTag("header").performTouchInput {
            click(Offset(back.left - header.left - 1f, back.center.y - header.top))
            click(Offset(more.right - header.left + 1f, more.center.y - header.top))
        }
        compose.runOnIdle { assertEquals(3, clicks) }
    }

    @Test fun standaloneBarButtonLongPressDoesNotAlsoClick() = verifyLongPress(false)
    @Test fun mergedBarButtonLongPressDoesNotAlsoClick() = verifyLongPress(true)

    private fun verifyLongPress(merged: Boolean) {
        var clicks = 0
        var longClicks = 0
        show(merged = merged) {
            TopBarActionsRow {
                TopBarActionButton(
                    { clicks++ }, AppIcons.Search, "Search",
                    onLongClick = { longClicks++ }, onLongClickLabel = "Filter",
                )
            }
        }
        val button = compose.onNodeWithContentDescription("Search")
        button.performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(1, longClicks) }
        button.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, longClicks) }
    }
}
