package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.coui.AppCouiSearchBar
import kotlin.math.abs
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
@Config(application = Application::class, sdk = [34], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SearchBarEngineTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun oxygenCentersHintAndInputAndSubmitsCurrentText() {
        val query = mutableStateOf("")
        var submitted: String? = null
        setContent("oxygen") {
            SearchBar(
                modifier = Modifier.width(320.dp).testTag("search-bar"),
                query = query.value,
                onQueryChange = { query.value = it },
                onSearch = { submitted = it },
                placeholder = "Find books",
                autoFocus = false,
                leadingIcon = { Box(Modifier.size(20.dp).testTag("leading")) },
            )
        }
        val iconBounds = compose.onNodeWithTag("leading").getUnclippedBoundsInRoot()
        val hintBounds = compose.onNodeWithText("Find books", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val field = compose.onNode(hasSetTextAction())
        val inputBounds = field.getUnclippedBoundsInRoot()
        val barBounds = compose.onNodeWithTag("search-bar").getUnclippedBoundsInRoot()
        assertEquals(48.dp, barBounds.bottom - barBounds.top - 4.dp)
        val surfaceCenter = (barBounds.top + barBounds.bottom - 4.dp) / 2
        assertEquals(surfaceCenter, (iconBounds.top + iconBounds.bottom) / 2)
        assertEquals(surfaceCenter.value, ((hintBounds.top + hintBounds.bottom) / 2).value, 1f)
        assertEquals(surfaceCenter.value, ((inputBounds.top + inputBounds.bottom) / 2).value, 1f)

        field.performTextInput("Ocean")
        field.performImeAction()
        compose.runOnIdle { assertEquals("Ocean", submitted) }
    }

    @Test
    fun oxygenSearchMatchesTheHostMaterialFieldHeightAndInsets() {
        setContent("material") {
            Column(Modifier.width(320.dp)) {
                Column(Modifier.testTag("material-search")) {
                    SearchBar(query = "", onQueryChange = {}, placeholder = "Material hint", autoFocus = false)
                }
                AppTheme(
                    AppUiConfiguration(appShell = AppShellSettings(composeEngine = "oxygen")),
                    applyBackground = false,
                ) {
                    Column(Modifier.testTag("oxygen-search")) {
                        SearchBar(query = "", onQueryChange = {}, placeholder = "Oxygen hint", autoFocus = false)
                    }
                }
            }
        }
        val material = compose.onNodeWithTag("material-search").getUnclippedBoundsInRoot()
        val oxygen = compose.onNodeWithTag("oxygen-search").getUnclippedBoundsInRoot()
        assertEquals(material.bottom - material.top, oxygen.bottom - oxygen.top)

        val materialHint = compose.onNodeWithText("Material hint", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val oxygenHint = compose.onNodeWithText("Oxygen hint", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val startInsetDifference = (materialHint.left - material.left) - (oxygenHint.left - oxygen.left)
        assertTrue("Search text inset difference was $startInsetDifference", abs(startInsetDifference.value) <= 4f)
    }

    @Test
    @Config(sdk = [33])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun couiUsesNativeSearchWithCallerWidthAndKeepsClearAndTrailingAction() {
        val query = mutableStateOf("")
        var cleared = 0
        var submitted: String? = null
        setContent("coui") {
            // Exercise the COUI component's solid-color path. Robolectric cannot build its glass mesh.
            AppCouiSearchBar(
                modifier = Modifier.width(320.dp).testTag("search-bar"),
                query = query.value,
                onQueryChange = { query.value = it },
                onSearch = { submitted = it },
                placeholder = "Find books",
                autoFocus = false,
                backgroundColor = Color.Unspecified,
                leadingIcon = null,
                clearIcon = null,
                onClear = {
                    cleared++
                    query.value = ""
                },
                clearDescription = "Clear books",
                trailingIcon = { Box(Modifier.size(40.dp).testTag("sort")) },
            )
        }
        val collapsed = compose.onNode(hasClickAction() and hasContentDescription("Find books"))
        val root = compose.onNodeWithTag("search-bar").getUnclippedBoundsInRoot()
        val bounds = collapsed.getUnclippedBoundsInRoot()
        val action = compose.onNodeWithTag("sort")
        assertEquals(root.left, bounds.left)
        assertEquals(action.getUnclippedBoundsInRoot().left, bounds.right)

        collapsed.performClick()
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("Ocean")
        compose.runOnIdle { assertEquals("Ocean", query.value) }
        compose.onNode(hasClickAction() and hasContentDescription("Clear books")).performClick()
        compose.runOnIdle {
            assertEquals("", query.value)
            assertEquals(1, cleared)
        }
        action.assertIsDisplayed()
        field.performTextInput("Coast")
        field.performImeAction()
        compose.runOnIdle { assertEquals("Coast", submitted) }
        collapsed.assertIsDisplayed()
    }

    @Test
    fun couiKeepsInputAvailableWhenPlatformCannotCreateSearchMaterial() {
        val query = mutableStateOf("")
        setContent("coui") {
            SearchBar(query = query.value, onQueryChange = { query.value = it }, autoFocus = true)
        }
        compose.onNode(hasSetTextAction()).performTextInput("Harbor")
        compose.runOnIdle { assertEquals("Harbor", query.value) }
    }

    private fun setContent(engine: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(
                AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine)),
                applyBackground = false,
                content = {
                    CompositionLocalProvider(LocalCouiContourGlowEnabled provides false, content = content)
                },
            )
        }
    }
}
