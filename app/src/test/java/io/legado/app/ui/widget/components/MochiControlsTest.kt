package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.TextAnimationLevel
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.text.AnimatedText
import io.legado.app.ui.widget.components.text.AnimatedTextLine
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
class MochiControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(engine: String = "mochi", content: @Composable () -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(
                AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine)),
                applyBackground = false,
            ) {
                Column(Modifier.width(360.dp)) { content() }
            }
        }
    }

    @Test
    @Config(sdk = [26, 35])
    fun animatedTextExposesOnlyTheCurrentTextAndCanDisableTheTransition() {
        val label = mutableStateOf("Reading")
        val count = mutableStateOf("12")
        val level = mutableStateOf(TextAnimationLevel.Standard)
        show(engine = "material") {
            val configuration = LocalAppUiConfiguration.current
            CompositionLocalProvider(
                LocalAppUiConfiguration provides configuration.copy(
                    appShell = configuration.appShell.copy(textAnimationLevel = level.value)
                )
            ) {
                AnimatedTextLine(label.value)
                AnimatedText(count.value)
            }
        }
        compose.onNodeWithText("Reading").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            label.value = "Finished"
            count.value = "13"
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Finished").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Reading").assertDoesNotExist()
        compose.onNodeWithText("13").assertExists()

        compose.runOnIdle {
            level.value = TextAnimationLevel.Off
            label.value = "Stopped"
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Stopped").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Stopped").assertIsDisplayed()
        compose.onNodeWithText("Finished").assertDoesNotExist()
    }

    @Test
    fun sheetSettingsKeepDecimalStepsAndOpenTheDropdown() {
        val value = mutableFloatStateOf(0.5f)
        val selection = mutableStateOf("manual")
        val checked = mutableStateOf(false)
        var finishes = 0
        var toggles = 0
        show {
            AppModalBottomSheet(show = true, onDismissRequest = {}) {
                TinySliderSettingItem(
                    title = "Playback speed",
                    value = value.floatValue,
                    valueRange = 0.25f..2f,
                    stepSize = 0.25f,
                    showDecimal = true,
                    onValueChange = { value.floatValue = it },
                    onValueChangeFinished = { finishes++ },
                )
                TinyDropdownSettingItem(
                    title = "Playback mode",
                    selectedValue = selection.value,
                    displayEntries = arrayOf("Manual", "Automatic"),
                    entryValues = arrayOf("manual", "automatic"),
                    onValueChange = { selection.value = it },
                )
                CompactSwitchSettingItem("Resume playback", checked = checked.value) {
                    checked.value = it
                    toggles++
                }
            }
        }
        val increase = RuntimeEnvironment.getApplication().getString(R.string.a11y_increase)
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        compose.onNodeWithContentDescription(increase)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performTouchInput { click() }
        slider.assertDoesNotExist()
        compose.onNodeWithText("Playback speed").performClick()
        slider.assertIsDisplayed()
        val switch = compose.onNodeWithText("Resume playback").assertIsOff()
        switch.performTouchInput { click(Offset(width * 0.9f, centerY)) }
        switch.assertIsOn()
        compose.onNodeWithText("Playback mode").performTouchInput { click() }
        compose.onNodeWithText("Automatic").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(0.75f, value.floatValue)
            assertEquals(1, finishes)
            assertEquals(1, toggles)
            assertEquals("automatic", selection.value)
        }
    }

    @Test
    fun checkboxesKeepInteractiveAndDisplayOnlyModes() {
        val checked = mutableStateOf(false)
        show {
            AppCheckbox(
                checked = checked.value,
                onCheckedChange = { checked.value = it },
                modifier = Modifier.testTag("active"),
            )
            AppCheckbox(
                checked = true,
                onCheckedChange = null,
                modifier = Modifier.testTag("display"),
            )
        }
        val active = compose.onNodeWithTag("active").assertIsOff()
        active.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        active.performTouchInput { click() }.assertIsOn()
        compose.onNodeWithTag("display").assertIsOn().assertHasNoClickAction()
    }

    @Test
    @Config(sdk = [26])
    fun minimumAndroidVersionSupportsTheBasicControls() {
        val checked = mutableStateOf(false)
        val value = mutableFloatStateOf(0f)
        var presses = 0
        show {
            PrimaryButton({ presses++ }, text = "Save")
            TinySwitch(checked.value, { checked.value = it }, Modifier.testTag("switch"))
            AppSlider(value.floatValue, { value.floatValue = it }, Modifier.testTag("slider"))
        }
        compose.onNodeWithText("Save").performTouchInput { click() }
        compose.onNodeWithTag("switch").performTouchInput { click() }
        compose.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.runOnIdle {
            assertEquals(1, presses)
            assertTrue(checked.value)
            assertEquals(0.5f, value.floatValue)
        }
    }

    @Test
    fun buttonsKeepNamesAndSupportPointerKeyboardAndDisabledState() {
        val enabled = mutableStateOf(true)
        var saves = 0
        var cancels = 0
        show {
            PrimaryButton({ saves++ }, enabled = enabled.value, text = "Save")
            SecondaryButton({ cancels++ }, text = "Cancel")
        }
        val save = compose.onNodeWithText("Save").assertHasClickAction()
        save.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        save.performClick()
        save.performTouchInput { click() }
        save.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        save.performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(3, saves); assertEquals(1, cancels); enabled.value = false }
        save.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(3, saves) }
    }

    @Test
    fun standaloneSwitchSupportsKeyboardAndDoesNotToggleWhileDisabled() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var changes = 0
        show {
            AdaptiveSwitch(
                checked.value,
                { checked.value = it; changes++ },
                Modifier.testTag("switch").semantics { contentDescription = "Notifications" },
                enabled = enabled.value,
            )
        }
        val control = compose.onNodeWithTag("switch").assertIsOff()
        control.performClick().assertIsOn()
        control.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        control.performKeyInput { pressKey(Key.Spacebar) }
        control.assertIsOff().performTouchInput { click() }
        control.assertIsOn()
        compose.runOnIdle { assertEquals(3, changes); enabled.value = false }
        control.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(3, changes) }
    }

    @Test
    fun settingRowExposesOneToggleAndEachTapChangesTheValueOnce() {
        val checked = mutableStateOf(false)
        var changes = 0
        show {
            SwitchSettingItem("Feature", checked = checked.value, onCheckedChange = {
                checked.value = it
                changes++
            })
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        val row = compose.onNodeWithText("Feature").assertIsOff()
        row.performClick().assertIsOn()
        row.performTouchInput { click(Offset(width - 40f, centerY)) }
        row.assertIsOff()
        compose.runOnIdle { assertEquals(2, changes) }
    }

    @Test
    fun sliderSnapsAccessibilityAndKeyboardValuesAndHonorsDisabledState() {
        val value = mutableFloatStateOf(0f)
        val enabled = mutableStateOf(true)
        var finishes = 0
        show {
            AppSlider(
                value.floatValue, { value.floatValue = it }, Modifier.testTag("slider"),
                enabled = enabled.value, valueRange = 0f..100f, steps = 3,
                onValueChangeFinished = { finishes++ }, accessibilityLabel = "Volume",
            )
        }
        val slider = compose.onNodeWithTag("slider")
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(37f) }
        compose.runOnIdle { assertEquals(25f, value.floatValue); assertEquals(1, finishes) }
        slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(50f, value.floatValue); assertEquals(2, finishes); enabled.value = false }
        slider.assertIsNotEnabled().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
    }

    @Test
    fun sliderDragSnapsAndCommitsOnceOnRelease() {
        val value = mutableFloatStateOf(0f)
        var finishes = 0
        show {
            AppSlider(
                value.floatValue, { value.floatValue = it }, Modifier.testTag("slider"),
                valueRange = 0f..100f, steps = 3, onValueChangeFinished = { finishes++ },
            )
        }
        compose.onNodeWithTag("slider").performTouchInput {
            swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY), 400)
        }
        compose.runOnIdle {
            assertTrue(value.floatValue > 0f)
            assertEquals(0f, value.floatValue % 25f)
            assertEquals(1, finishes)
        }
    }
}
