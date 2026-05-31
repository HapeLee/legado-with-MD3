package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AccentColorButton
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun TinySettingItem(
    title: String,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    trailingContent: (@Composable () -> Unit)? = null,
    expanded: Boolean = false,
    onExpandChange: ((Boolean) -> Unit)? = null,
    expandContent: (@Composable ColumnScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val isExpandable = expandContent != null && onExpandChange != null

    NormalCard(
        onClick = {
            when {
                isExpandable -> onExpandChange.invoke(!expanded)
                else -> onClick?.invoke()
            }
        },
        modifier = modifier
            .padding(bottom = 4.dp)
            .heightIn(min = 56.dp)
            .fillMaxWidth(),
        cornerRadius = 12.dp,
        containerColor = color,
        contentColor = LegadoTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                imageVector?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.titleSmallEmphasized,
                    )
                    description?.let {
                        AppText(
                            text = it,
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    when {
                        trailingContent != null -> trailingContent()
                        isExpandable -> {
                            val rotation by animateFloatAsState(
                                targetValue = if (expanded) 180f else 0f,
                                label = "tinySettingArrow",
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotation),
                            )
                        }
                    }
                }
            }

            if (isExpandable) {
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    ) {
                        expandContent.invoke(this)
                    }
                }
            }
        }
    }
}

@Composable
fun TinyDropdownSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    onValueChange: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val currentEntry = displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue

    Box(modifier = Modifier.fillMaxWidth()) {
        TinySettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            modifier = modifier,
            color = color,
            trailingContent = {
                TextCard(
                    cornerRadius = 8.dp,
                    horizontalPadding = 8.dp,
                    verticalPadding = 4.dp,
                    text = currentEntry,
                    backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
                    contentColor = LegadoTheme.colorScheme.onSurface,
                )
            },
            onClick = { showMenu = true },
        )

        RoundDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) { dismiss ->
            displayEntries.forEachIndexed { index, display ->
                RoundDropdownMenuItem(
                    text = display,
                    onClick = {
                        onValueChange(entryValues[index])
                        dismiss()
                    },
                    trailingIcon = if (selectedValue == entryValues[index]) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
fun TinySliderSettingItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    var displayValue by remember(value) { mutableFloatStateOf(value) }

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        expanded = expanded,
        onExpandChange = { if (enabled) expanded = it },
        trailingContent = {
            ValueStepper(
                value = value,
                displayValue = displayValue,
                valueRange = valueRange,
                onValueChange = onValueChange,
                enabled = enabled,
            )
        },
        expandContent = {
            AppSlider(
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
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )

    LaunchedEffect(value) {
        if (!expanded) {
            sliderValue = value
            displayValue = value
        }
    }
}

@Composable
fun TinySwitchSettingItem(
    title: String,
    checked: Boolean,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        trailingContent = {
            TinySwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        onClick = { if (enabled) onCheckedChange(!checked) },
    )
}

@Composable
fun TinyClickableSettingItem(
    title: String,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        trailingContent = trailingContent ?: {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
fun TinyColorSettingItem(
    title: String,
    colorValue: Int,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        trailingContent = {
            AccentColorButton(
                color = colorValue,
                onClick = onClick,
                enabled = enabled,
            )
        },
        onClick = { if (enabled) onClick() },
    )
}
