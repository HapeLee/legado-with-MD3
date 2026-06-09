package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
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
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
    trailingContent: (@Composable () -> Unit)? = null,
    expanded: Boolean = false,
    onExpandChange: ((Boolean) -> Unit)? = null,
    expandContent: (@Composable ColumnScope.() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val isExpandable = expandContent != null && onExpandChange != null
    val alpha = if (enabled) 1f else 0.5f

    NormalCard(
        onClick = if (enabled) {
            {
                when {
                    isExpandable -> onExpandChange.invoke(!expanded)
                    else -> onClick?.invoke()
                }
            }
        } else null,
        onLongClick = if (enabled) onLongClick else null,
        modifier = modifier
            .padding(bottom = 4.dp)
            .heightIn(min = 56.dp)
            .fillMaxWidth(),
        cornerRadius = 12.dp,
        containerColor = color?.copy(alpha = alpha),
        contentColor = LegadoTheme.colorScheme.onSurface.copy(alpha = alpha),
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
                        tint = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.titleSmallEmphasized,
                        color = LegadoTheme.colorScheme.onSurface.copy(alpha = alpha),
                    )
                    description?.let {
                        AppText(
                            text = it,
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                tint = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
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
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
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
                    backgroundColor = LegadoTheme.colorScheme.surfaceContainerHigh,
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
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
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
        onExpandChange = { expanded = it },
        enabled = enabled,
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
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        trailingContent = {
            TinySwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
fun TinyClickableSettingItem(
    title: String,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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
        onLongClick = onLongClick,
    )
}

@Composable
fun TinyColorSettingItem(
    title: String,
    colorValue: Int,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        trailingContent = {
            AccentColorButton(
                color = colorValue,
                onClick = onClick,
                enabled = enabled,
            )
        },
        onClick = { onClick() },
    )
}

/**
 * A color setting item with an integrated light/dark mode pill toggle.
 *
 * @param title The title text.
 * @param dayColor The color value for light mode (ARGB int).
 * @param nightColor The color value for dark mode (ARGB int).
 * @param onClickColor Called when the color knob is clicked (passes current mode's selection).
 */
@Composable
fun TinyColorModeSettingItem(
    title: String,
    dayColor: Int,
    nightColor: Int,
    onClickColor: (isNight: Boolean) -> Unit,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
) {
    val currentDarkMode = LegadoTheme.isDark
    var isNightMode by remember(currentDarkMode) { mutableStateOf(currentDarkMode) }

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        trailingContent = {
            ColorModePill(
                dayColor = dayColor,
                nightColor = nightColor,
                isNightMode = isNightMode,
                onToggleMode = { isNightMode = !isNightMode },
                onClickColor = { onClickColor(isNightMode) },
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun ColorModePill(
    dayColor: Int,
    nightColor: Int,
    isNightMode: Boolean,
    onToggleMode: () -> Unit,
    onClickColor: () -> Unit,
    enabled: Boolean = true,
) {
    val pillWidth = 60.dp
    val pillHeight = 32.dp
    val knobSize = 24.dp
    val padding = 4.dp

    val knobOffset by animateDpAsState(
        targetValue = if (isNightMode) pillWidth - knobSize - padding else padding,
        animationSpec = tween(durationMillis = 200),
        label = "knobOffset",
    )

    val currentColor = if (isNightMode) nightColor else dayColor

    Box(
        modifier = Modifier
            .width(pillWidth)
            .height(pillHeight)
            .clip(RoundedCornerShape(pillHeight))
            .background(LegadoTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                enabled = enabled,
                onClick = onToggleMode,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (!isNightMode) {
                    LegadoTheme.colorScheme.onSurface
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                imageVector = Icons.Default.LightMode,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isNightMode) {
                    LegadoTheme.colorScheme.onSurface
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // Knob — color or + icon
        Box(
            modifier = Modifier
                .offset { IntOffset(x = knobOffset.roundToPx(), y = 0) }
                .size(knobSize)
                .clip(CircleShape)
                .background(
                    if (currentColor != 0) Color(currentColor)
                    else LegadoTheme.colorScheme.surfaceContainerLow
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClickColor,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (currentColor == 0) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A color mode setting item with a reset icon to the left of the pill.
 * Shows a reset button when the current mode has a custom color set.
 */
@Composable
fun TinyClearColorModeSettingItem(
    title: String,
    dayColor: Int,
    nightColor: Int,
    onClickColor: (isNight: Boolean) -> Unit,
    onClearColor: (isNight: Boolean) -> Unit,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
) {
    val currentDarkMode = LegadoTheme.isDark
    var isNightMode by remember(currentDarkMode) { mutableStateOf(currentDarkMode) }

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        trailingContent = {
            ClearColorModePill(
                dayColor = dayColor,
                nightColor = nightColor,
                isNightMode = isNightMode,
                enabled = enabled,
                onToggleMode = { isNightMode = !isNightMode },
                onClickColor = { onClickColor(isNightMode) },
                onReset = { onClearColor(isNightMode) },
            )
        },
    )
}

@Composable
private fun ClearColorModePill(
    dayColor: Int,
    nightColor: Int,
    isNightMode: Boolean,
    enabled: Boolean,
    onToggleMode: () -> Unit,
    onClickColor: () -> Unit,
    onReset: () -> Unit,
) {
    val currentColor = if (isNightMode) nightColor else dayColor
    val hasCustomColor = currentColor != 0
    val knobSize = 32.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasCustomColor) {
            Box(
                modifier = Modifier
                    .size(knobSize)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onReset)
                    .border(
                        width = 1.dp,
                        color = LegadoTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ColorModePill(
            dayColor = dayColor,
            nightColor = nightColor,
            isNightMode = isNightMode,
            onToggleMode = onToggleMode,
            onClickColor = onClickColor,
            enabled = enabled,
        )
    }
}

/**
 * A background image mode setting item with separate day/night cards.
 * Each card shows the selected image and a reset button when an image is set.
 */
@Composable
fun TinyBgImageModeSettingItem(
    title: String,
    dayBgImage: String?,
    nightBgImage: String?,
    onClickImage: (isNight: Boolean) -> Unit,
    onClearImage: (isNight: Boolean) -> Unit,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = LegadoTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        expanded = expanded,
        onExpandChange = { expanded = it },
        expandContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                BgImageCard(
                    label = stringResource(R.string.day),
                    bgImage = dayBgImage,
                    enabled = enabled,
                    onClick = { onClickImage(false) },
                    onReset = { onClearImage(false) },
                    modifier = Modifier.weight(1f),
                )
                BgImageCard(
                    label = stringResource(R.string.night),
                    bgImage = nightBgImage,
                    enabled = enabled,
                    onClick = { onClickImage(true) },
                    onReset = { onClearImage(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@Composable
private fun BgImageCard(
    label: String,
    bgImage: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImage = !bgImage.isNullOrBlank()

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LegadoTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (hasImage) {
            AsyncImage(
                model = bgImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.6f,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val labelStyle = if (hasImage) {
                LegadoTheme.typography.labelSmall.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                        blurRadius = 3f,
                    )
                )
            } else {
                LegadoTheme.typography.labelSmall
            }
            AppText(
                text = label,
                style = labelStyle,
                color = if (hasImage) Color.White else LegadoTheme.colorScheme.onSurfaceVariant,
            )

            if (hasImage) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(LegadoTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .clickable(enabled = enabled, onClick = onReset),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = LegadoTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
