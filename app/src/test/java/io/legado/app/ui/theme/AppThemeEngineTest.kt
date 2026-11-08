package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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
}
