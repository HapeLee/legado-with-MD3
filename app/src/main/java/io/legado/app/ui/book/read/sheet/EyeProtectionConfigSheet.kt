package io.legado.app.ui.book.read.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem

@Composable
fun EyeProtectionConfigSheet(
    show: Boolean,
    enabled: Boolean,
    intensity: Int,
    autoNight: Boolean,
    onDismissRequest: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (Int) -> Unit,
    onAutoNightChange: (Boolean) -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.eye_protection),
    ) {
        SwitchSettingItem(
            title = stringResource(R.string.eye_protection_enabled),
            description = stringResource(R.string.eye_protection_enabled_summary),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
        SliderSettingItem(
            title = stringResource(R.string.eye_protection_intensity),
            description = stringResource(R.string.eye_protection_intensity_summary, intensity),
            value = intensity.toFloat(),
            defaultValue = 50f,
            valueRange = 0f..100f,
            onValueChange = { onIntensityChange(it.toInt()) },
        )
        SwitchSettingItem(
            title = stringResource(R.string.eye_protection_auto_night),
            description = stringResource(R.string.eye_protection_auto_night_summary),
            checked = autoNight,
            onCheckedChange = onAutoNightChange,
        )
    }
}
