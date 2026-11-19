package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.widget.components.card.SelectionItemCardContent
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.OxygenGlassScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.oxygenx.theme.OxygenTheme
import java.io.File
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
class OxygenTypographyTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactPageTitlesAndNavigationUseTheCalibratedMetrics() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val collapse = mutableStateOf(false)
        var standardRoleSize = 0.sp
        var mediumRoleSize = 0.sp
        compose.setContent {
            AppTheme(
                AppUiConfiguration(appShell = AppShellSettings(composeEngine = "oxygen"), theme = ThemeSettings(useFlexibleTopAppBar = true)),
                applyBackground = false,
            ) {
                val state = io.oxygenx.ui.navigation.rememberTopAppBarState(initialCollapsedFraction = if (collapse.value) 1f else 0f)
                val type = MaterialTheme.typography
                SideEffect {
                    standardRoleSize = type.titleLarge.fontSize
                    mediumRoleSize = type.titleMedium.fontSize
                }
                Column(Modifier.width(360.dp).background(MaterialTheme.colorScheme.surface)) {
                    GlassTopAppBar(
                        "Standard title",
                        navigationIcon = { TopBarNavigationButton(onClick = {}, contentDescription = "Standard back") },
                    )
                    GlassMediumFlexibleTopAppBar(
                        "Page title",
                        scrollBehavior = OxygenGlassScrollBehavior(state),
                        navigationIcon = { TopBarNavigationButton(onClick = {}, contentDescription = "Page back") },
                    )
                }
            }
        }
        fun layout(label: String): TextStyle {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single().layoutInput.style
        }
        fun iconWidth(label: String) = compose.onNodeWithContentDescription(label, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().let { it.right - it.left }

        assertEquals(18.sp, layout("Standard title").fontSize)
        assertEquals(24.sp, layout("Standard title").lineHeight)
        assertEquals(16.dp, iconWidth("Standard back"))
        assertEquals(24.dp, iconWidth("Page back"))
        assertEquals(FontWeight.Normal, layout("Standard title").fontWeight)
        assertEquals(45.sp, layout("Page title").fontSize)
        assertEquals(52.sp, layout("Page title").lineHeight)
        compose.runOnIdle {
            assertEquals(22.sp, standardRoleSize)
            assertEquals(16.sp, mediumRoleSize)
        }
        val file = File("build/outputs/oxygenx-appbar-titles.png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { collapse.value = true }
        assertEquals(18.sp, layout("Page title").fontSize)
        assertEquals(24.sp, layout("Page title").lineHeight)
        assertEquals(16.dp, iconWidth("Page back"))
        assertEquals(FontWeight.Normal, layout("Page title").fontWeight)
        File("build/outputs/oxygenx-appbar-titles-collapsed.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun sourceManagementSharedRowAndAllHostRolesMatchMaterialAcrossEngineSwitches() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val engine = mutableStateOf("material")
        var actual: LegadoTypography? = null
        compose.setContent {
            AppTheme(
                AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine.value), theme = ThemeSettings(useMiuixMonet = false)),
                applyBackground = false,
            ) {
                val typography = LegadoTheme.typography
                SideEffect { actual = typography }
                Column(Modifier.width(360.dp).background(MaterialTheme.colorScheme.surface)) {
                    // BookSourceScreen -> ReorderableSelectionItem uses this exact shared content.
                    SelectionItemCardContent(title = "书源示例 Source title", subtitle = "分组与校验说明")
                }
            }
        }
        var baseline: LegadoTypography? = null
        var metrics: List<Any?>? = null
        for (id in listOf("material", "oxygen", "material", "oxygen")) {
            compose.runOnIdle { engine.value = id }
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("书源示例 Source title", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val style = results.single().layoutInput.style
            val currentMetrics = listOf(style.fontSize, style.lineHeight, style.fontWeight, style.letterSpacing, style.fontFamily)
            compose.runOnIdle {
                if (baseline == null) { baseline = actual; metrics = currentMetrics }
                assertEquals(baseline, actual)
                assertEquals(metrics, currentMetrics)
                assertEquals(14.sp, style.fontSize)
                assertEquals(20.sp, style.lineHeight)
            }
        }
        val file = File("build/outputs/oxygenx-source-typography.png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun customMaterialRoleStylesAndHostTypographyArePreserved() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val custom = Typography().copy(
            titleSmall = Typography().titleSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 17.sp, lineHeight = 27.sp),
            headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.Serif, fontSize = 31.sp),
            labelSmall = Typography().labelSmall.copy(letterSpacing = 2.sp),
            titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.Serif, fontSize = 25.sp),
        )
        val host = custom.toLegadoTypography()
        var actualHost: LegadoTypography? = null
        var actual: io.oxygenx.theme.OxygenTypography? = null
        compose.setContent {
            MaterialTheme(typography = custom) {
                CompositionLocalProvider(LocalLegadoTypography provides host) {
                    ProvideOxygenTheme(isDark = false) {
                        val typography = OxygenTheme.typography
                        val legado = LegadoTheme.typography
                        SideEffect { actual = typography; actualHost = legado }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(host, actualHost)
            assertEquals(custom.titleSmall, actual?.titleSmall)
            assertEquals(custom.titleLarge, actual?.titleLarge)
            assertEquals(custom.headlineMedium, actual?.headlineMedium)
            assertEquals(custom.labelSmall, actual?.labelSmall)
            assertEquals(custom.bodyLarge, actual?.bodyLarge)
        }
    }
}
