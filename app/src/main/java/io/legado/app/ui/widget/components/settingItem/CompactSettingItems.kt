package io.legado.app.ui.widget.components.settingItem

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.legado.app.ui.widget.components.AppSlider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.menu.AppChoiceOption
import io.legado.app.ui.widget.components.sliderAccessibility
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
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
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    onValueChange: (String) -> Unit
) {
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)
            val spinnerItems = displayEntries.toList()
            val startAction: (@Composable () -> Unit)? = if (imageVector != null) {
                { Icon(imageVector = imageVector, contentDescription = null) }
            } else null
            val onSelectedIndexChange: (Int) -> Unit = { index ->
                onValueChange(entryValues[index])
            }
            val hasPopupHost = LocalHasMiuixPopupHost.current
            val useGlass = hasPopupHost && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                LocalAppUiConfiguration.current.theme.useHyperOs4Components
            when {
                useGlass -> GlassDropdownPreference(
                    title = title,
                    summary = description,
                    items = spinnerItems,
                    selectedIndex = selectedIndex,
                    backdrop = LocalMiuixBarBackdrop.current,
                    startAction = startAction,
                    onSelectedIndexChange = onSelectedIndexChange,
                )
                // Scaffold 页面用同 host 的 Miuix 普通 dropdown；window 级弹层没有 host，
                // 只能用 window 级 dropdown。两条回落都保留 Miuix 视觉，不退回 M3。
                hasPopupHost -> OverlayDropdownPreference(
                    title = title,
                    summary = description,
                    items = spinnerItems,
                    selectedIndex = selectedIndex,
                    startAction = startAction,
                    onSelectedIndexChange = onSelectedIndexChange,
                )
                else -> WindowDropdownPreference(
                    title = title,
                    summary = description,
                    items = spinnerItems,
                    selectedIndex = selectedIndex,
                    startAction = startAction,
                    onSelectedIndexChange = onSelectedIndexChange,
                )
            }
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            val currentEntry =
                displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue

            val valueChip: @Composable () -> Unit = {
                TextCard(
                    cornerRadius = 8.dp,
                    horizontalPadding = 8.dp,
                    verticalPadding = 4.dp,
                    text = currentEntry,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            // COUI 也走统一路径：`CouiSettingMenu` 的 `Menu` 拿的是整行 bounds，弹层会从
            // 行左端展开；受控弹层的锚点落在右侧取值 Chip 上。
            SettingItem(
                title = title,
                description = description,
                imageVector = imageVector,
                color = color,
                cornerRadius = cornerRadius,
                trailingContent = valueChip,
                choiceOptions = remember(displayEntries, entryValues) {
                    displayEntries.mapIndexed { index, display ->
                        AppChoiceOption(id = entryValues[index], label = display)
                    }.toImmutableList()
                },
                choiceSelectedId = selectedValue,
                onChoiceSelect = onValueChange,
            )
        }
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
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    onValueChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    var displayValue by remember(value) { mutableFloatStateOf(value) }
    val sliderAccessibilityValue = description ?: displayValue.toString()

    when (composeEngine) {
        ComposeEngine.Miuix -> {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .sliderAccessibility(
                                    label = title,
                                    value = sliderAccessibilityValue,
                                )
                        )
                    }
                }
            }
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            SettingItem(
                title = title,
                description = description,
                imageVector = imageVector,
                color = color,
                cornerRadius = cornerRadius,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .sliderAccessibility(
                                label = title,
                                value = sliderAccessibilityValue,
                            )
                    )
                }
            )
        }
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
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            SwitchPreference(
                title = title,
                summary = description,
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier,
                enabled = enabled,
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            SettingItem(
                title = title,
                description = description,
                imageVector = imageVector,
                color = color,
                cornerRadius = cornerRadius,
                enabled = enabled,
                semanticRole = Role.Switch,
                semanticToggleState = checked,
                onClick = { if (enabled) onCheckedChange(!checked) },
                trailingContent = {
                    if (composeEngine == ComposeEngine.Mochi || composeEngine == ComposeEngine.Coui) {
                        TinySwitch(
                            checked = checked,
                            onCheckedChange = onCheckedChange,
                            enabled = enabled,
                            includeStateSemantics = false,
                        )
                    } else {
                        Switch(
                            modifier = Modifier
                                .scale(0.8f)
                                .clearAndSetSemantics { },
                            checked = checked,
                            onCheckedChange = onCheckedChange,
                            enabled = enabled,
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun CompactClickableSettingItem(
    title: String,
    description: String? = null,
    imageVector: ImageVector? = null,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            ArrowPreference(
                title = title,
                summary = description,
                insideMargin = BasicComponentDefaults.InsideMargin,
                onClick = onClick
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            SettingItem(
                title = title,
                description = description,
                imageVector = imageVector,
                color = color,
                cornerRadius = cornerRadius,
                trailingContent = trailingContent ?: {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = onClick
            )
        }
    }
}
