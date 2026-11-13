package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.feature.settings.search.SettingsSearchCatalog
import io.legado.app.feature.settings.search.SettingsSearchTargetScope
import io.legado.app.ui.config.themeConfig.ThemeConfigIntent
import io.legado.app.ui.config.themeConfig.ThemeConfigScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigUiState
import io.legado.app.ui.widget.components.AppRadioButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.coui.CouiGlassScrollBehavior
import io.legado.app.ui.widget.components.coui.LocalAppCouiTopBarHost
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassSmallTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarAnimatedActionButton
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.feice.mochi.theme.MochiTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppThemeEngineTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun mochiUsesTheCustomPaletteAndLocalOverridesAndInheritsFonts() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val customPrimary = Color(0xFF123456)
        val overridePrimary = Color(0xFF285040)
        var rootPrimary = Color.Unspecified
        var localPrimary = Color.Unspecified
        var materialPrimary = Color.Unspecified
        var titleFamily: FontFamily? = null
        var bodyFamily: FontFamily? = null
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "mochi"),
                    theme = ThemeSettings(
                        appTheme = "12", enableDeepPersonalization = true, themeColor = 0xFF123456.toInt(),
                    ),
                ),
                applyBackground = false,
            ) {
                val root = MochiTheme.colorScheme.primary
                SideEffect { rootPrimary = root }
                ProvideColorSchemeOverride(lightColorScheme(primary = overridePrimary)) {
                    val local = MochiTheme.colorScheme.primary
                    val material = MaterialTheme.colorScheme.primary
                    SideEffect { localPrimary = local; materialPrimary = material }
                    MaterialTheme(typography = Typography(bodyLarge = TextStyle(fontFamily = FontFamily.Monospace))) {
                        ProvideMochiTheme(MaterialTheme.colorScheme, false) {
                            val title = MochiTheme.textStyles.largeTitle.fontFamily
                            val body = MochiTheme.textStyles.bodyLarge.fontFamily
                            SideEffect { titleFamily = title; bodyFamily = body }
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(customPrimary, rootPrimary)
            assertEquals(overridePrimary, localPrimary)
            assertEquals(overridePrimary, materialPrimary)
            assertEquals(FontFamily.Monospace, titleFamily)
            assertEquals(FontFamily.Monospace, bodyFamily)
        }
    }

    @Test
    fun persistedStyleSelectsItsBackendAndTracksRuntimeDarkModeChanges() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val configuration = mutableStateOf(AppUiConfiguration())
        var observedEngine = ComposeEngine.Material
        var observedDark = false
        var observedPrimary = Color.Unspecified
        var backendPrimary = Color.Unspecified
        var observedContent: Any? = null

        compose.setContent {
            AppTheme(configuration.value, applyBackground = false) {
                val contentToken = remember { Any() }
                val engine = LegadoTheme.composeEngine
                val dark = LegadoTheme.isDark
                val primary = LegadoTheme.colorScheme.primary
                val backend = when (engine) {
                    ComposeEngine.Material -> MaterialTheme.colorScheme.primary
                    ComposeEngine.Mochi -> MochiTheme.colorScheme.primary
                    ComposeEngine.Miuix -> MiuixTheme.colorScheme.primary
                    ComposeEngine.Coui -> CouiTheme.colorScheme.accent
                }
                SideEffect {
                    observedEngine = engine
                    observedDark = dark
                    observedPrimary = primary
                    backendPrimary = backend
                    observedContent = contentToken
                }
            }
        }

        val initialContent = compose.runOnIdle { checkNotNull(observedContent) }

        listOf(
            "material" to ComposeEngine.Material,
            "miuix" to ComposeEngine.Miuix,
            "MIUIX" to ComposeEngine.Miuix,
            "mochi" to ComposeEngine.Mochi,
            "MOCHI" to ComposeEngine.Mochi,
            "coui" to ComposeEngine.Coui,
            "unknown" to ComposeEngine.Material,
        ).forEach { (storedValue, engine) ->
            listOf(false, true).forEach { dark ->
                compose.runOnIdle {
                    configuration.value = AppUiConfiguration(
                        appShell = AppShellSettings(composeEngine = storedValue),
                        isSystemDarkTheme = dark,
                    )
                }
                compose.runOnIdle {
                    assertEquals(storedValue, engine, observedEngine)
                    assertEquals("$storedValue dark=$dark", dark, observedDark)
                    assertEquals("$storedValue dark=$dark", backendPrimary, observedPrimary)
                    assertSame("$storedValue content survives theme switch", initialContent, observedContent)
                }
            }
        }
    }

    @Test
    fun couiDefaultUsesNativeColorsAndKeepsExplicitPaletteOverrides() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val configuration = mutableStateOf(
            AppUiConfiguration(appShell = AppShellSettings(composeEngine = "coui"))
        )
        val overridePrimary = Color(0xFF285040)
        var nativeColors: CouiColorScheme? = null
        var materialColors = lightColorScheme()
        var contentColor = Color.Unspecified
        var localPrimary = Color.Unspecified
        compose.setContent {
            AppTheme(configuration.value, applyBackground = false) {
                val native = CouiTheme.colorScheme
                val material = MaterialTheme.colorScheme
                val ambientContentColor = LocalContentColor.current
                SideEffect {
                    nativeColors = native
                    materialColors = material
                    contentColor = ambientContentColor
                }
                ProvideColorSchemeOverride(lightColorScheme(primary = overridePrimary)) {
                    val primary = CouiTheme.colorScheme.accent
                    SideEffect { localPrimary = primary }
                }
            }
        }
        listOf(false, true).forEach { dark ->
            val expected = if (dark) CouiPresets.Dark else CouiPresets.Light
            compose.runOnIdle { configuration.value = configuration.value.copy(isSystemDarkTheme = dark) }
            compose.runOnIdle {
                assertEquals(expected, nativeColors)
                assertEquals(expected.accent, materialColors.primary)
                assertEquals(expected.background, materialColors.background)
                assertEquals(expected.onSurface, contentColor)
                assertEquals(expected.card.compositeOver(expected.background), materialColors.surfaceContainerLow)
                assertEquals(overridePrimary, localPrimary)
            }
        }

        val customPrimary = Color(0xFF123456)
        compose.runOnIdle {
            configuration.value = configuration.value.copy(
                isSystemDarkTheme = false,
                theme = ThemeSettings(
                    appTheme = "12", enableDeepPersonalization = true, themeColor = 0xFF123456.toInt(),
                ),
            )
        }
        compose.runOnIdle {
            assertEquals(customPrimary, nativeColors?.accent)
            assertEquals(customPrimary, materialColors.primary)
        }
    }

    @Test
    fun couiCustomColorSwitchRestoresDynamicFirstTheme() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val configuration = mutableStateOf(
            AppUiConfiguration(
                appShell = AppShellSettings(composeEngine = "coui"),
                theme = ThemeSettings(useMiuixMonet = true),
            )
        )
        var dynamicColor = false
        var materialPrimary = Color.Unspecified
        var nativeColors: CouiColorScheme? = null
        compose.setContent {
            AppTheme(configuration.value, applyBackground = false) {
                val dynamic = LegadoTheme.useDynamicColor
                val primary = MaterialTheme.colorScheme.primary
                val native = CouiTheme.colorScheme
                SideEffect {
                    dynamicColor = dynamic
                    materialPrimary = primary
                    nativeColors = native
                }
            }
        }
        compose.runOnIdle {
            assertTrue("开启自定义主题色后首项应标记为动态取色", dynamicColor)
            assertTrue(
                "首项不应再被 CouiPresets 固定预设接管",
                materialPrimary != CouiPresets.Light.accent,
            )
            assertTrue(
                "COUI 原生控件应跟随动态取色而非固定预设",
                nativeColors != CouiPresets.Light,
            )
        }

        compose.runOnIdle {
            configuration.value = configuration.value.copy(
                theme = ThemeSettings(useMiuixMonet = false),
            )
        }
        compose.runOnIdle {
            assertFalse("关闭自定义主题色后应由 COUI 内置预设接管", dynamicColor)
            assertEquals(CouiPresets.Light, nativeColors)
            assertEquals(CouiPresets.Light.accent, materialPrimary)
        }
    }

    @Test
    @Config(sdk = [26, 33])
    fun storedCouiSelectionUsesMaterialBelowAndroid14() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var engine = ComposeEngine.Coui
        var stored = ""
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "coui"))) {
                val resolved = LegadoTheme.composeEngine
                val selected = LocalAppUiConfiguration.current.appShell.composeEngine
                SideEffect { engine = resolved; stored = selected }
            }
        }
        compose.runOnIdle {
            assertEquals(ComposeEngine.Material, engine)
            assertEquals("coui", stored)
        }
    }

    @Test
    @Config(sdk = [33])
    fun themePickerShowsCouiAsUnavailableBelowAndroid14() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val target = requireNotNull(SettingsSearchCatalog.find("theme.compose_engine"))
        val shell = AppShellSettings(composeEngine = "coui")
        var writes = 0
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = shell)) {
                SettingsSearchTargetScope(target) {
                    ThemeConfigScreen(
                        ThemeConfigUiState(appShell = shell),
                        { if (it is ThemeConfigIntent.SetComposeEngine) writes++ },
                        {}, {}, {},
                    )
                }
            }
        }
        val row = hasAnyAncestor(hasTestTag("setting:theme.compose_engine"))
        compose.onNode(hasText(app.getString(R.string.compose_engine)) and hasClickAction() and row).performClick()
        compose.onNodeWithText(app.getString(R.string.coui_theme_requires_android_14))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test
    @Config(sdk = [34])
    fun couiSettingsPreserveControlSemanticsAndCommitCallbacks() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val checked = mutableStateOf(false)
        val value = mutableFloatStateOf(0f)
        val selection = mutableStateOf("initial")
        var toggles = 0
        var commits = 0
        var choices = 0
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "coui"))) {
                check(LegadoTheme.composeEngine == ComposeEngine.Coui)
                Column {
                    SwitchSettingItem("Reading hints", checked = checked.value) {
                        checked.value = it
                        toggles++
                    }
                    AppSlider(
                        value = value.floatValue,
                        onValueChange = { value.floatValue = it },
                        steps = 3,
                        accessibilityLabel = "Speed",
                        onValueChangeFinished = { commits++ },
                    )
                    DropdownListSettingItem(
                        title = "Mode",
                        selectedValue = selection.value,
                        displayEntries = arrayOf("Initial", "Updated"),
                        entryValues = arrayOf("initial", "updated"),
                    ) {
                        selection.value = it
                        choices++
                    }
                }
            }
        }
        compose.onNodeWithText("Reading hints").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Speed").performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.5f)
        }
        compose.onNodeWithText("Mode").performClick()
        compose.onNodeWithText("Updated").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, toggles)
            assertEquals(0.5f, value.floatValue)
            assertEquals(1, commits)
            assertEquals("updated", selection.value)
            assertEquals(1, choices)
        }
    }

    @Test
    @Config(sdk = [34])
    fun couiGroupedRowsKeepAccessibleHeadingAndControlledSelection() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val showFirst = mutableStateOf(true)
        val checked = mutableStateOf(false)
        val selected = mutableStateOf(false)
        var rowChanges = 0
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "coui"))) {
                SplicedColumnGroup(modifier = Modifier.padding(horizontal = 16.dp), title = "Preferences") {
                    if (showFirst.value) SwitchSettingItem("Auto refresh", checked = false) { }
                    SwitchSettingItem("Reading hints", checked = false) { rowChanges++ }
                    AppCheckbox(
                        checked = checked.value,
                        onCheckedChange = { checked.value = it },
                        modifier = Modifier.semantics { contentDescription = "Show checkmark" },
                    )
                    AppRadioButton(
                        selected = selected.value,
                        onClick = { selected.value = true },
                        modifier = Modifier.semantics { contentDescription = "Compact mode" },
                    )
                    AppRadioButton(
                        selected = false,
                        onClick = { error("Disabled option was activated") },
                        enabled = false,
                        modifier = Modifier.semantics { contentDescription = "Unavailable mode" },
                    )
                }
            }
        }
        compose.onNode(hasText("Preferences") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
        val first = compose.onNodeWithText("Auto refresh").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 16.dp.toPx() }, first.left, 1f)
        val hints = compose.onNodeWithText("Reading hints")
        assertTrue(hints.fetchSemanticsNode().boundsInRoot.top > first.bottom)
        compose.runOnIdle { showFirst.value = false }
        assertEquals(first.top, hints.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        hints.performClick()
        compose.onNodeWithContentDescription("Show checkmark").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Compact mode").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Unavailable mode").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, rowChanges) }
    }

    @Test
    @Config(sdk = [34])
    fun couiSheetDropdownsOpenAndCommitIndependently() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val tiny = mutableStateOf("initial")
        val compact = mutableStateOf("initial")
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "coui"))) {
                AppModalBottomSheet(show = true, onDismissRequest = {}) {
                    TinyDropdownSettingItem(
                        title = "Tiny option",
                        selectedValue = tiny.value,
                        displayEntries = arrayOf("Tiny initial", "Tiny updated"),
                        entryValues = arrayOf("initial", "updated"),
                        onValueChange = { tiny.value = it },
                    )
                    CompactDropdownSettingItem(
                        title = "Compact option",
                        selectedValue = compact.value,
                        displayEntries = arrayOf("Compact initial", "Compact updated"),
                        entryValues = arrayOf("initial", "updated"),
                        onValueChange = { compact.value = it },
                    )
                }
            }
        }
        compose.onNodeWithText("Tiny option").performClick()
        compose.onNodeWithText("Tiny updated").assertIsDisplayed().performClick()
        compose.onNodeWithText("Compact option").performClick()
        compose.onNodeWithText("Compact updated").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("updated", tiny.value)
            assertEquals("updated", compact.value)
        }
    }

    @Test
    @Config(sdk = [34])
    fun couiToolbarSamplesEvenWithoutBlurToggleAndKeepsItsActions() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val blur = mutableStateOf(false)
        val compact = mutableStateOf(false)
        val disableSampling = mutableStateOf(false)
        var toolbarSamples = false
        var bodyReceivesSource = false
        var clicks = 0
        var scrollBehavior: GlassTopAppBarScrollBehavior? = null
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "coui"),
                    theme = ThemeSettings(enableBlur = blur.value),
                ),
            ) {
                val behavior = GlassTopAppBarDefaults.defaultScrollBehavior()
                SideEffect { scrollBehavior = behavior }
                val actions: @Composable RowScope.() -> Unit = {
                    TopBarActionButton({ clicks++ }, Icons.Filled.Check, "Apply")
                }
                AppScaffold(
                    disableContentSampling = disableSampling.value,
                    topBar = {
                        val samples = LocalAppCouiTopBarHost.current?.backdrop != null
                        SideEffect { toolbarSamples = samples }
                        if (compact.value) {
                            GlassSmallTopAppBar("Page", actions = actions)
                        } else {
                            GlassMediumFlexibleTopAppBar("Page", scrollBehavior = behavior, actions = actions)
                        }
                    },
                ) { padding ->
                    val receivesSource = LocalAppCouiTopBarHost.current?.backdrop != null
                    SideEffect { bodyReceivesSource = receivesSource }
                    Box(Modifier.fillMaxSize().padding(padding))
                }
            }
        }
        compose.onNodeWithText("Page").assertIsDisplayed()
        compose.runOnIdle {
            // COUI 顶栏材质是外观本身，enableBlur=false 也采样。
            assertTrue(toolbarSamples)
        }
        compose.onNodeWithText("Page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Apply").performClick()
        compose.runOnIdle {
            assertTrue(toolbarSamples)
            assertEquals(false, bodyReceivesSource)
            val native = scrollBehavior as CouiGlassScrollBehavior
            assertTrue(native.nestedScrollConnection.onPreScroll(Offset(0f, -20f), NestedScrollSource.UserInput).y < 0f)
            compact.value = true
        }
        compose.onNodeWithText("Page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Apply").performClick()
        compose.runOnIdle {
            assertTrue(toolbarSamples)
            disableSampling.value = true
        }
        compose.onNodeWithText("Page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Apply").performClick()
        compose.runOnIdle {
            assertEquals(false, toolbarSamples)
            assertEquals(3, clicks)
        }
    }

    @Test
    @Config(sdk = [34])
    fun couiToolbarKeepsActionsIconOnlyAndExposesOnlyClick() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val blur = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "coui"),
                    theme = ThemeSettings(enableBlur = blur.value),
                ),
            ) {
                val checked = remember { mutableStateOf(false) }
                AppScaffold(topBar = {
                    GlassSmallTopAppBar("Page", actions = {
                        TopBarActionButton(
                            onClick = { clicks++ },
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Apply",
                            onLongClick = { error("COUI top bar actions must ignore long press") },
                            onLongClickLabel = "Apply all",
                        )
                        TopBarAnimatedActionButton(
                            checked = checked.value,
                            onCheckedChange = { checked.value = it },
                            iconChecked = Icons.Filled.Check,
                            iconUnchecked = Icons.Filled.Close,
                            activeText = "Enabled",
                            inactiveText = "Disabled",
                        )
                    })
                }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) }
            }
        }

        for (enableBlur in listOf(false, true)) {
            compose.runOnIdle { blur.value = enableBlur }
            compose.onNode(hasClickAction() and hasContentDescription("Apply"))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
                .performClick()

            val off = compose.onNode(hasClickAction() and hasContentDescription("Disabled"))
            off.assertIsNotSelected()
            val width = off.fetchSemanticsNode().boundsInRoot.width
            off.performClick()
            val on = compose.onNode(hasClickAction() and hasContentDescription("Enabled"))
            on.assertIsSelected()
            compose.onNodeWithText("Enabled", useUnmergedTree = true).assertDoesNotExist()
            assertEquals(width, on.fetchSemanticsNode().boundsInRoot.width, 0f)
            on.performClick()
            off.assertIsNotSelected()
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }
}
