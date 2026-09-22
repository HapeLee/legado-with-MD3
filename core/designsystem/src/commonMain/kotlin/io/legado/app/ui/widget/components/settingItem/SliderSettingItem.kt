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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.legado.app.core.designsystem.res.Res
import io.legado.app.core.designsystem.res.edit
import io.legado.app.core.designsystem.res.input_value_range
import io.legado.app.core.designsystem.res.slider
import io.legado.app.core.designsystem.res.text_default
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.sliderAccessibility
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import org.jetbrains.compose.resources.stringResource

// M5-2c：本文件从 `:core:ui/src/main` 上提到 `:core:designsystem/commonMain`
// （`git mv`，**包名不变** ⇒ 调用方 import 零改动）。它是 `:feature:settings` 下一个子页
// （translation）的最后一个前置资产——`ClickableSettingItem`(M5-2a-pre)、
// `DropdownListSettingItem`(M5-2b) 已分别上提。
//
// 与前面两个资产的关键差异：**它要带资源一起走**。迁移前这 4 条文案
// （`slider` / `edit` / `text_default` / `input_value_range`）来自 `:core:ui` 自己的
// `R`（`io.legado.app.core.ui.R`），而 Android 的 `R` 在 CMP 模块里不存在 ⇒ 按
// composeResources 的既有做法搬进 `core/designsystem/src/commonMain/composeResources/`
// （4 个语言各一份，**值逐字照搬**，含 `input_value_range` 的两个占位符 `%1$d-%2$d`）。
//
// ⚠️ `:core:ui` 的那份副本**不删**：`InputSettingItem.kt` 仍在用 `edit` 与 `text_default`
// （本仓规则是只移除「本次改动产生的」无用资源）。
//
// 另外两个依赖已经随前面的片上提到 designsystem：`sliderAccessibility`（在
// `AppSlider.kt`，M5-2b）与 `ConfirmDismissButtonsRow`。Miuix 分支用的
// `BasicComponent` / `Slider` / `TextField` 都在 `miuix-ui` 里（**有 desktop 变体**），
// 所以本文件**不需要** `MiuixPreferenceRenderer` 契约——它与 `ClickableSettingItem` /
// `DropdownListSettingItem` 那两个撞 `miuix-preference` 的情况不同。

@Composable
fun SliderSettingItem(
    title: String,
    color: Color? = null,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    valueLabel: ((Float) -> String)? = null,
    decimal: Boolean = false,
    onValueChange: (Float) -> Unit
) {

    var expanded by remember { mutableStateOf(false) }
    var isInputMode by remember { mutableStateOf(false) }
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val textFieldState = rememberTextFieldState()

    // 默认整数模式：滑动吸附到整数；decimal = true 时保留 0.1 精度
    fun snap(v: Float): Float =
        if (decimal) (v * 10).roundToInt() / 10f else v.roundToInt().toFloat()

    fun format(v: Float): String =
        if (v % 1f == 0f) v.toInt().toString() else v.toString()

    LaunchedEffect(value) {
        sliderValue = value
    }

    // 拖动过程中让标题下的数值实时跟随滑块，松手后才真正应用
    val displayDescription = when {
        valueLabel != null -> valueLabel(sliderValue)
        sliderValue != value -> format(sliderValue)
        else -> description
    }

    val sliderAccessibilityValue = displayDescription ?: sliderValue.toString()

    LaunchedEffect(isInputMode) {
        if (isInputMode) {
            textFieldState.edit {
                replace(0, length, format(value))
            }
        }
    }

    fun commitValue() {
        if (isInputMode) {
            textFieldState.text.toString().toFloatOrNull()?.let { num ->
                onValueChange(snap(num).coerceIn(valueRange))
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
                summary = displayDescription,
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
                                    Res.string.input_value_range,
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
                                    sliderValue = snap(it)
                                },
                                onValueChangeFinished = {
                                    onValueChange(sliderValue.coerceIn(valueRange))
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

                    ConfirmDismissButtonsRow(
                        modifier = Modifier.padding(top = 16.dp),
                        onDismiss = { isInputMode = !isInputMode },
                        onConfirm = {
                            onValueChange(defaultValue)
                            textFieldState.edit {
                                replace(0, length, format(defaultValue))
                            }
                        },
                        dismissText = if (isInputMode) {
                            stringResource(Res.string.slider)
                        } else {
                            stringResource(Res.string.edit)
                        },
                        confirmText = stringResource(Res.string.text_default)
                    )
                }
            }
        }

    } else {
        SettingItem(
            title = title,
            option = displayDescription,
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
                                            Res.string.input_value_range,
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
                                    sliderValue = snap(it)
                                },
                                onValueChangeFinished = {
                                    onValueChange(sliderValue.coerceIn(valueRange))
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

                ConfirmDismissButtonsRow(
                    modifier = Modifier.padding(top = 16.dp),
                    onDismiss = { isInputMode = !isInputMode },
                    onConfirm = {
                        onValueChange(defaultValue)
                        textFieldState.edit {
                            replace(0, length, format(defaultValue))
                        }
                    },
                    dismissText = if (isInputMode) {
                        stringResource(Res.string.slider)
                    } else {
                        stringResource(Res.string.edit)
                    },
                    confirmText = stringResource(Res.string.text_default)
                )
            }
        )
    }
}
