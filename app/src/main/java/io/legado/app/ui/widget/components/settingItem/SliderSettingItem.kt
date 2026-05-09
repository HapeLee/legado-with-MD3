package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

@Composable
fun SliderSettingItem(
    title: String,
    color: Color? = null,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    onValueChange: (Float) -> Unit
) {

    var expanded by remember { mutableStateOf(false) }
    var isInputMode by remember { mutableStateOf(false) }
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val textFieldState = rememberTextFieldState()

    LaunchedEffect(value) {
        sliderValue = value
    }

    LaunchedEffect(isInputMode) {
        if (isInputMode) {
            textFieldState.edit {
                replace(0, length, value.toString())
            }
        }
    }

    fun commitValue() {
        if (isInputMode) {
            textFieldState.text.toString().toFloatOrNull()?.let { num ->
                val rounded = (num * 10).roundToInt() / 10f
                onValueChange(rounded.coerceIn(valueRange))
            }
        } else if (sliderValue != value) {
            onValueChange(sliderValue)
        }
    }

    SplicedColumnDivider()

    if (ThemeResolver.isMiuixEngine(composeEngine)) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            BasicComponent(
                title = title,
                summary = description,
                onClick = {
                    if (expanded) {
                        commitValue()
                    }
                    expanded = !expanded
                }
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    AnimatedContent(
                        targetState = isInputMode,
                        label = "input_slider_switch"
                    ) { targetInputMode ->
                        if (targetInputMode) {
                            MiuixTextField(
                                state = textFieldState,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                label = stringResource(
                                    R.string.input_value_range,
                                    valueRange.start.toInt(),
                                    valueRange.endInclusive.toInt()
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            MiuixSlider(
                                value = sliderValue,
                                onValueChange = {
                                    sliderValue = (it * 10).roundToInt() / 10f
                                    textFieldState.edit {
                                        replace(0, length, sliderValue.toString())
                                    }
                                },
                                onValueChangeFinished = {
                                    onValueChange(sliderValue.coerceIn(valueRange))
                                },
                                valueRange = valueRange,
                                steps = steps,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    ConfirmDismissButtonsRow(
                        modifier = Modifier.padding(top = 16.dp),
                        onDismiss = { isInputMode = !isInputMode },
                        onConfirm = {
                            onValueChange(defaultValue)
                            textFieldState.edit {
                                replace(
                                    0,
                                    length,
                                    defaultValue.toInt().toString()
                                )
                            }
                        },
                        dismissText = if (isInputMode) {
                            stringResource(R.string.slider)
                        } else {
                            stringResource(R.string.edit)
                        },
                        confirmText = stringResource(R.string.text_default)
                    )
                }
            }
        }

    } else {
        SettingItem(
            title = title,
            option = description,
            expanded = expanded,
            onExpandChange = {
                if (expanded) {
                    commitValue()
                }
                expanded = it
            },
            expandContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(
                        targetState = isInputMode,
                        label = "input_slider_switch"
                    ) { targetInputMode ->
                        if (targetInputMode) {
                            TextField(
                                state = textFieldState,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                label = {
                                    AppText(
                                        stringResource(
                                            R.string.input_value_range,
                                            valueRange.start.toInt(),
                                            valueRange.endInclusive.toInt()
                                        )
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                contentPadding = PaddingValues(
                                    top = 4.dp,
                                    bottom = 4.dp,
                                    start = 12.dp,
                                    end = 12.dp
                                )
                            )
                        } else {
                            Slider(
                                value = sliderValue,
                                onValueChange = {
                                    sliderValue = (it * 10).roundToInt() / 10f
                                    textFieldState.edit {
                                        replace(0, length, sliderValue.toString())
                                    }
                                },
                                onValueChangeFinished = {
                                    onValueChange(sliderValue.coerceIn(valueRange))
                                },
                                valueRange = valueRange,
                                steps = steps,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                ConfirmDismissButtonsRow(
                    modifier = Modifier.padding(top = 16.dp),
                    onDismiss = { isInputMode = !isInputMode },
                    onConfirm = {
                        onValueChange(defaultValue)
                        textFieldState.edit {
                            replace(
                                0,
                                length,
                                defaultValue.toInt().toString()
                            )
                        }
                    },
                    dismissText = if (isInputMode) {
                        stringResource(R.string.slider)
                    } else {
                        stringResource(R.string.edit)
                    },
                    confirmText = stringResource(R.string.text_default)
                )
            }
        )
    }
}

