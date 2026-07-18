package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ReadSheetConfigUiState
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem

@Composable
fun ShadowSetSheet(
    show: Boolean,
    config: ReadSheetConfigUiState,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var textShadow by remember(show, config.textShadow) { mutableStateOf(config.textShadow) }
    var shadowColor by remember(show, config.textShadowColor) { mutableIntStateOf(config.textShadowColor) }
    var shadowRadius by remember(show, config.shadowRadius) { mutableFloatStateOf(config.shadowRadius) }
    var shadowDx by remember(show, config.shadowDx) { mutableFloatStateOf(config.shadowDx) }
    var shadowDy by remember(show, config.shadowDy) { mutableFloatStateOf(config.shadowDy) }
    var showColorPicker by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.text_shadow_set),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            TinySwitchSettingItem(
                title = stringResource(R.string.text_shadow_set),
                checked = textShadow,
                onCheckedChange = {
                    textShadow = it
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextShadow(it)))
                },
            )
            TinyColorSettingItem(
                title = stringResource(R.string.text_shadow_color),
                colorValue = shadowColor,
                onClick = { showColorPicker = true },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.text_shadow_radius),
                value = shadowRadius,
                valueRange = 0f..100f,
                onValueChange = { value ->
                    shadowRadius = value
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShadowRadius(value)))
                },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.text_shadow_x),
                value = shadowDx,
                valueRange = -50f..50f,
                onValueChange = { value ->
                    shadowDx = value
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShadowDx(value)))
                },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.text_shadow_y),
                value = shadowDy,
                valueRange = -50f..50f,
                onValueChange = { value ->
                    shadowDy = value
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShadowDy(value)))
                },
            )
        }
    }

    ColorPickerSheet(
        show = showColorPicker,
        initialColor = shadowColor,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            shadowColor = color
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShadowColor(color)))
            showColorPicker = false
        },
    )
}
