package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem

@Composable
fun UnderlineConfigSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var underline by remember { mutableStateOf(ReadBookConfig.underline) }
    var dottedLine by remember { mutableStateOf(ReadBookConfig.dottedLine) }
    var underlineExtend by remember { mutableStateOf(ReadBookConfig.underlineExtend) }
    var underlineColor by remember { mutableStateOf(ReadBookConfig.durConfig.curUnderlineColor()) }
    var underlineHeight by remember { mutableFloatStateOf(ReadBookConfig.underlineHeight.toFloat()) }
    var underlinePadding by remember { mutableFloatStateOf(ReadBookConfig.underlinePadding.toFloat()) }
    var dottedBase by remember { mutableFloatStateOf(ReadBookConfig.durConfig.dottedBase) }
    var dottedRatio by remember { mutableFloatStateOf(ReadBookConfig.durConfig.dottedRatio) }
    var showColorPicker by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.text_underline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            TinySwitchSettingItem(
                title = stringResource(R.string.text_underline),
                checked = underline,
                onCheckedChange = {
                    underline = it
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.Underline(it)))
                    if (!it) {
                        dottedLine = false
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.DottedLine(false)))
                    }
                },
            )
            TinyColorSettingItem(
                title = stringResource(R.string.underline_color),
                colorValue = underlineColor,
                onClick = { showColorPicker = true },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.text_dottedline),
                checked = dottedLine,
                enabled = underline,
                onCheckedChange = {
                    dottedLine = it
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.DottedLine(it)))
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.underline_extend),
                checked = underlineExtend,
                onCheckedChange = {
                    underlineExtend = it
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.UnderlineExtend(it)))
                },
            )

            // Underline height & padding
            TinySliderSettingItem(
                title = stringResource(R.string.underline_height),
                value = underlineHeight,
                valueRange = 0f..20f,
                onValueChange = { value ->
                    underlineHeight = value
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.UnderlineHeight(value.toInt())))
                },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.underline_padding),
                value = underlinePadding,
                valueRange = 0f..20f,
                onValueChange = { value ->
                    underlinePadding = value
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.UnderlinePadding(value.toInt())))
                },
            )

            Spacer(Modifier.height(8.dp))

            // Dotted line section title
            Text(
                text = stringResource(R.string.text_dottedline),
                style = MaterialTheme.typography.titleSmallEmphasized,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // Dotted line sliders
            TinySliderSettingItem(
                title = stringResource(R.string.dotted_line_black),
                value = dottedBase,
                valueRange = 0f..20f,
                onValueChange = { value ->
                    dottedBase = value
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.DottedBase(value)))
                },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.dotted_line_while),
                value = dottedRatio,
                valueRange = 0f..20f,
                onValueChange = { value ->
                    dottedRatio = value
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.DottedRatio(value)))
                },
            )
        }
    }

    // Color picker
    ColorPickerSheet(
        show = showColorPicker,
        initialColor = underlineColor,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            underlineColor = color
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.UnderlineColor(color)))
            showColorPicker = false
        },
    )
}
