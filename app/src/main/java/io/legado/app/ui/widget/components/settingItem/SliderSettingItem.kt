package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

@Composable
fun SliderSettingItem(
    title: String,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    onValueChange: (Float) -> Unit
) {

    var expanded by remember { mutableStateOf(false) }
    var isInputMode by remember { mutableStateOf(false) }
    val textFieldState = rememberTextFieldState(initialText = value.toInt().toString())

    if (ThemeResolver.isMiuixEngine(composeEngine)) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            BasicComponent(
                title = title,
                summary = description,
                onClick = { expanded = !expanded }
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
                                modifier = Modifier.fillMaxWidth(),
                                inputTransformation = {
                                    val newText = asCharSequence().toString()
                                    newText.toFloatOrNull()?.let { num ->
                                        onValueChange(
                                            num.coerceIn(
                                                valueRange.start,
                                                valueRange.endInclusive
                                            )
                                        )
                                    }
                                }
                            )
                        } else {
                            MiuixSlider(
                                value = value,
                                onValueChange = {
                                    onValueChange(it)
                                    textFieldState.edit {
                                        replace(
                                            0,
                                            length,
                                            it.toInt().toString()
                                        )
                                    }
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
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            option = description,
            expanded = expanded,
            onExpandChange = { expanded = it },
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
                                ),
                                inputTransformation = {
                                    val newText = asCharSequence().toString()
                                    newText.toFloatOrNull()?.let { num ->
                                        onValueChange(
                                            num.coerceIn(
                                                valueRange.start,
                                                valueRange.endInclusive
                                            )
                                        )
                                    }
                                }
                            )
                        } else {
                            Slider(
                                value = value,
                                onValueChange = {
                                    onValueChange(it)
                                    textFieldState.edit {
                                        replace(
                                            0,
                                            length,
                                            it.toInt().toString()
                                        )
                                    }
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

