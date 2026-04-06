package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactDropdownSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    color: Color? = LegadoTheme.colorScheme.surface,
    shape: Shape = MaterialTheme.shapes.small,
    onValueChange: (String) -> Unit
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)
        val spinnerItems = displayEntries.toList()

        WindowDropdownPreference(
            title = title,
            summary = description,
            items = spinnerItems,
            selectedIndex = selectedIndex,
            startAction = imageVector?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null
                    )
                }
            },
            onSelectedIndexChange = { index ->
                onValueChange(entryValues[index])
            }
        )
    } else {
        val currentEntry =
            displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue

        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            color = color,
            shape = shape,
            trailingContent = {
                TextCard(
                    cornerRadius = 8.dp,
                    horizontalPadding = 8.dp,
                    verticalPadding = 4.dp,
                    text = currentEntry,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            },
            dropdownMenu = { onDismiss ->
                displayEntries.forEachIndexed { index, display ->
                    RoundDropdownMenuItem(
                        text = display,
                        onClick = {
                            onValueChange(entryValues[index])
                            onDismiss()
                        },
                        trailingIcon = if (selectedValue == entryValues[index]) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactSliderSettingItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    imageVector: ImageVector? = null,
    color: Color? = MaterialTheme.colorScheme.surface,
    shape: Shape = MaterialTheme.shapes.small,
    onValueChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    var displayValue by remember(value) { mutableFloatStateOf(value) }

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = title,
                summary = description,
                onClick = { expanded = !expanded },
                endActions = {
                    ValueStepper(
                        value = value,
                        displayValue = displayValue,
                        valueRange = valueRange,
                        onValueChange = onValueChange
                    )
                }
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    MiuixSlider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            displayValue = it
                        },
                        onValueChangeFinished = {
                            onValueChange(sliderValue)
                        },
                        valueRange = valueRange,
                        steps = steps,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    } else {
        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            color = color,
            shape = shape,
            expanded = expanded,
            onExpandChange = { expanded = it },
            trailingContent = {
                ValueStepper(
                    value = value,
                    displayValue = displayValue,
                    valueRange = valueRange,
                    onValueChange = onValueChange
                )
            },
            expandContent = {
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        displayValue = it
                    },
                    onValueChangeFinished = {
                        onValueChange(sliderValue)
                    },
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    LaunchedEffect(value) {
        if (!expanded) {
            sliderValue = value
            displayValue = value
        }
    }
}

@Composable
fun CompactSwitchSettingItem(
    title: String,
    checked: Boolean,
    description: String? = null,
    imageVector: ImageVector? = null,
    color: Color? = MaterialTheme.colorScheme.surface,
    shape: Shape = MaterialTheme.shapes.small,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        SwitchPreference(
            title = title,
            summary = description,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    } else {
        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            color = color,
            shape = shape,
            onClick = { if (enabled) onCheckedChange(!checked) },
            trailingContent = {
                Switch(
                    modifier = Modifier.scale(0.8f),
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }
        )
    }
}
