package io.legado.app.ui.widget.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.core.designsystem.res.Res
import io.legado.app.core.designsystem.res.action_save
import io.legado.app.core.designsystem.res.color_value
import io.legado.app.core.designsystem.res.reset
import io.legado.app.core.designsystem.res.select_color
import io.legado.app.domain.model.text.isHex
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
// M5-3a-pre：本文件从 `:core:ui/src/main` 上提到 `:core:designsystem/commonMain`
// （`git mv`，**包名不变** ⇒ 调用方 import 零改动）。目的与前面三片资产上提一样：
// 给 `:feature:settings` 的下一个子页（customTheme）让路。
//
// 依赖链很浅——`AppTextField` / `MediumTonalButton` / `AppModalBottomSheet` / `AppText`
// 都已经在 designsystem，Miuix 的 `ColorPicker` / `ColorPalette` / `ColorSpace` 在
// `miuix-ui` 里（**有** desktop 变体）⇒ 不需要 `MiuixPreferenceRenderer` 契约。
//
// 唯一的额外工作与 `SliderSettingItem`(M5-2c) 同形：**它要带资源**。迁移前这 4 条文案
// （`select_color` / `reset` / `action_save` / `color_value`）来自 `:core:ui` 自己的 `R`，
// 已按 composeResources 的做法搬进 `core/designsystem/src/commonMain/composeResources/`
// （4 个语言各一份，值逐字照搬）。

@Composable
fun ColorPickerSheet(
    show: Boolean,
    initialColor: Int,
    onDismissRequest: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    var currentColor by remember { mutableStateOf(Color(initialColor)) }
    var hexInput by remember { mutableStateOf(initialColor.asHexColorString()) }
    var isHexInputError by remember { mutableStateOf(false) }

    LaunchedEffect(show, initialColor) {
        if (show) {
            currentColor = Color(initialColor)
            hexInput = initialColor.asHexColorString()
            isHexInputError = false
        }
    }

    val parsedHexColor = parseHexColor(hexInput)

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.select_color),
        startAction = {
            MediumTonalButton(
                onClick = {
                    currentColor = Color.Transparent
                    hexInput = "#00000000"
                    isHexInputError = false
                },
                icon = Icons.Default.Restore,
                contentDescription = stringResource(Res.string.reset),
            )
        },
        endAction = {
            MediumTonalButton(
                onClick = {
                    onColorSelected(currentColor.toArgb())
                    onDismissRequest()
                },
                enabled = parsedHexColor != null && !isHexInputError,
                icon = Icons.Default.Save,
                contentDescription = stringResource(Res.string.action_save),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ColorPalette(
                color = currentColor,
                onColorChanged = { color ->
                    currentColor = color
                    hexInput = color.toArgb().asHexColorString()
                    isHexInputError = false
                },
                rows = 8,
                hueColumns = 12,
                modifier = Modifier.fillMaxWidth(),
                showPreview = false
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentColor)
                        .border(
                            1.dp,
                            LegadoTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(8.dp)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                AppTextField(
                    value = hexInput,
                    onValueChange = { value ->
                        hexInput = normalizeHexInput(value)
                        val parsedColor = parseHexColor(hexInput)
                        if (parsedColor != null) {
                            currentColor = Color(parsedColor)
                            isHexInputError = false
                        } else {
                            isHexInputError = hexInput.isNotBlank()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.color_value),
                    singleLine = true,
                    isError = isHexInputError,
                    backgroundColor = LegadoTheme.colorScheme.surfaceContainerLow,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    )
                )
            }

        }
    }
}

private fun normalizeHexInput(input: String): String {
    val trimmed = input.trim().uppercase()
    return if (trimmed.startsWith("#")) {
        "#${trimmed.removePrefix("#")}"
    } else {
        trimmed
    }
}

private fun parseHexColor(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    if (hex.length !in setOf(6, 8) || !hex.isHex()) return null
    val argb = if (hex.length == 6) "FF$hex" else hex
    return argb.toLong(16).toInt()
}

private fun Int.asHexColorString(): String =
    "#${Integer.toHexString(this).uppercase().padStart(8, '0')}"
