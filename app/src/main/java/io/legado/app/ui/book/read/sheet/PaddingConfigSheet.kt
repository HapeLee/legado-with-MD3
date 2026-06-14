package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem

@Composable
fun PaddingConfigSheet(
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = {
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismissRequest()
        },
        title = stringResource(R.string.padding),
    ) {
        PaddingConfigContent(
            onIntent = onIntent,
            modifier = Modifier
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
fun PaddingConfigContent(
    onIntent: (ReadBookIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Body padding
    var paddingTop by remember { mutableFloatStateOf(ReadBookConfig.paddingTop.toFloat()) }
    var paddingBottom by remember { mutableFloatStateOf(ReadBookConfig.paddingBottom.toFloat()) }
    var paddingLeft by remember { mutableFloatStateOf(ReadBookConfig.paddingLeft.toFloat()) }
    var paddingRight by remember { mutableFloatStateOf(ReadBookConfig.paddingRight.toFloat()) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        PaddingSliders(
            top = paddingTop, bottom = paddingBottom,
            left = paddingLeft, right = paddingRight,
            onTopChange = {
                paddingTop = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingTop(it.toInt())))
            },
            onBottomChange = {
                paddingBottom = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingBottom(it.toInt())))
            },
            onLeftChange = {
                paddingLeft = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingLeft(it.toInt())))
            },
            onRightChange = {
                paddingRight = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingRight(it.toInt())))
            },
        )
    }
}

@Composable
internal fun PaddingSliders(
    top: Float, bottom: Float, left: Float, right: Float,
    onTopChange: (Float) -> Unit, onBottomChange: (Float) -> Unit,
    onLeftChange: (Float) -> Unit, onRightChange: (Float) -> Unit,
) {
    TinySliderSettingItem(
        title = stringResource(R.string.padding_top),
        value = top,
        valueRange = 0f..300f,
        onValueChange = onTopChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_bottom),
        value = bottom,
        valueRange = 0f..300f,
        onValueChange = onBottomChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_left),
        value = left,
        valueRange = 0f..300f,
        onValueChange = onLeftChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_right),
        value = right,
        valueRange = 0f..300f,
        onValueChange = onRightChange,
    )
}
