package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.itos.coui.scroll.CouiOverscrollEffect
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.config.themeConfig.TopBottomBarSettingsSheet
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.coui.rememberAppVerticalOverscrollEffect
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuLazy
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.tabRow.AppTabRowIconButton
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
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
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CouiFloatingAndMenusTest {
    @get:Rule val compose = createComposeRule()

    private fun setCouiContent(enableBlur: Boolean = false, content: @Composable () -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "coui"),
                    theme = ThemeSettings(enableBlur = enableBlur),
                ),
                content = content,
            )
        }
    }

    @Test
    fun verticalOverscrollHasAnIndependentNativeEffectForEachContainer() {
        var first: OverscrollEffect? = null
        var second: OverscrollEffect? = null
        setCouiContent {
            val firstEffect = rememberAppVerticalOverscrollEffect()
            val secondEffect = rememberAppVerticalOverscrollEffect()
            SideEffect {
                first = firstEffect
                second = secondEffect
            }
        }
        compose.runOnIdle {
            val effect = first as CouiOverscrollEffect
            val other = second as CouiOverscrollEffect
            assertNotSame(effect, other)
            effect.applyToScroll(Offset(0f, 32f), NestedScrollSource.UserInput) { Offset.Zero }
            assertTrue(effect.isInProgress)
            assertFalse(other.isInProgress)
        }
    }

    @Test
    fun normalAndLazyMenusPreserveCustomSlotsAndSelection() {
        val open = mutableStateOf("")
        var choices = 0
        setCouiContent {
            Column {
                Box {
                    TextButton(onClick = { open.value = "regular" }) { Text("Open regular") }
                    RoundDropdownMenu(open.value == "regular", { open.value = "" }) { dismiss ->
                        RoundDropdownMenuItem(
                            text = "Regular choice",
                            isSelected = true,
                            trailingIcon = { Text("Shortcut") },
                            onClick = { choices++; dismiss() },
                        )
                    }
                }
                Box {
                    TextButton(onClick = { open.value = "lazy" }) { Text("Open lazy") }
                    RoundDropdownMenuLazy(open.value == "lazy", { open.value = "" }, maxHeight = 160.dp) { dismiss ->
                        item {
                            RoundDropdownMenuItem("Disabled choice", onClick = {}, enabled = false)
                        }
                        item {
                            RoundDropdownMenuItem("Lazy choice", onClick = { choices++; dismiss() })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Open regular").performClick()
        compose.onNodeWithText("Shortcut").assertIsDisplayed()
        compose.onNodeWithText("Regular choice").assertIsSelected().performClick()
        compose.onNodeWithText("Open lazy").performClick()
        compose.onNodeWithText("Disabled choice").assertIsNotEnabled()
        compose.onNodeWithText("Lazy choice").performClick()
        compose.runOnIdle {
            assertEquals("", open.value)
            assertEquals(2, choices)
        }
    }

    @Test
    fun floatingNavigationWithoutADeviceRendererStillActivatesTheSelectedTab() {
        val activations = mutableListOf<Int>()
        val selected = mutableStateOf(0)
        setCouiContent {
            FloatingBottomBar(
                modifier = Modifier.width(240.dp),
                selectedIndex = { selected.value },
                onSelected = { activations.add(it) },
                onReselected = { activations.add(it) },
                backdrop = null,
                tabsCount = 5,
                isBlurEnabled = true,
            ) {
                listOf("Library", "Home", "Discover", "RSS", "My").forEachIndexed { index, title ->
                    FloatingBottomBarItem(
                        selected = index == selected.value,
                        onClick = { activations.add(index); selected.value = index },
                    ) { Text(title) }
                }
            }
        }
        compose.onNodeWithText("Library").assertIsDisplayed().assertIsSelected().performClick()
        compose.onNodeWithText("My").assertIsDisplayed().assertIsNotSelected().performClick()
        compose.onNodeWithText("My").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(0, 4), activations) }
    }

    @Test
    fun couiFloatingNavigationKeepsTheCaptionCloseToTheCapsuleBottom() {
        val compactProgress = mutableStateOf(0f)
        setCouiContent {
            FloatingBottomBar(
                modifier = Modifier.width(180.dp).testTag("Capsule"),
                selectedIndex = { 0 },
                onSelected = {},
                backdrop = null,
                tabsCount = 2,
                isBlurEnabled = false,
                compactProgress = compactProgress.value,
            ) {
                FloatingBottomBarItem(onClick = {}, selected = true) {
                    Box(Modifier.size(24.dp).testTag("Labeled icon"))
                    AppText(
                        text = "书架",
                        style = LegadoTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
                FloatingBottomBarItem(onClick = {}) {
                    Box(Modifier.size(24.dp).testTag("Unlabeled icon"))
                }
            }
        }

        val capsuleNode = compose.onNodeWithTag("Capsule")
        val capsule = capsuleNode.fetchSemanticsNode().boundsInRoot
        val caption = compose.onNodeWithText("书架", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val labeledIcon = compose.onNodeWithTag("Labeled icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("Unlabeled icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            assertEquals(56.dp.toPx(), capsule.height, 1f)
            assertEquals(labeledIcon.top - capsule.top, capsule.bottom - caption.bottom, 1f)
            assertEquals(capsule.center.y, icon.center.y, 1f)
            compose.runOnIdle { compactProgress.value = 0.25f }
            assertEquals(58.dp.toPx(), capsuleNode.fetchSemanticsNode().boundsInRoot.height, 1f)
            compose.runOnIdle { compactProgress.value = 1f }
            assertEquals(64.dp.toPx(), capsuleNode.fetchSemanticsNode().boundsInRoot.height, 1f)
        }
    }

    @Test
    fun couiLocksFloatingNavigationOnButKeepsItsGlassSetting() {
        val context = RuntimeEnvironment.getApplication()
        setCouiContent {
            TopBottomBarSettingsSheet(
                show = true,
                appShell = AppShellSettings(composeEngine = "coui", useFloatingBottomBar = false),
                theme = ThemeSettings(),
                isMiuixEngine = false,
                onDismissRequest = {},
                onIntent = {},
            )
        }

        compose.onNodeWithText(context.getString(R.string.floating_bottom_bar))
            .assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.floating_bottom_bar_liquid_glass))
            .assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.bottom_bar_opacity)).assertDoesNotExist()
    }

    @Test
    fun scrollableCouiTabsRevealInitialAndExternalSelection() {
        val selected = mutableStateOf(7)
        val clicks = mutableListOf<Int>()
        val titles = List(8) { "Category ${it + 1}" }
        setCouiContent(enableBlur = true) {
            AppScaffold(topBar = {
                AppTabRow(
                    titles, selected.value, { clicks.add(it) }, Modifier.width(200.dp),
                    trailingAction = {
                        AppTabRowIconButton(Icons.Filled.Menu, "Categories", { clicks.add(-1) })
                    },
                )
            }) {}
        }

        compose.onNode(hasContentDescription("Categories") and hasClickAction()).performClick()
        compose.onNodeWithText("Category 8").assertIsDisplayed().assertIsSelected().performClick()
        compose.runOnIdle { selected.value = 0 }
        compose.onNodeWithText("Category 1").assertIsDisplayed().assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(listOf(-1, 7, 0), clicks) }
    }

    @Test
    fun tabTrailingActionsHaveTheirOwnClickTargetAndKeepBothEdgesInset() {
        val engines = listOf("material", "miuix", "mochi", "coui")
        val activeEngine = mutableStateOf(engines.first())
        val showAction = mutableStateOf(true)
        val actions = mutableListOf<String>()
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            val engine = activeEngine.value
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = engine),
                theme = ThemeSettings(useHyperOs4Components = engine == "miuix"),
            )) {
                // The test compiler cannot render the Miuix squircle shader.
                CompositionLocalProvider(LocalSquircleEnabled provides false) {
                    Box(Modifier.width(280.dp).testTag("Header")) {
                        AppTabRow(
                            tabTitles = listOf("First", "Last"),
                            selectedTabIndex = 0,
                            onTabSelected = { error("Trailing action must not change tabs") },
                            isScrollable = false,
                            horizontalPadding = 12.dp,
                            trailingAction = if (showAction.value) {
                                {
                                    AppTabRowIconButton(
                                        icon = Icons.Filled.Menu,
                                        contentDescription = "Browse",
                                        onClick = { actions.add(engine) },
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        }

        val margin = with(compose.density) { 12.dp.toPx() }
        for (engine in engines) {
            val expectedMargin = if (engine == "material") 0f else margin
            val expectedActionMargin = if (engine == "material") with(compose.density) { 16.dp.toPx() } else margin
            compose.runOnIdle {
                activeEngine.value = engine
                showAction.value = true
            }
            val header = compose.onNodeWithTag("Header").fetchSemanticsNode().boundsInRoot
            val action = compose.onNode(hasContentDescription("Browse") and hasClickAction())
            val button = action.fetchSemanticsNode().boundsInRoot
            val first = compose.onNodeWithText("First").fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithText("Last").fetchSemanticsNode().boundsInRoot
            assertTrue("$engine button left edge: $button in $header", button.left >= header.left + expectedMargin - 1f)
            assertTrue("$engine action overlaps last tab: $button, $last", button.left >= last.right)
            assertTrue("$engine first tab left edge: $first in $header", first.left >= header.left + expectedMargin - 1f)
            assertTrue("$engine button right edge: $button in $header", button.right <= header.right - expectedActionMargin + 1f)
            assertTrue("$engine last tab right edge: $last in $header", last.right <= header.right - expectedMargin + 1f)
            if (engine != "mochi") {
                assertEquals(expectedMargin, first.left - header.left, 1f)
                assertEquals(expectedActionMargin, header.right - button.right, 1f)
                assertEquals(with(compose.density) { 8.dp.toPx() }, button.left - last.right, 1f)
            }
            action.performClick()
            compose.runOnIdle { showAction.value = false }
            compose.onNode(hasContentDescription("Browse")).assertDoesNotExist()
            val firstWithoutAction = compose.onNodeWithText("First").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val lastWithoutAction = compose.onNodeWithText("Last").fetchSemanticsNode().boundsInRoot
            assertTrue("$engine first tab left edge", firstWithoutAction.left >= header.left + expectedMargin - 1f)
            assertTrue("$engine last tab right edge", lastWithoutAction.right <= header.right - expectedMargin + 1f)
            if (engine != "mochi") {
                assertEquals(expectedMargin, firstWithoutAction.left - header.left, 1f)
                assertEquals(expectedMargin, header.right - lastWithoutAction.right, 1f)
            }
        }
        compose.runOnIdle { assertEquals(engines, actions) }
    }

    @Test
    fun materialAndCouiScrollViewportsUseContentInsetsInsteadOfOuterMargins() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val engine = mutableStateOf("material")
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val selected = mutableStateOf(0)
        val requestedPadding = mutableStateOf(12.dp)
        val showAction = mutableStateOf(false)
        val titles = List(8) { "Tab $it" }
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine.value))) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    Box(Modifier.width(240.dp).testTag("header")) {
                        AppTabRow(
                            titles, selected.value, { selected.value = it },
                            horizontalPadding = requestedPadding.value,
                            trailingAction = if (showAction.value) {
                                { AppTabRowIconButton(Icons.Filled.Menu, "Browse", {}) }
                            } else null,
                        )
                    }
                }
            }
        }
        for (value in listOf("material", "coui")) {
            for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle {
                    engine.value = value
                    direction.value = layoutDirection
                    selected.value = 0
                    requestedPadding.value = if (value == "material") 12.dp else 0.dp
                }
                val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
                val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
                assertEquals(header.left, viewport.left, 1f)
                assertEquals(header.right, viewport.right, 1f)
                val inset = with(compose.density) { (if (value == "coui") 12.dp else 0.dp).toPx() }
                val first = compose.onNodeWithText(titles.first()).assertIsDisplayed().assertIsSelected().fetchSemanticsNode().boundsInRoot
                assertEquals(inset, if (layoutDirection == LayoutDirection.Ltr) first.left - header.left else header.right - first.right, 1f)
                compose.runOnIdle { selected.value = titles.lastIndex }
                val last = compose.onNodeWithText(titles.last()).assertIsDisplayed().assertIsSelected().fetchSemanticsNode().boundsInRoot
                assertEquals(inset, if (layoutDirection == LayoutDirection.Ltr) header.right - last.right else last.left - header.left, 1f)
                if (value == "material") {
                    compose.runOnIdle { showAction.value = true }
                    val button = compose.onNode(hasContentDescription("Browse") and hasClickAction()).fetchSemanticsNode().boundsInRoot
                    val actionMargin = if (layoutDirection == LayoutDirection.Ltr) header.right - button.right else button.left - header.left
                    assertEquals(with(compose.density) { 16.dp.toPx() }, actionMargin, 1f)
                    compose.runOnIdle { showAction.value = false }
                }
            }
        }
    }

    @Test
    fun fixedCouiCardTabsKeepDuplicateTitlesAndCardTabActions() {
        val selected = mutableStateOf(1)
        var longPresses = 0
        var trailingClicks = 0
        setCouiContent {
            Column(Modifier.width(250.dp)) {
                CardTabRow(listOf("Same", "Same"), selected.value, { selected.value = it })
                CardTabRow(
                    tabTitles = listOf("Chapter", "Notes"),
                    selectedTabIndex = 0,
                    onTabSelected = {},
                    onTabLongClick = { longPresses++ },
                    tabEndContent = {
                        if (it == 1) TextButton(onClick = { trailingClicks++ }) { Text("Edit") }
                    },
                )
            }
        }
        compose.onNode(hasText("Same") and isSelected()).assertIsDisplayed()
        compose.onNode(hasText("Same") and !isSelected()).performClick()
        compose.runOnIdle { assertEquals(0, selected.value) }
        compose.onNode(hasText("Chapter") and hasClickAction()).performTouchInput { longClick() }
        compose.onNodeWithText("Edit").performClick()
        compose.runOnIdle {
            assertEquals(1, longPresses)
            assertEquals(1, trailingClicks)
        }
    }
}
