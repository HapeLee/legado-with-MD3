package io.legado.app.ui.widget.components

import android.os.Build
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.LocalMochiBackdropRegistry
import io.legado.app.ui.theme.LocalMochiScrollBlurProgress
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.LocalTopBarBackdrop
import io.legado.app.ui.book.changesource.ChangeSourceMigrationOptionsSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.menuItem.GlassMenuAnchor
import io.legado.app.ui.widget.components.menuItem.LocalGlassPopupContent
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuLazy
import io.legado.app.ui.widget.components.mochi.AppMochiNavigationBar
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.MochiSheetLayout
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassSmallTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.topbar.LocalMiuixGlassTopBar
import io.legado.app.ui.widget.components.topbar.LocalMiuixGlassTopBarActionProgress
import io.legado.app.ui.widget.components.topbar.M3GlassScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarAnimatedActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.topbar.TopBarOverflowMenu
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
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

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
    fun switchingFromMochiToMaterialCommitsAfterMenuDismisses() {
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
        picker.performClick()
        // Mochi 原生菜单由 InWindowOverlay 挂到 DecorView，不是 Compose Popup，因此用文本定位选项。
        // 切换根主题必须等浮层收起摘除后再提交（commitAfterDismiss），否则会在测量期间移除子视图。
        compose.onNodeWithText(material, useUnmergedTree = true)
            .assertIsDisplayed()
            .performTouchInput { click() }
        compose.waitForIdle()
        // 新入口的可点按钮节点只声明标题文本，当前值在纯展示的 trigger 子节点里；
        // 行为基线仍是「切换后设置行显示新值」，改到该文本节点上断言。
        compose.onNodeWithText(material, useUnmergedTree = true).assertIsDisplayed()
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
    @Config(sdk = [26, 35])
    fun hyperOs4TopBarStaysGlassWhenBackgroundSamplingChanges() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val theme = mutableStateOf(
            ThemeSettings(useHyperOs4Components = true, miuixTopBarLiquidGlass = false)
        )
        val samplingDisabled = mutableStateOf(false)
        val nativeEffectsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        var miuixSampling = false
        var glassStyle = false
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
                        miuixSampling = hasBackdrop
                        materialSampling = material
                    }
                    GlassTopAppBar("Title", actions = {
                        val glass = LocalMiuixGlassTopBar.current
                        SideEffect { glassStyle = glass }
                        TopBarActionButton({ clicks++ }, AppIcons.Search, "Search")
                    })
                }) {}
            }
        }
        compose.runOnIdle {
            assertEquals(nativeEffectsSupported, glassStyle)
            assertEquals(nativeEffectsSupported, miuixSampling)
        }
        compose.onNodeWithContentDescription("Search").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(nativeEffectsSupported, glassStyle)
            assertEquals(nativeEffectsSupported, miuixSampling)
            assertFalse(materialSampling)
            assertEquals(1, clicks)
            theme.value = theme.value.copy(enableBlur = true, enableProgressiveBlur = true)
        }
        compose.runOnIdle {
            assertEquals(nativeEffectsSupported, glassStyle)
            assertEquals(nativeEffectsSupported, miuixSampling)
            assertFalse(materialSampling)
            samplingDisabled.value = true
        }
        compose.onNodeWithContentDescription("Search").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(nativeEffectsSupported, glassStyle)
            assertFalse(miuixSampling)
            assertEquals(2, clicks)
            samplingDisabled.value = false
            theme.value = theme.value.copy(useHyperOs4Components = false, miuixTopBarLiquidGlass = true)
        }
        compose.runOnIdle {
            assertFalse(glassStyle)
            assertEquals(nativeEffectsSupported, miuixSampling)
        }
    }

    @Test
    fun hyperOs4SwitchAloneControlsGlassTopBarWithLegacySettings() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val theme = mutableStateOf(
            ThemeSettings(miuixTopBarLiquidGlass = true, useHyperOs4Components = false)
        )
        var glassStyle = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = theme.value,
            )) {
                AppScaffold(topBar = {
                    GlassTopAppBar("Title", actions = {
                        val glass = LocalMiuixGlassTopBar.current
                        SideEffect { glassStyle = glass }
                        TopBarActionButton({}, AppIcons.Search, "Search")
                    })
                }) {}
            }
        }
        compose.runOnIdle {
            assertFalse(glassStyle)
            theme.value = theme.value.copy(useHyperOs4Components = true)
        }
        compose.onNodeWithContentDescription("Search").performTouchInput { click() }
        compose.runOnIdle {
            assertTrue(glassStyle)
            theme.value = theme.value.copy(useHyperOs4Components = false)
        }
        compose.onNodeWithContentDescription("Search").performTouchInput { click() }
        compose.runOnIdle { assertFalse(glassStyle) }
    }

    @Test
    fun hyperOs4SwitchIsSearchableInThemeSettings() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val target = requireNotNull(SettingsSearchCatalog.find("theme.hyper_os4_components"))
        val shell = AppShellSettings(composeEngine = "miuix")
        var theme by mutableStateOf(ThemeSettings())
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = shell, theme = theme)) {
                SettingsSearchTargetScope(target) {
                    ThemeConfigScreen(
                        ThemeConfigUiState(appShell = shell, theme = theme),
                        { intent -> if (intent is ThemeConfigIntent.UpdateTheme) theme = intent.transform(theme) },
                        {}, {}, {},
                    )
                }
            }
        }
        val inSetting = hasAnyAncestor(hasTestTag("setting:theme.hyper_os4_components"))
        compose.onNode(hasText(app.getString(R.string.hyper_os4_components)) and inSetting).assertIsDisplayed()
        val switch = compose.onNode(isToggleable() and inSetting)
        switch.assertIsOff().performClick()
        switch.assertIsOn()
        compose.runOnIdle { assertTrue(theme.useHyperOs4Components) }
    }

    @Test
    @Config(sdk = [26, 35])
    fun hyperOs4CardTabsSelectAndPreserveTrailingActions() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var selected by mutableStateOf(0)
        var trailingClicks = 0
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                AppScaffold {
                    Column {
                        CardTabRow(
                            tabTitles = listOf("First view", "Second view"),
                            selectedTabIndex = selected,
                            onTabSelected = { selected = it },
                            modifier = Modifier.testTag("card-tabs"),
                        )
                        CardTabRow(
                            tabTitles = listOf("With action"),
                            selectedTabIndex = 0,
                            onTabSelected = {},
                            tabEndContent = {
                                TopBarActionButton(
                                    { trailingClicks++ },
                                    AppIcons.MoreVert,
                                    "Tab options",
                                )
                            },
                        )
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            compose.onNodeWithTag("card-tabs").assertHeightIsEqualTo(GlassSegmentedTabRowDefaults.Height)
        }
        compose.onNodeWithText("First view").assertIsSelected()
        compose.onNodeWithText("Second view").performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithContentDescription("Tab options").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, trailingClicks) }
    }

    @Test
    fun hyperOs4RendersGlassTabsAndFallsBackForDropdownWithoutHost() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                // 主题根现在提供 root host；显式关掉才能覆盖 window 级弹层的回落分支。
                CompositionLocalProvider(LocalHasMiuixPopupHost provides false) {
                    Column {
                        AppTabRow(listOf("标签一", "标签二"), 0, {})
                        CompactDropdownSettingItem(
                            title = "顶栏样式",
                            selectedValue = "glass",
                            displayEntries = arrayOf("默认", "玻璃"),
                            entryValues = arrayOf("default", "glass"),
                            onValueChange = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("标签一").assertExists()
        compose.onNodeWithText("顶栏样式").assertExists()
    }

    @Test
    fun hyperOs4KeepsOverflowingTabsScrollable() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var selected by mutableStateOf(0)
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                AppTabRow(
                    tabTitles = List(8) { "Group ${it + 1}" },
                    selectedTabIndex = selected,
                    onTabSelected = { selected = it },
                    modifier = Modifier.width(240.dp).testTag("overflow-tabs"),
                )
            }
        }
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("overflow-tabs")))
            .performScrollToIndex(7)
        compose.onNodeWithText("Group 8").performTouchInput { click() }
        compose.runOnIdle { assertEquals(7, selected) }
    }

    @Test
    fun miuixScaffoldDeclaresPopupHostCapability() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var outsideHost = true
        var insideHost = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
            )) {
                val outside = LocalHasMiuixPopupHost.current
                SideEffect { outsideHost = outside }
                AppScaffold(topBar = {}) {
                    val inside = LocalHasMiuixPopupHost.current
                    SideEffect { insideHost = inside }
                }
            }
        }
        compose.runOnIdle {
            assertFalse(outsideHost)
            assertTrue(insideHost)
        }
    }

    @Test
    fun hyperOs4GlassDropdownRendersInsideMiuixScaffold() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                AppScaffold(topBar = {}) {
                    DropdownListSettingItem(
                        title = "主题引擎",
                        selectedValue = "miuix",
                        displayEntries = arrayOf("Material", "Miuix"),
                        entryValues = arrayOf("material", "miuix"),
                        onValueChange = {},
                    )
                }
            }
        }
        compose.onNodeWithText("主题引擎").assertExists()
    }

    @Test
    fun miuixDropdownWithoutHostOpensTinyWindowPopup() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                // 主题根现在提供 root host；显式关掉才能覆盖 window 级弹层的 tiny 回落。
                CompositionLocalProvider(LocalHasMiuixPopupHost provides false) {
                    DropdownListSettingItem(
                        title = "翻译引擎",
                        selectedValue = "a",
                        displayEntries = arrayOf("引擎一", "引擎二"),
                        entryValues = arrayOf("a", "b"),
                        onValueChange = {},
                    )
                }
            }
        }
        compose.onNodeWithText("引擎二").assertDoesNotExist()
        compose.onNodeWithText("翻译引擎").performClick()
        compose.onNodeWithText("引擎二").assertExists()
    }

    @Test
    fun hyperOs4SheetUsesHostBackedGlassContainer() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var hostBackedComposed = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                AppScaffold(topBar = {}) {
                    AppModalBottomSheet(show = true, onDismissRequest = {}, title = "布局设置") {
                        val hasHost = LocalHasMiuixPopupHost.current
                        SideEffect { if (hasHost) hostBackedComposed = true }
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { hostBackedComposed }
    }

    @Test
    fun hyperOs4OffKeepsWindowLevelSheetContainer() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var windowLevelComposed = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = false),
            )) {
                AppScaffold(topBar = {}) {
                    AppModalBottomSheet(show = true, onDismissRequest = {}, title = "布局设置") {
                        val hasHost = LocalHasMiuixPopupHost.current
                        SideEffect { if (!hasHost) windowLevelComposed = true }
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { windowLevelComposed }
    }

    @Test
    fun hyperOs4DialogUsesHostBackedGlassContainer() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var hostBackedComposed = false
        var confirmEnabled by mutableStateOf(false)
        var confirmations = 0
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                AppScaffold(topBar = {}) {
                    AppAlertDialog(
                        show = true,
                        onDismissRequest = {},
                        title = "确认",
                        confirmText = "Save changes",
                        confirmEnabled = confirmEnabled,
                        onConfirm = { confirmations++ },
                        content = {
                            val hasHost = LocalHasMiuixPopupHost.current
                            SideEffect { if (hasHost) hostBackedComposed = true }
                        },
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { hostBackedComposed }
        val confirm = compose.onNodeWithText("Save changes")
        confirm.assertIsNotEnabled()
        compose.runOnIdle { confirmEnabled = true }
        confirm.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
        confirm.assertExists()
    }

    @Test
    fun hyperOs4CompactBarActionKeepsItsNativeSizeAfterToggling() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var checked by mutableStateOf(false)
        var glass = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true, miuixTopBarLiquidGlass = true),
            )) {
                AppScaffold(topBar = {
                    GlassSmallTopAppBar(title = "Search", actions = {
                        val glassEnabled = LocalMiuixGlassTopBar.current
                        SideEffect { glass = glassEnabled }
                        TopBarAnimatedActionButton(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            iconChecked = AppIcons.Search,
                            iconUnchecked = AppIcons.Search,
                            activeText = "Search filter enabled",
                            inactiveText = "Search filter disabled",
                            modifier = Modifier.testTag("animated-action"),
                        )
                    })
                }) {}
            }
        }
        val action = compose.onNodeWithTag("animated-action")
        val nativeWidth = with(compose.density) { 44.dp.roundToPx() }.toFloat()
        assertEquals(nativeWidth, action.fetchSemanticsNode().boundsInRoot.width, 1f)
        assertTrue(glass)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Search filter disabled").performClick()
        compose.mainClock.advanceTimeBy(450)
        compose.onNodeWithContentDescription("Search filter enabled").assertExists()
        assertEquals(nativeWidth, action.fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.onNodeWithText("Search filter enabled").assertDoesNotExist()
    }

    @Test
    fun hyperOs4ListSearchStartsVisibleAndUnfocused() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        var searchMode by mutableStateOf(false)
        var query by mutableStateOf("")
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true, miuixTopBarLiquidGlass = true),
            )) {
                AppScaffold(topBar = {
                    DynamicTopAppBar(
                        title = "Sources",
                        state = object : ListUiState<String> {
                            override val items = emptyList<String>()
                            override val selectedIds = emptySet<Any>()
                            override val searchKey = query
                            override val isSearch = searchMode
                            override val isLoading = false
                        },
                        scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior(),
                        onSearchToggle = { searchMode = it },
                        onSearchQueryChange = { query = it },
                        searchPlaceholder = "Filter sources",
                        onClearSelection = {},
                    )
                }) {}
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.assertIsNotFocused()
        compose.onNodeWithContentDescription(app.getString(R.string.search)).assertDoesNotExist()
        field.performTextInput("abc")
        compose.runOnIdle {
            assertTrue(searchMode)
            assertEquals("abc", query)
        }
        field.performTextClearance()
        compose.runOnIdle {
            assertFalse(searchMode)
            assertEquals("", query)
        }
        field.assertExists()
    }

    @Test
    fun hyperOs4TopBarSpacesActionsAndBlursWithoutAnOpaqueBand() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var scrolled by mutableStateOf(false)
        var enableBlur by mutableStateOf(false)
        var captureBackdrop by mutableStateOf(false)
        var compact by mutableStateOf(false)
        var progress: State<Float>? = null
        var longClicks = 0
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "miuix"),
                    theme = ThemeSettings(
                        useHyperOs4Components = true,
                        miuixTopBarLiquidGlass = true,
                        enableBlur = enableBlur,
                    ),
                ),
                applyBackground = false,
            ) {
                val backdrop = rememberLayerBackdrop { drawRect(Color.Cyan) }
                Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                    Box(Modifier.matchParentSize().layerBackdrop(backdrop))
                    CompositionLocalProvider(LocalMiuixBarBackdrop provides backdrop.takeIf { captureBackdrop }) {
                        GlassMediumFlexibleTopAppBar(
                            title = "Bookshelf",
                            modifier = Modifier.testTag("os4-bar"),
                            isContentScrolled = scrolled,
                            compactMiuixActions = compact,
                            actions = {
                                val actionProgress = LocalMiuixGlassTopBarActionProgress.current
                                SideEffect { progress = actionProgress }
                                TopBarActionButton(
                                    {}, AppIcons.Search, "Search",
                                    onLongClick = { longClicks++ },
                                )
                                TopBarAnimatedActionButton(
                                    checked = false,
                                    onCheckedChange = {},
                                    iconChecked = AppIcons.Search,
                                    iconUnchecked = AppIcons.Search,
                                    activeText = "Filter",
                                    inactiveText = "Filter",
                                )
                            },
                        )
                    }
                }
            }
        }
        val search = compose.onNodeWithContentDescription("Search")
        val filter = compose.onNodeWithContentDescription("Filter")
        fun actionGap() = filter.fetchSemanticsNode().boundsInRoot.left - search.fetchSemanticsNode().boundsInRoot.right
        assertEquals(with(compose.density) { 8.dp.roundToPx() }.toFloat(), actionGap(), 1f)
        fun bandPixel(): Color {
            val pixels = compose.onNodeWithTag("os4-bar").captureToImage().toPixelMap()
            return pixels[1, pixels.height / 2]
        }
        val unmasked = bandPixel()
        assertEquals(0f, progress!!.value, 0.001f)
        search.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, longClicks)
            scrolled = true
        }
        assertEquals(unmasked, bandPixel())
        assertEquals(1f, progress!!.value, 0.001f)
        compose.runOnIdle { enableBlur = true }
        assertEquals(unmasked, bandPixel())
        compose.runOnIdle { captureBackdrop = true }
        val sampled = bandPixel()
        assertEquals(Color.Cyan.red, sampled.red, 0.05f)
        assertEquals(Color.Cyan.green, sampled.green, 0.05f)
        assertEquals(Color.Cyan.blue, sampled.blue, 0.05f)
        compose.runOnIdle {
            scrolled = false
            compact = true
        }
        assertEquals(unmasked, bandPixel())
        assertEquals(0f, progress!!.value, 0.001f)
        assertEquals(0f, actionGap(), 1f)
    }

    @Test
    fun hyperOs4TopBarBlurMatchesTheScaffoldOnAnEmptyPage() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var darkTheme by mutableStateOf(false)
        var scrolled by mutableStateOf(false)
        var progressiveBlur by mutableStateOf(false)
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "miuix"),
                    theme = ThemeSettings(
                        useHyperOs4Components = true,
                        miuixTopBarLiquidGlass = true,
                        enableBlur = true,
                        enableProgressiveBlur = progressiveBlur,
                    ),
                ),
                darkTheme = darkTheme,
            ) {
                AppScaffold(
                    modifier = Modifier.testTag("miuix-scaffold"),
                    topBar = {
                        GlassMediumFlexibleTopAppBar(title = "", isContentScrolled = scrolled)
                    },
                ) {}
            }
        }
        fun assertBarMatchesPage(mode: String) {
            val pixels = compose.onNodeWithTag("miuix-scaffold").captureToImage().toPixelMap()
            val bar = pixels[pixels.width / 2, with(compose.density) { 32.dp.roundToPx() }]
            val page = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(mode, page.red, bar.red, 2f / 255f)
            assertEquals(mode, page.green, bar.green, 2f / 255f)
            assertEquals(mode, page.blue, bar.blue, 2f / 255f)
        }
        assertBarMatchesPage("Before scrolling")
        compose.runOnIdle { scrolled = true }
        assertBarMatchesPage("Regular blur after scrolling")
        compose.runOnIdle { progressiveBlur = true }
        assertBarMatchesPage("Progressive blur after scrolling")
        compose.runOnIdle { darkTheme = true }
        assertBarMatchesPage("Dark progressive blur after scrolling")
        compose.runOnIdle { progressiveBlur = false }
        assertBarMatchesPage("Dark regular blur after scrolling")
    }

    @Test
    fun hyperOs4OffKeepsWindowLevelDialogContainer() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var windowLevelComposed = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = false),
            )) {
                AppScaffold(topBar = {}) {
                    AppAlertDialog(
                        show = true,
                        onDismissRequest = {},
                        title = "确认",
                        content = {
                            val hasHost = LocalHasMiuixPopupHost.current
                            SideEffect { if (!hasHost) windowLevelComposed = true }
                        },
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { windowLevelComposed }
    }

    @Test
    fun hyperOs4ThemeRootServesGlassContainersOutsideAppScaffold() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var pageBackdrop: Any? = null
        var rootBackdrop: Any? = null
        var hostInsideSheet = false
        var hostInsideDialog = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true, enableBlur = true),
            )) {
                val backdrop = LocalMiuixBarBackdrop.current
                SideEffect { rootBackdrop = backdrop }
                AppScaffold(topBar = {}) {
                    val source = LocalMiuixBarBackdrop.current
                    SideEffect { pageBackdrop = source }
                }
                // 书架、设置页把 sheet/dialog 调用放在 AppScaffold 之外。
                AppModalBottomSheet(show = true, onDismissRequest = {}) {
                    val hasHost = LocalHasMiuixPopupHost.current
                    SideEffect { if (hasHost) hostInsideSheet = true }
                }
                AppAlertDialog(
                    show = true,
                    onDismissRequest = {},
                    content = {
                        val hasHost = LocalHasMiuixPopupHost.current
                        SideEffect { if (hasHost) hostInsideDialog = true }
                    },
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            pageBackdrop != null && rootBackdrop === pageBackdrop && hostInsideSheet && hostInsideDialog
        }
    }

    @Test
    fun hyperOs4OffDoesNotCreateRootBackdrop() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var backdropAtRoot = true
        var hostAtRoot = true
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = false),
            )) {
                val hasBackdrop = LocalMiuixBarBackdrop.current != null
                val hasHost = LocalHasMiuixPopupHost.current
                SideEffect {
                    backdropAtRoot = hasBackdrop
                    hostAtRoot = hasHost
                }
            }
        }
        compose.runOnIdle {
            assertFalse(backdropAtRoot)
            assertFalse(hostAtRoot)
        }
    }

    @Test
    fun hyperOs4GlassSheetDropdownUsesSameHostAndCanSelect() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var selected = "grid"
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                AppScaffold(topBar = {}) {
                    AppModalBottomSheet(show = true, onDismissRequest = {}) {
                        CompactDropdownSettingItem(
                            title = "书架布局",
                            selectedValue = selected,
                            displayEntries = arrayOf("列表", "网格"),
                            entryValues = arrayOf("list", "grid"),
                            onValueChange = { selected = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("书架布局").performClick()
        compose.onNodeWithText("列表").assertExists().performClick()
        compose.runOnIdle { assertEquals("list", selected) }
    }

    @Test
    fun hyperOs4RuleItemMenuUsesButtonParentAsGlassAnchor() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var renderedInGlass = false
        var selections = 0
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                AppScaffold {
                    SelectionItemCard(
                        title = "替换规则",
                        moreContentDescription = "规则更多操作",
                        dropdownContent = { dismiss ->
                            val inGlass = LocalGlassPopupContent.current
                            SideEffect { renderedInGlass = inGlass }
                            RoundDropdownMenuItem(
                                text = "规则置顶",
                                onClick = { selections++; dismiss() },
                            )
                        },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("规则更多操作").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { renderedInGlass }
        compose.onNodeWithText("规则置顶").performClick()
        compose.runOnIdle { assertEquals(1, selections) }
        compose.onNodeWithText("规则置顶").assertDoesNotExist()
        compose.onNodeWithContentDescription("规则更多操作").performClick()
        compose.onNodeWithText("规则置顶").assertExists()
    }

    @Test
    fun hyperOs4DialogCanOpenAndCloseAnOrdinaryDropdown() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        var expanded by mutableStateOf(false)
        var dialogDismissals = 0
        var renderedInGlass = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true),
            )) {
                AppScaffold {}
                AppAlertDialog(
                    show = true,
                    title = "编码设置",
                    onDismissRequest = { dialogDismissals++ },
                    content = {
                        Box {
                            TopBarActionButton({ expanded = true }, AppIcons.MoreVert, "选择编码")
                            RoundDropdownMenu(expanded, onDismissRequest = { expanded = false }) { dismiss ->
                                val inGlass = LocalGlassPopupContent.current
                                SideEffect { renderedInGlass = inGlass }
                                RoundDropdownMenuItem(text = "UTF-8", onClick = dismiss)
                            }
                        }
                    },
                )
            }
        }
        compose.onNodeWithContentDescription("选择编码").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { renderedInGlass }
        compose.onNodeWithText("UTF-8").performTouchInput { click() }
        compose.onNodeWithText("UTF-8").assertDoesNotExist()
        compose.onNodeWithText("编码设置").assertExists()
        compose.runOnIdle { assertEquals(0, dialogDismissals) }
    }

    @Test
    fun hyperOs4TopBarMenuRendersInPageGlassOverlay() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        var expanded by mutableStateOf(false)
        var renderedInGlass = false
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = true, miuixTopBarLiquidGlass = true),
            )) {
                AppScaffold(topBar = {
                    GlassTopAppBar("Glass", actions = {
                        TopBarOverflowMenu(expanded, onExpandedChange = { expanded = it }) { dismiss ->
                            val inGlass = LocalGlassPopupContent.current
                            SideEffect { renderedInGlass = inGlass }
                            RoundDropdownMenuItem(text = "菜单项", onClick = { dismiss() })
                        }
                    })
                }) {}
            }
        }
        compose.onNodeWithContentDescription(app.getString(R.string.more_menu)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { renderedInGlass }
        compose.onNodeWithText("菜单项").assertExists()
    }

    @Test
    fun hyperOs4OffKeepsTopBarMenuOutOfGlassOverlay() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var expanded by mutableStateOf(false)
        var renderedInGlass = true
        compose.setContent {
            AppTheme(AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "miuix"),
                theme = ThemeSettings(useHyperOs4Components = false),
            )) {
                AppScaffold(topBar = {}) {
                    val anchor = rememberGlassPopupAnchor()
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("menu-anchor")
                                .glassPopupAnchor(anchor, cornerRadius = 20.dp)
                                .onGloballyPositioned { bounds = it.boundsInRoot() }
                                .clickable { expanded = true },
                        )
                        RoundDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            glassAnchor = GlassMenuAnchor(anchor, bounds),
                        ) { dismiss ->
                            val inGlass = LocalGlassPopupContent.current
                            SideEffect { renderedInGlass = inGlass }
                            RoundDropdownMenuItem(text = "菜单项", onClick = { dismiss() })
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("menu-anchor").performClick()
        compose.onNodeWithText("菜单项").assertExists()
        compose.runOnIdle { assertFalse(renderedInGlass) }
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
