package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.utils.postEvent

@Composable
fun ShadowSetSheet(
    onDismissRequest: () -> Unit,
) {
    var textShadow by remember { mutableStateOf(ReadBookConfig.textShadow) }
    var shadowColor by remember { mutableIntStateOf(ReadBookConfig.durConfig.curTextShadowColor()) }
    var shadowRadius by remember { mutableFloatStateOf(ReadBookConfig.shadowRadius) }
    var shadowDx by remember { mutableFloatStateOf(ReadBookConfig.shadowDx) }
    var shadowDy by remember { mutableFloatStateOf(ReadBookConfig.shadowDy) }
    var showColorPicker by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.text_shadow_set),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TinySwitchSettingItem(
                title = stringResource(R.string.text_shadow_set),
                checked = textShadow,
                onCheckedChange = {
                    textShadow = it
                    ReadBookConfig.textShadow = it
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
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
                    ReadBookConfig.shadowRadius = value
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.text_shadow_x),
                value = shadowDx,
                valueRange = -50f..50f,
                onValueChange = { value ->
                    shadowDx = value
                    ReadBookConfig.shadowDx = value
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                },
            )
            TinySliderSettingItem(
                title = stringResource(R.string.text_shadow_y),
                value = shadowDy,
                valueRange = -50f..50f,
                onValueChange = { value ->
                    shadowDy = value
                    ReadBookConfig.shadowDy = value
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                },
            )
        }
    }

    if (showColorPicker) {
        ColorPickerSheet(
            show = true,
            initialColor = shadowColor,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                shadowColor = color
                ReadBookConfig.durConfig.setCurShadColor(color)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5, 9))
                showColorPicker = false
            },
        )
    }
}
