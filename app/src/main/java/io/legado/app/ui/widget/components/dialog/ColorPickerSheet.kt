package io.legado.app.ui.widget.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.isHex
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    show: Boolean,
    initialColor: Int,
    onDismissRequest: () -> Unit,
    onColorSelected: (Int) -> Unit
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
        title = "选择颜色",
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
                    label = "色值",
                    singleLine = true,
                    isError = isHexInputError,
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ConfirmDismissButtonsRow(
                modifier = Modifier.fillMaxWidth(),
                onDismiss = onDismissRequest,
                onConfirm = {
                    onColorSelected(currentColor.toArgb())
                    onDismissRequest()
                },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok),
                confirmEnabled = parsedHexColor != null && !isHexInputError
            )
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
