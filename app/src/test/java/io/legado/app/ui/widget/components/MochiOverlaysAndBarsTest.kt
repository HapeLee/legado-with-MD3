package io.legado.app.ui.widget.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.feature.settings.search.SettingsSearchCatalog
import io.legado.app.feature.settings.search.SettingsSearchTargetScope
import io.legado.app.ui.config.themeConfig.ThemeConfigIntent
import io.legado.app.ui.config.themeConfig.ThemeConfigScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigUiState
import io.legado.app.ui.config.themeConfig.TopBottomBarSettingsSheet
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalMochiBackdropRegistry
import io.legado.app.ui.theme.LocalMochiScrollBlurProgress
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.LocalTopBarBackdrop
import io.legado.app.ui.book.changesource.ChangeSourceMigrationOptionsSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuLazy
import io.legado.app.ui.widget.components.mochi.AppMochiNavigationBar
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.MochiSheetLayout
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
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
import ui.feice.mochi.theme.MochiTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MochiOverlaysAndBarsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        glass: Boolean = false,
        merged: Boolean = false,
        theme: ThemeSettings = ThemeSettings(),
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "mochi"),
                theme = theme.copy(topBarButtonStyle = if (glass) "liquid" else "tonal", mergeTopBarActions = merged),
            ), applyBackground = false) { Box(Modifier.fillMaxSize()) { content() } }
        }
    }

    @Test
    fun listsUseMochiEdgeTranslationAndSettleInBothDirections() {
        show {
            Column {
                LazyColumn(Modifier.height(160.dp).fillMaxWidth().testTag("vertical-list")) {
                    items(10) { index ->
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("vertical-$index"))
                    }
                }
                LazyRow(Modifier.fillMaxWidth().testTag("horizontal-list")) {
                    items(10) { index ->
                        Box(Modifier.size(64.dp).testTag("horizontal-$index"))
                    }
                }
            }
        }

        val firstRow = compose.onNodeWithTag("vertical-0")
        val verticalList = compose.onNodeWithTag("vertical-list")
        val top = firstRow.fetchSemanticsNode().boundsInRoot.top
        verticalList.performTouchInput {
            down(center)
            moveBy(Offset(0f, 48.dp.toPx()))
        }
        assertTrue(firstRow.fetchSemanticsNode().boundsInRoot.top > top)
        verticalList.performTouchInput { up() }
        compose.waitUntil(timeoutMillis = 5_000) { firstRow.fetchSemanticsNode().boundsInRoot.top == top }

        val firstCard = compose.onNodeWithTag("horizontal-0")
        val horizontalList = compose.onNodeWithTag("horizontal-list")
        val left = firstCard.fetchSemanticsNode().boundsInRoot.left
        horizontalList.performTouchInput {
            down(center)
            moveBy(Offset(48.dp.toPx(), 0f))
        }
        assertTrue(firstCard.fetchSemanticsNode().boundsInRoot.left > left)
        horizontalList.performTouchInput { up() }
        compose.waitUntil(timeoutMillis = 5_000) { firstCard.fetchSemanticsNode().boundsInRoot.left == left }
    }

    @Test
    fun pullToRefreshStillReceivesTheDragAtTheTop() {
        val refreshing = mutableStateOf(false)
        var refreshes = 0
        show {
            AppPullToRefresh(
                isRefreshing = refreshing.value,
                onRefresh = {
                    refreshes++
                    refreshing.value = true
                },
                modifier = Modifier.height(300.dp).fillMaxWidth(),
            ) {
                LazyColumn(Modifier.fillMaxSize().testTag("refresh-list")) {
                    items(12) { index ->
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("refresh-$index"))
                    }
                }
            }
        }
        compose.onNodeWithTag("refresh-list").performTouchInput {
            swipeDown(startY = height * 0.1f, endY = height * 0.9f)
        }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun cardTabsKeepSelectionForPointerAndKeyboard() {
        val selected = mutableStateOf(0)
        show {
            CardTabRow(
                tabTitles = listOf("By minute", "By chapter"),
                selectedTabIndex = selected.value,
                onTabSelected = { selected.value = it },
                modifier = Modifier.testTag("card-tabs"),
            )
        }
        compose.onNodeWithText("By minute").assertIsSelected()
        compose.onNodeWithText("By chapter").performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithTag("card-tabs")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("By minute").assertIsSelected()
    }

    @Test
    fun groupedSettingsDrawADividerOnlyBetweenVisibleRows() {
        val showFirst = mutableStateOf(true)
        var clicks = 0
        show(theme = ThemeSettings(enableItemDivider = true, itemDividerColor = 0xFFFF0000.toInt())) {
            SplicedColumnGroup(Modifier.width(280.dp).testTag("settings-group")) {
                if (showFirst.value) SettingItem(title = "First", onClick = {})
                SettingItem(title = "Second", onClick = { clicks++ })
            }
        }
        fun hasDivider(): Boolean {
            val pixels = compose.onNodeWithTag("settings-group").captureToImage().toPixelMap()
            return (0 until pixels.height).any { y ->
                val color = pixels[pixels.width / 2, y]
                color.red > 0.9f && color.green < 0.8f && color.blue < 0.8f
            }
        }
        assertTrue(hasDivider())
        compose.onNodeWithText("Second").performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            showFirst.value = false
        }
        assertFalse(hasDivider())
    }

    @Test
    fun sourceMigrationConfirmationCanBeReachedInTheHalfSheet() {
        var confirmations = 0
        show {
            ChangeSourceMigrationOptionsSheet(
                show = true,
                title = "Migration options",
                subtitle = "Choose the information to migrate",
                onDismissRequest = {},
                onConfirm = { confirmations++ },
            )
        }
        val confirm = RuntimeEnvironment.getApplication().getString(android.R.string.ok)
        compose.onNode(hasText(confirm) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun halfSheetDoesNotAddStatusBarSpaceBetweenTitleAndContent() {
        var sheetView: android.view.View? = null
        show {
            AppModalBottomSheet(show = true, onDismissRequest = {}, title = "Sheet heading") {
                sheetView = LocalView.current
                Box(Modifier.fillMaxWidth().height(32.dp).testTag("sheet-first-content"))
            }
        }
        val heading = compose.onNodeWithText("Sheet heading")
        val content = compose.onNodeWithTag("sheet-first-content")
        fun gap() = content.fetchSemanticsNode().boundsInRoot.top -
            heading.fetchSemanticsNode().boundsInRoot.bottom
        val originalGap = gap()
        compose.runOnIdle {
            val type = WindowInsetsCompat.Type.statusBars()
            val status = Insets.of(0, 48, 0, 0)
            requireNotNull(sheetView).dispatchApplyWindowInsets(
                WindowInsetsCompat.Builder()
                    .setInsets(type, status)
                    .setInsetsIgnoringVisibility(type, status)
                    .setVisible(type, true)
                    .build().toWindowInsets()!!
            )
        }
        assertEquals(originalGap, gap(), 0.5f)
    }

    @Test
    fun defaultSheetStopsAtHalfHeightAndLetsTheListScroll() {
        show {
            Box(Modifier.fillMaxSize().testTag("behind-half-sheet"))
            AppModalBottomSheet(show = true, onDismissRequest = {}, title = "Half-height settings") {
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("half-sheet-list")) {
                    items(40) { index ->
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("half-item-$index"))
                    }
                }
            }
        }

        val background = compose.onNodeWithTag("behind-half-sheet").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Half-height settings").assertIsDisplayed()
        assertTrue(title.fetchSemanticsNode().boundsInRoot.top >= background.center.y)
        val list = compose.onNodeWithTag("half-sheet-list")
        list.performScrollToIndex(39)
        compose.onNodeWithTag("half-item-39").assertIsDisplayed()
        assertTrue(title.fetchSemanticsNode().boundsInRoot.top >= background.center.y)
    }

    @Test
    fun fullScreenSheetLetsItsContentHandleBackFirst() {
        val visible = mutableStateOf(false)
        val editing = mutableStateOf(true)
        var dismissals = 0
        var activity: ComponentActivity? = null
        show {
            activity = LocalActivity.current as? ComponentActivity
            Box(Modifier.fillMaxSize().testTag("behind-sheet"))
            AppModalBottomSheet(
                show = visible.value,
                onDismissRequest = { dismissals++; visible.value = false },
                title = "Settings",
                contentPaddingEnabled = false,
                animateContentSize = false,
                mochiLayout = MochiSheetLayout.FullScreen,
            ) {
                BackHandler(enabled = editing.value) { editing.value = false }
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("settings-list")) {
                    items(50) { index ->
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("item-$index"))
                    }
                }
            }
        }

        val background = compose.onNodeWithTag("behind-sheet")
        val originalBounds = background.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { visible.value = true }
        val header = compose.onNodeWithText("Settings").assertIsDisplayed()
        assertTrue(header.fetchSemanticsNode().boundsInRoot.top < originalBounds.height / 4)
        assertTrue(background.fetchSemanticsNode().boundsInRoot.width < originalBounds.width)
        compose.onNodeWithTag("settings-list").performScrollToIndex(49)
        compose.onNodeWithTag("item-49").assertIsDisplayed()

        compose.runOnIdle { requireNotNull(activity).onBackPressedDispatcher.onBackPressed() }
        header.assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(editing.value)
            assertEquals(0, dismissals)
            requireNotNull(activity).onBackPressedDispatcher.onBackPressed()
        }
        header.assertDoesNotExist()
        assertEquals(originalBounds, background.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun switchingFromMochiToMaterialUsesASeparatePopupWindow() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val target = requireNotNull(SettingsSearchCatalog.find("theme.compose_engine"))
        val material = app.resources.getStringArray(R.array.composeEngine).first()
        val shell = mutableStateOf(AppShellSettings(composeEngine = "mochi"))
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = shell.value)) {
                SettingsSearchTargetScope(target) {
                    ThemeConfigScreen(
                        ThemeConfigUiState(appShell = shell.value),
                        { intent ->
                            if (intent is ThemeConfigIntent.SetComposeEngine) {
                                shell.value = shell.value.copy(composeEngine = intent.value)
                            }
                        },
                        {}, {}, {},
                    )
                }
            }
        }

        val row = hasAnyAncestor(hasTestTag("setting:theme.compose_engine"))
        val picker = compose.onNode(hasText(app.getString(R.string.compose_engine)) and hasClickAction() and row)
        picker.performTouchInput { click() }
        compose.onNode(hasText(material) and hasAnyAncestor(isPopup())).assertIsDisplayed().performTouchInput { click() }
        compose.onNode(isPopup()).assertDoesNotExist()
        picker.assertIsDisplayed().assertTextContains(material)
        compose.runOnIdle { assertEquals("material", shell.value.composeEngine) }
    }

    @Test
    fun settingsDropdownsTrackTheirValueAndDismissInsideTheSheet() {
        val value = mutableStateOf("one")
        var activity: ComponentActivity? = null
        var sheetDismissals = 0
        show {
            activity = LocalActivity.current as? ComponentActivity
            AppModalBottomSheet(show = true, onDismissRequest = { sheetDismissals++ }, title = "Options") {
                TinyDropdownSettingItem(
                    title = "Compact choice",
                    selectedValue = value.value,
                    displayEntries = arrayOf("1", "Automatic"),
                    entryValues = arrayOf("one", "auto"),
                    onValueChange = { value.value = it },
                )
                DropdownListSettingItem(
                    title = "Display quality",
                    selectedValue = "low",
                    displayEntries = arrayOf("Low", "High"),
                    entryValues = arrayOf("low", "high"),
                    onValueChange = {},
                )
            }
        }

        val chip = compose.onNodeWithText("1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Compact choice").performTouchInput { click() }
        val automatic = compose.onNodeWithText("Automatic").assertIsDisplayed()
        assertTrue(
            "The Tiny menu should extend to the selected value at the end of the row",
            automatic.fetchSemanticsNode().boundsInRoot.right > chip.center.x,
        )
        automatic.performClick()
        compose.runOnIdle { assertEquals("auto", value.value) }

        val option = compose.onNodeWithText("Low", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val quality = compose.onNodeWithText("Display quality")
        quality.performTouchInput { click() }
        val high = compose.onNodeWithText("High").assertIsDisplayed()
        assertTrue(
            "The regular menu should open near the current value below its label",
            high.fetchSemanticsNode().boundsInRoot.left < option.center.x,
        )
        compose.runOnIdle { requireNotNull(activity).onBackPressedDispatcher.onBackPressed() }
        high.assertDoesNotExist()
        quality.assertIsDisplayed().performTouchInput { click() }
        high.assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, sheetDismissals) }
    }

    @Test
    fun nativeSheetKeepsDropdownAndReportsOnlyUserDismissal() {
        val visible = mutableStateOf(true)
        val choice = mutableStateOf("manual")
        var dismissals = 0
        show {
            AppModalBottomSheet(
                show = visible.value,
                onDismissRequest = { dismissals++; visible.value = false },
                title = "Timer",
                mochiLayout = MochiSheetLayout.Compact,
            ) {
                TinyDropdownSettingItem(
                    title = "Mode",
                    selectedValue = choice.value,
                    displayEntries = arrayOf("Manual", "Automatic"),
                    entryValues = arrayOf("manual", "automatic"),
                    onValueChange = { choice.value = it },
                )
            }
        }
        compose.onNodeWithText("Mode").assertIsDisplayed().performClick()
        compose.onNodeWithText("Automatic").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("automatic", choice.value) }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Timer"))
            .performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Mode").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissals)
            visible.value = true
        }
        compose.onNodeWithText("Mode").assertIsDisplayed()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithText("Mode").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals) }
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
        show {
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
    fun mochiTopBarGlassCanTurnOffWhileTheBottomBarStillUsesGlass() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val theme = mutableStateOf(ThemeSettings())
        var topBarGlass = false
        var bottomBarGlass = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "mochi", useFloatingBottomBarLiquidGlass = true),
                theme = theme.value,
            )) {
                AppScaffold(
                    topBar = {
                        val glass = MochiTheme.materials.controlGlass != null && LocalMochiBackdrop.current != null
                        SideEffect { topBarGlass = glass }
                        GlassTopAppBar("Title", actions = { TopBarActionButton({}, AppIcons.Search, "Search") })
                    },
                    bottomBar = {
                        val glass = MochiTheme.materials.bar != null && LocalMochiBackdrop.current != null
                        SideEffect { bottomBarGlass = glass }
                    },
                ) {}
            }
        }
        compose.runOnIdle {
            assertTrue(topBarGlass)
            assertTrue(bottomBarGlass)
            theme.value = theme.value.copy(mochiTopBarLiquidGlass = false)
        }
        compose.runOnIdle {
            assertFalse(topBarGlass)
            assertTrue(bottomBarGlass)
        }
    }

    @Test
    fun miuixTopBarGlassUsesItsOwnPreferenceAndRespectsSamplingDisabled() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val theme = mutableStateOf(ThemeSettings(topBarButtonStyle = "liquid"))
        val samplingDisabled = mutableStateOf(false)
        var topBarGlass = false
        var materialSampling = false
        var clicks = 0
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = theme.value,
            )) {
                AppScaffold(disableContentSampling = samplingDisabled.value, topBar = {
                    val hasBackdrop = LocalMiuixBarBackdrop.current != null
                    val material = LocalTopBarBackdrop.current != null || LocalHazeState.current != null
                    SideEffect {
                        topBarGlass = hasBackdrop
                        materialSampling = material
                    }
                    GlassTopAppBar("Title", actions = { TopBarActionButton({ clicks++ }, AppIcons.Search, "Search") })
                }) {}
            }
        }
        compose.runOnIdle {
            assertFalse(topBarGlass)
            theme.value = theme.value.copy(miuixTopBarLiquidGlass = true)
        }
        compose.onNodeWithContentDescription("Search").performTouchInput { click() }
        compose.runOnIdle {
            assertTrue(topBarGlass)
            assertFalse(materialSampling)
            assertEquals(1, clicks)
            theme.value = theme.value.copy(miuixTopBarLiquidGlass = false, enableBlur = true, enableProgressiveBlur = true)
        }
        compose.runOnIdle {
            assertTrue(topBarGlass)
            assertFalse(materialSampling)
            samplingDisabled.value = true
        }
        compose.runOnIdle { assertFalse(topBarGlass) }
    }

    @Test
    fun mochiEnablesScrollEdgeBlurByDefaultWithoutRecordingAnotherBackdrop() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val theme = mutableStateOf(ThemeSettings(mochiTopBarLiquidGlass = false))
        var edgeBlur = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "mochi"),
                theme = theme.value,
            )) {
                AppScaffold(topBar = {
                    check(LocalHazeState.current == null)
                    check(LocalTopBarBackdrop.current == null)
                    check(LocalMiuixBarBackdrop.current == null)
                    check(LocalMochiBackdrop.current == null)
                    val active = LocalMochiScrollBlurProgress.current != null && MochiTheme.materials.scrollEdge != null
                    SideEffect { edgeBlur = active }
                    GlassTopAppBar("Native edge blur")
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding))
                }
            }
        }
        compose.runOnIdle {
            assertTrue(edgeBlur)
            theme.value = theme.value.copy(
                mochiScrollEdgeBlurEnabled = false,
                enableBlur = true,
                enableProgressiveBlur = true,
            )
        }
        compose.runOnIdle { assertFalse(edgeBlur) }
    }

    @Test
    fun materialScaffoldOnlyProvidesMaterialBlurSources() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(theme = ThemeSettings(
                enableBlur = true,
                enableProgressiveBlur = true,
                topBarButtonStyle = "liquid",
                mochiTopBarLiquidGlass = true,
                miuixTopBarLiquidGlass = true,
            ))) {
                AppScaffold(topBar = {
                    check(LocalHazeState.current != null)
                    check(LocalTopBarBackdrop.current != null)
                    check(LocalMiuixBarBackdrop.current == null)
                    check(LocalMochiBackdrop.current == null)
                    check(LocalMochiScrollBlurProgress.current == null)
                    GlassTopAppBar("Material blur")
                }) {}
            }
        }
        compose.onNodeWithText("Material blur").assertIsDisplayed()
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
    @Test fun savedMergePreferenceDoesNotChangeLongPress() = verifyLongPress(true)

    @Test
    fun mochiBarSettingsHideMaterialButtonStyleAndMerge() {
        val app = RuntimeEnvironment.getApplication()
        val theme = mutableStateOf(ThemeSettings(topBarButtonStyle = "liquid", mergeTopBarActions = true))
        show(glass = true, merged = true) {
            TopBottomBarSettingsSheet(
                show = true,
                appShell = AppShellSettings(composeEngine = "mochi"),
                theme = theme.value,
                isMiuixEngine = false,
                onDismissRequest = {},
                onIntent = { if (it is ThemeConfigIntent.UpdateTheme) theme.value = it.transform(theme.value) },
            )
        }
        compose.onNodeWithText(app.getString(R.string.show_bottom_nav)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.top_bar_button_style)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.merge_top_bar_actions)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.top_bar_button_liquid_glass)).assertIsOn().performClick()
        compose.runOnIdle { assertFalse(theme.value.mochiTopBarLiquidGlass) }
    }

    @Test
    fun mochiActionSlotIgnoresTheMaterialMergePreference() {
        val showActions = mutableStateOf(false)
        var slotSize = IntSize.Zero
        show(glass = true, merged = true) {
            TopBarActionsRow(Modifier.onSizeChanged { slotSize = it }) {
                if (showActions.value) {
                    TopBarActionButton({}, AppIcons.Search, "Search")
                    TopBarActionButton({}, AppIcons.Search, "More", onLongClick = {})
                }
            }
        }
        compose.runOnIdle {
            assertEquals(IntSize.Zero, slotSize)
            showActions.value = true
        }
        val search = compose.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithContentDescription("More").fetchSemanticsNode().boundsInRoot
        val moreIcon = compose.onNodeWithContentDescription("More", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            assertEquals(44.dp.toPx(), search.width, 0.5f)
            assertEquals(search.width, more.width, 0.5f)
            assertEquals(8.dp.toPx(), more.left - search.right, 0.5f)
            assertEquals(search.center.y, more.center.y, 0.5f)
            assertEquals(more.center.x, moreIcon.center.x, 0.5f)
            assertEquals(more.center.y, moreIcon.center.y, 0.5f)
        }
    }

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
