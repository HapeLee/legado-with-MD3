package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.navigation.AppNavigationBar
import io.legado.app.ui.widget.components.navigation.AppNavigationBarItem
import io.oxygenx.theme.OxygenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class OxygenEngineTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Host(content: @Composable () -> Unit) {
        AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "oxygen")), applyBackground = false, content = content)
    }

    @Test
    fun customColorSwitchUsesExactNativePalettesAndRestoresStoredColors() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val settings = io.legado.app.domain.model.settings.ThemeSettings(
            appTheme = "12", enableDeepPersonalization = true,
            themeColor = 0xFF235040.toInt(), themeColorNight = 0xFF90CAB0.toInt(),
            secondaryThemeColor = 0xFFB02230.toInt(), secondaryThemeColorNight = 0xFFB091CC.toInt(),
            customPrimary = 0xFF113322.toInt(), customNightPrimary = 0xFF446633.toInt(),
            useMiuixMonet = false,
        )
        val configuration = mutableStateOf(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "oxygen"), theme = settings))
        var actual: io.oxygenx.theme.OxygenColorScheme? = null
        var material = lightColorScheme()
        var semanticPrimary = Color.Unspecified
        var seed = Color.Unspecified
        var secondaryOverride = Color.Unspecified
        var dynamic = false
        compose.setContent {
            AppTheme(configuration.value, applyBackground = false) {
                val colors = OxygenTheme.colorScheme
                val fallback = MaterialTheme.colorScheme
                val primary = LegadoTheme.colorScheme.primary
                val bar = io.legado.app.ui.widget.components.GlassDefaults.secondaryColorOr { Color.Cyan }
                val isDynamic = LegadoTheme.useDynamicColor
                val seedColor = LegadoTheme.seedColor
                SideEffect { seed = seedColor; actual = colors; material = fallback; semanticPrimary = primary; secondaryOverride = bar; dynamic = isDynamic }
            }
        }
        listOf(false, true).forEach { dark ->
            val expected = if (dark) io.oxygenx.theme.darkColorScheme() else io.oxygenx.theme.lightColorScheme()
            compose.runOnIdle { configuration.value = configuration.value.copy(isSystemDarkTheme = dark, theme = settings) }
            compose.runOnIdle {
                assertEquals(expected, actual)
                assertEquals(expected.primary, material.primary)
                assertEquals(expected.surface, material.background)
                assertEquals(expected.primary, semanticPrimary)
                assertEquals(expected.primary, seed)
                assertEquals(Color.Cyan, secondaryOverride)
                assertFalse(dynamic)
                configuration.value = configuration.value.copy(theme = settings.copy(useMiuixMonet = true))
            }
            compose.runOnIdle {
                val custom = Color(if (dark) settings.themeColorNight else settings.themeColor)
                assertEquals(custom, actual?.primary)
                assertEquals(custom, material.primary)
                assertEquals(Color(if (dark) settings.secondaryThemeColorNight else settings.secondaryThemeColor), secondaryOverride)
                configuration.value = configuration.value.copy(theme = settings)
            }
            compose.runOnIdle { assertEquals(expected, actual); assertEquals("12", configuration.value.theme.appTheme) }
        }
        compose.runOnIdle { configuration.value = configuration.value.copy(theme = io.legado.app.domain.model.settings.ThemeSettings(useMiuixMonet = true)) }
        compose.runOnIdle {
            assertTrue(dynamic)
            assertEquals(androidx.compose.material3.dynamicDarkColorScheme(RuntimeEnvironment.getApplication()).primary, actual?.primary)
        }
    }

    @Test
    fun builtInPaletteIgnoresStoredTransparentModeAndStillSupportsAmoled() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val light = ThemeEngine.getColorScheme(app, AppThemeMode.Transparent, false, false, null,
            composeEngine = ComposeEngine.Oxygen, useBuiltInPalette = true)
        assertEquals(io.oxygenx.theme.lightColorScheme().surface, light.surface)
        val configuration = AppUiConfiguration(
            appShell = AppShellSettings(composeEngine = "oxygen"),
            isSystemDarkTheme = true,
            theme = io.legado.app.domain.model.settings.ThemeSettings(isPureBlack = true, useMiuixMonet = false),
        )
        var native = Color.Unspecified
        var fallback = Color.Unspecified
        compose.setContent {
            AppTheme(configuration, applyBackground = false) {
                val a = OxygenTheme.colorScheme.surface
                val b = MaterialTheme.colorScheme.surface
                SideEffect { native = a; fallback = b }
            }
        }
        compose.runOnIdle { assertEquals(Color.Black, native); assertEquals(Color.Black, fallback) }
    }

    @Test
    fun themeSettingsExposeTheCustomColorSwitchAndGatePaletteSelection() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val shell = AppShellSettings(composeEngine = "oxygen")
        val settings = mutableStateOf(io.legado.app.domain.model.settings.ThemeSettings(useMiuixMonet = false, showRefactorTip = false))
        var writes = 0
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = shell, theme = settings.value), applyBackground = false) {
                io.legado.app.feature.settings.search.SettingsSearchTargetScope(
                    requireNotNull(io.legado.app.feature.settings.search.SettingsSearchCatalog.find("theme.theme")),
                ) {
                    io.legado.app.ui.config.themeConfig.ThemeConfigScreen(
                        io.legado.app.ui.config.themeConfig.ThemeConfigUiState(appShell = shell, theme = settings.value),
                        { intent ->
                            if (intent is io.legado.app.ui.config.themeConfig.ThemeConfigIntent.SetMiuixMonet) {
                                settings.value = settings.value.copy(useMiuixMonet = intent.enabled)
                                writes++
                            }
                        }, {}, {}, {},
                    )
                }
            }
        }
        compose.onNodeWithTag("setting:theme.miuix_monet").assertExists()
        compose.onNodeWithTag("setting:theme.theme").assertDoesNotExist()
        compose.onNodeWithText(app.getString(io.legado.app.R.string.miuix_monet)).performClick()
        compose.onNodeWithTag("setting:theme.theme").assertExists()
        compose.runOnIdle { assertEquals(1, writes); assertTrue(settings.value.useMiuixMonet) }
    }

    @Test
    fun paletteOverridesAndFontsReachOxygenAndRetainedMaterialControls() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val primary = Color(0xFF237850)
        var oxygen = Color.Unspecified
        var material = Color.Unspecified
        var family: FontFamily? = null
        compose.setContent {
            Host {
                ProvideColorSchemeOverride(lightColorScheme(primary = primary)) {
                    MaterialTheme(typography = Typography(bodyLarge = TextStyle(fontFamily = FontFamily.Monospace))) {
                        ProvideOxygenTheme {
                            val oxygenValue = OxygenTheme.colorScheme.primary
                            val materialValue = MaterialTheme.colorScheme.primary
                            val font = OxygenTheme.typography.inputText.fontFamily
                            SideEffect { oxygen = oxygenValue; material = materialValue; family = font }
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(primary, oxygen)
            assertEquals(primary, material)
            assertEquals(FontFamily.Monospace, family)
        }
    }

    @Test
    fun appTextFollowsOxygenButtonStateAndNearerMaterialSlots() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val enabled = mutableStateOf(true)
        var color = Color.Unspecified
        var size = 0.sp
        var localColor = Color.Unspecified
        compose.setContent {
            Host {
                io.oxygenx.ui.basic.Button(
                    {}, enabled = enabled.value,
                    colors = io.oxygenx.ui.basic.ButtonDefaults.colors(contentColor = Color.Red, disabledContentColor = Color.Blue),
                ) {
                    AppText("Inherited", onTextLayout = { color = it.layoutInput.style.color; size = it.layoutInput.style.fontSize })
                }
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.Green) {
                    AppText("Material slot", onTextLayout = { localColor = it.layoutInput.style.color })
                }
            }
        }
        compose.runOnIdle {
            assertEquals(Color.Red, color)
            assertEquals(14.sp, size)
            assertEquals(Color.Green, localColor)
            enabled.value = false
        }
        compose.runOnIdle { assertEquals(Color.Blue, color) }
    }

    @Test
    fun oxygenDispatchRestoresSlotColorsBelowTheRetainedMaterialScaffold() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val enabled = mutableStateOf(true)
        var color = Color.Unspecified
        compose.setContent {
            Host {
                io.legado.app.ui.widget.components.AppScaffold {
                    io.legado.app.ui.widget.components.button.AppIconButton(
                        {}, enabled = enabled.value,
                        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                            contentColor = Color.Red, disabledContentColor = Color.Blue,
                        ),
                    ) {
                        AppText("Scaffold slot", onTextLayout = { color = it.layoutInput.style.color })
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(Color.Red, color); enabled.value = false }
        compose.runOnIdle { assertEquals(Color.Blue, color) }
    }

    @Test
    fun dispatchControlsKeepDisabledAndToggleSemantics() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val checked = mutableStateOf(false)
        var writes = 0
        compose.setContent {
            Host {
                Column {
                    PrimaryButton({}, enabled = false, text = "Disabled action")
                    AdaptiveSwitch(checked.value, { checked.value = it; writes++ }, Modifier.testTag("switch"))
                }
            }
        }
        compose.onNodeWithText("Disabled action").assertIsNotEnabled()
        compose.onNodeWithTag("switch").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test
    fun stateTextFieldKeepsItsStateAndInputOutputTransformations() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var readState: () -> String = { "" }
        compose.setContent {
            Host {
                val state = rememberTextFieldState()
                SideEffect { readState = { state.text.toString() } }
                AppTextField(
                    state, Modifier.testTag("field"),
                    inputTransformation = InputTransformation { if (!asCharSequence().all(Char::isDigit)) revertAllChanges() },
                    outputTransformation = OutputTransformation { append(" units") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        }
        compose.onNodeWithTag("field").performTextInput("12")
        compose.runOnIdle { assertEquals("12", readState()) }
        compose.onNodeWithTag("field").performTextInput("a")
        compose.runOnIdle { assertEquals("12", readState()) }
    }

    @Test
    fun settingsDispatchRendersAndItsSwitchRowOnlyWritesOnce() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val checked = mutableStateOf(false)
        var writes = 0
        compose.setContent {
            Host {
                io.legado.app.ui.widget.components.AppScaffold(
                    topBar = { io.legado.app.ui.widget.components.topbar.GlassSmallTopAppBar("OxygenX") },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppTextField(rememberTextFieldState("Oxygen demo"), label = "Reading name")
                        io.legado.app.ui.widget.components.AppSlider(0.5f, {})
                        io.legado.app.ui.widget.components.settingItem.SwitchSettingItem(
                            title = "Keep screen on", checked = checked.value,
                            onCheckedChange = { checked.value = it; writes++ },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            io.legado.app.ui.widget.components.button.SecondaryButton({}, text = "Cancel")
                            PrimaryButton({}, text = "Preview")
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Keep screen on").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, writes) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = java.io.File("build/outputs/oxygenx-preview.png")
        output.parentFile.mkdirs()
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun oxygenKeepsOpaqueColorsWithoutAMaterialBlurSamplingSource() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var color = Color.Unspecified
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "oxygen"),
                    theme = io.legado.app.domain.model.settings.ThemeSettings(enableBlur = true),
                ),
                applyBackground = false,
            ) {
                val actual = io.legado.app.ui.widget.components.GlassDefaults.glassColor(Color.Red, 0f)
                SideEffect { color = actual }
            }
        }
        compose.runOnIdle { assertEquals(Color.Red, color) }
    }

    @Test
    fun errorTextFieldKeepsLocalizedAccessibilityErrorSemantics() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            Host {
                AppTextField(rememberTextFieldState(), Modifier.testTag("error-field"), isError = true)
            }
        }
        compose.onNodeWithTag("error-field").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, RuntimeEnvironment.getApplication().getString(io.legado.app.R.string.error)),
        )
    }

    @Test
    fun navigationDispatchPreservesSelectionAndCallsTheHostOnce() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val selected = mutableStateOf(0)
        var writes = 0
        compose.setContent {
            Host {
                AppNavigationBar {
                    repeat(2) { index ->
                        AppNavigationBarItem(
                            selected = selected.value == index,
                            onClick = { selected.value = index; writes++ },
                            labelString = "Page $index",
                            iconVector = Icons.Default.Check,
                            m3Icon = { androidx.compose.material3.Icon(Icons.Default.Check, null) },
                            m3IndicatorColor = Color.Red,
                            m3ShowLabel = true,
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Page 0").assertIsSelected()
        compose.onNodeWithText("Page 1").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test
    fun transparentLightPaletteUsesHostDarkModeRatherThanSurfaceLuminance() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var dark = true
        var surface = Color.White
        compose.setContent {
            Host {
                ProvideOxygenTheme(lightColorScheme(surface = Color.Transparent), isDark = false) {
                    val colors = OxygenTheme.colorScheme
                    SideEffect { dark = colors.isDark; surface = colors.surface }
                }
            }
        }
        compose.runOnIdle { assertFalse(dark); assertEquals(Color.Transparent, surface) }
        assertEquals(ComposeEngine.Oxygen, ThemeResolver.resolveComposeEngine("OXYGEN"))
        assertTrue(ThemeResolver.resolveComposeEngine("unknown") == ComposeEngine.Material)
    }
}
