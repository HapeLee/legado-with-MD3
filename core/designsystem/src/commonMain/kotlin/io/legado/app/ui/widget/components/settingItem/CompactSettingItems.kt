package io.legado.app.ui.widget.components.settingItem

/**
 * M5-15c：从 `:core:ui` 搬进 `:core:designsystem/commonMain`（包名不变 ⇒ 10 处 `:app` 消费方
 * import 零改动）。触发条件同 `CardTabRow`(M5-9b-pre) / `TimePickerDialog`(M5-10a-pre)：
 * **共享层出现第一个消费者** —— `themeManage` 的 `EditThemeSheet` 与 `themeConfig` 的两个 sheet
 * 都要用它，而它们是 `ui/config` 剩余两块的前置。
 *
 * ⚠️ **不是纯搬运**（M5-15b 的原计划在这里落空，见下）：
 *
 * 1. 三个组件的 Miuix 分支原先**直接调** `top.yukonga.miuix.kmp.preference` 的
 *    `WindowDropdownPreference` / `SwitchPreference` / `ArrowPreference`，而该制品**没有
 *    desktop 变体**（只有 `miuix-preference-android`）⇒ 直接引用进不了 `commonMain`。
 *    改写为走既有的窄契约 [MiuixPreferenceRenderer]（宿主注入点
 *    [MiuixPreferenceRendererProvider]），与 `SwitchSettingItem` / `ClickableSettingItem` /
 *    `ListSettingItem` 三个先例**同一范式**：未注入 ⇒ 显式落回各自**原本就存在**的 Material3
 *    渲染路径（不是画空白）。
 * 2. 其中 `WindowDropdownPreference` 对应的契约方法是本次**新增**的
 *    [MiuixPreferenceRenderer.windowDropdownPreference]：它与 `ListSettingItem` 用的
 *    `OverlaySpinnerPreference` 是**两个不同的 miuix 组件**，既有先例的判据是「迁移后行为与
 *    迁移前**逐字等价**」⇒ 不复用 `overlaySpinnerPreference`（那等于顺手换了渲染）。
 *
 * 行为等价性：三个分支改写后与迁移前的调用**参数逐项一致**（`WindowDropdownPreference` 的
 * `items` 直接收字符串列表、`startAction` 就是那个 `Icon(imageVector, null)`；
 * `SwitchPreference` 的 `modifier = Modifier` 由实现侧补；`ArrowPreference` 的
 * `insideMargin = BasicComponentDefaults.InsideMargin` 由实现侧补）。这三个常量不进契约的理由
 * 与契约 KDoc 里 `modifier` / `insideMargin` 的既有说明一致。
 *
 * ⚠️ 与 `ValueStepper`(M5-15b) 的关系：本文件依赖它，它先一步搬了进来。
 */

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.sliderAccessibility
import top.yukonga.miuix.kmp.basic.BasicComponent
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
    // M5-15c：Miuix 分支改走 `MiuixPreferenceRenderer` 窄契约（原先直接调 miuix 的
    // `WindowDropdownPreference`，而 `miuix-preference` 没有 desktop 变体 ⇒ 本文件进不了
    // `commonMain`）。未注入 ⇒ 走下面那条原本就存在的 Material3 渲染路径；理由见契约 KDoc。
    val miuixRenderer = MiuixPreferenceRendererProvider.current

    if (miuixRenderer != null && ThemeResolver.isMiuixEngine(composeEngine)) {
        val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)

        miuixRenderer.windowDropdownPreference(
            title = title,
            summary = description,
            items = displayEntries.toList(),
            selectedIndex = selectedIndex,
            imageVector = imageVector,
            onSelectedIndexChange = { index ->
                onValueChange(entryValues[index])
            },
        )
    } else {
        val currentEntry =
            displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue

        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            color = color,
            cornerRadius = cornerRadius,
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
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    onValueChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    var displayValue by remember(value) { mutableFloatStateOf(value) }
    val sliderAccessibilityValue = description ?: displayValue.toString()

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
    } else {
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
    // M5-15c：同 `CompactDropdownSettingItem`，Miuix 分支改走窄契约（未注入 ⇒ 落 Material3）。
    val miuixRenderer = MiuixPreferenceRendererProvider.current

    if (miuixRenderer != null && ThemeResolver.isMiuixEngine(composeEngine)) {
        miuixRenderer.switchPreference(
            title = title,
            summary = description,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    } else {
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
                Switch(
                    modifier = Modifier
                        .scale(0.8f)
                        .clearAndSetSemantics { },
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }
        )
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
    // M5-15c：同 `CompactDropdownSettingItem`，Miuix 分支改走窄契约（未注入 ⇒ 落 Material3）。
    val miuixRenderer = MiuixPreferenceRendererProvider.current

    if (miuixRenderer != null && ThemeResolver.isMiuixEngine(composeEngine)) {
        miuixRenderer.arrowPreference(
            title = title,
            summary = description,
            onClick = onClick,
        )
    } else {
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
