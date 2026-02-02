package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.components.button.SmallTextButton

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
    var inputText by remember { mutableStateOf(value.toInt().toString()) }
    val textFieldState = rememberTextFieldState(initialText = value.toInt().toString())

    SettingItem(
        title = title,
        description = description,
        color = color ?: MaterialTheme.colorScheme.surfaceContainerLow,
        option = value.toInt().toString(),
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
                            label = { Text("输入数值 (${valueRange.start.toInt()}-${valueRange.endInclusive.toInt()})") },
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
                                textFieldState.edit { replace(0, length, it.toInt().toString()) }
                            },
                            valueRange = valueRange,
                            steps = steps,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SmallTextButton(
                    text = if (isInputMode) "滑块" else "输入",
                    icon = if (isInputMode) Icons.Default.LinearScale else Icons.Default.Edit,
                    onClick = { isInputMode = !isInputMode }
                )

                Spacer(Modifier.width(8.dp))

                SmallTextButton(
                    text = "默认",
                    icon = Icons.Default.RestartAlt,
                    onClick = {
                        onValueChange(defaultValue)
                        textFieldState.edit { replace(0, length, defaultValue.toInt().toString()) }
                    }
                )
            }
        }
    )
}