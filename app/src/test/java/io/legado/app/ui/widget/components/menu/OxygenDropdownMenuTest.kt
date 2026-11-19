package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.oxygenx.theme.OxygenTheme
import java.io.File
import org.junit.Assert.assertEquals
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
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OxygenDropdownMenuTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Host(dark: Boolean = false, content: @Composable () -> Unit) {
        AppTheme(
            AppUiConfiguration(appShell = AppShellSettings(composeEngine = "oxygen"), theme = ThemeSettings(useMiuixMonet = false)),
            darkTheme = dark, applyBackground = false, content = content,
        )
    }

    private fun labelLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    @Test
    fun nativeItemBridgesCompactStyleDisabledSlotsPaddingAndInteractions() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val enabled = mutableStateOf(false)
        val source = MutableInteractionSource()
        val events = mutableListOf<androidx.compose.foundation.interaction.Interaction>()
        var disabled = Color.Unspecified
        var leading = Color.Unspecified
        var trailing = Color.Unspecified
        var slotSize = 0.sp
        var density = 1f
        var clicks = 0
        compose.setContent {
            Host {
                val expected = OxygenTheme.colorScheme.onSurfaceDisabled
                val scale = LocalDensity.current.density
                SideEffect { disabled = expected; density = scale }
                LaunchedEffect(source) { source.interactions.collect { events += it } }
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 30.sp), LocalContentColor provides Color.Magenta) {
                    RoundDropdownMenuItem(
                        text = "Selected", color = Color.Magenta, onClick = { clicks++ },
                        modifier = Modifier.width(240.dp).testTag("row"), isSelected = true, enabled = enabled.value,
                        contentPadding = PaddingValues(start = 28.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        interactionSource = source,
                        leadingIcon = {
                            val color = LocalContentColor.current
                            val size = LocalTextStyle.current.fontSize
                            SideEffect { leading = color; slotSize = size }
                            AppIcon(AppIcons.Info, null)
                        },
                        trailingIcon = {
                            val color = LocalContentColor.current
                            SideEffect { trailing = color }
                            AppIcon(AppIcons.Check, null)
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("Selected").assertIsNotEnabled().assertIsSelected().performClick()
        val layout = labelLayout("Selected")
        compose.runOnIdle {
            assertEquals(14.sp, layout.layoutInput.style.fontSize)
            assertEquals(disabled, layout.layoutInput.style.color)
            assertEquals(disabled, leading)
            assertEquals(disabled, trailing)
            assertEquals(14.sp, slotSize)
            assertEquals(0, clicks)
        }
        val row = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText("Selected", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // 28dp inset + 24dp leading slot + 12dp icon gap.
        assertEquals(64f * density, text.left - row.left, 0.5f)
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithText("Selected").performClick()
        assertEquals(Color.Magenta, labelLayout("Selected").layoutInput.style.color)
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(1, events.filterIsInstance<PressInteraction.Press>().size)
            assertEquals(1, events.filterIsInstance<PressInteraction.Release>().size)
        }
    }

    @Test
    fun actualPopupRendersBothThemesAndSelectionClosesOnce() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val dark = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        var writes = 0
        var dismissals = 0
        compose.setContent {
            Host(dark.value) {
                Box(Modifier.width(280.dp)) {
                    AppText("Anchor")
                    RoundDropdownMenu(expanded.value, { dismissals++; expanded.value = false }, Modifier.testTag("popup")) { dismiss ->
                        RoundDropdownMenuItem("By name", onClick = { writes++; dismiss() }, isSelected = true,
                            trailingIcon = { AppIcon(AppIcons.Check, null, tint = LegadoTheme.colorScheme.primary) })
                        RoundDropdownMenuItem("Edit", onClick = {}, leadingIcon = { AppIcon(AppIcons.Edit, null) })
                        RoundDropdownMenuItem("Delete", onClick = {}, leadingIcon = { AppIcon(AppIcons.Delete, null) })
                        RoundDropdownMenuItem("Unavailable", onClick = {}, enabled = false, leadingIcon = { AppIcon(AppIcons.Info, null) })
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            val file = File("build/outputs/oxygenx-dropdown-${if (night) "dark" else "light"}.png")
            checkNotNull(file.parentFile).mkdirs()
            file.outputStream().use {
                compose.onNodeWithTag("popup").captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            assertEquals(14.sp, labelLayout("By name").layoutInput.style.fontSize)
        }
        compose.onNodeWithText("By name").performClick()
        compose.onNodeWithTag("popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, writes); assertEquals(1, dismissals) }
    }

    @Test
    fun longPopupScrollsToTheLastChoiceWithoutLosingTheCallback() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val expanded = mutableStateOf(true)
        var writes = 0
        compose.setContent {
            Host {
                Box(Modifier.width(280.dp)) {
                    RoundDropdownMenu(expanded.value, { expanded.value = false }) { dismiss ->
                        repeat(40) { index ->
                            RoundDropdownMenuItem("Choice $index", onClick = { writes++; dismiss() })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Choice 39").performScrollTo().performClick()
        compose.onNodeWithText("Choice 39").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, writes); assertTrue(!expanded.value) }
    }
}
