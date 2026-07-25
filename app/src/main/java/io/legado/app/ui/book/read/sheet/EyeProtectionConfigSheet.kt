package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.dialog.TimePickerDialog
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem

/**
 * 护眼模式设置。阅读菜单与阅读设置共用这一份，字段全部落在 ThemeSettings 上，
 * 与外观设置里的护眼分组是同一套值。
 */
@Composable
fun EyeProtectionConfigSheet(
    show: Boolean,
    enabled: Boolean,
    intensity: Int,
    autoNight: Boolean,
    schedule: Boolean,
    startTime: String,
    endTime: String,
    onDismissRequest: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (Int) -> Unit,
    onAutoNightChange: (Boolean) -> Unit,
    onScheduleChange: (Boolean) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
) {
    var editingStartTime by remember { mutableStateOf(false) }
    var editingEndTime by remember { mutableStateOf(false) }
    val configured = enabled || autoNight

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.eye_protection),
        animateContentSize = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            SwitchSettingItem(
                title = stringResource(R.string.eye_protection_enabled),
                description = stringResource(R.string.eye_protection_enabled_summary),
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
            SwitchSettingItem(
                title = stringResource(R.string.eye_protection_auto_night),
                description = stringResource(R.string.eye_protection_auto_night_summary),
                checked = autoNight,
                onCheckedChange = onAutoNightChange,
            )
            AnimatedVisibility(visible = configured) {
                Column {
                    SliderSettingItem(
                        title = stringResource(R.string.eye_protection_intensity),
                        description = stringResource(
                            R.string.eye_protection_intensity_summary,
                            intensity
                        ),
                        value = intensity.toFloat(),
                        defaultValue = 50f,
                        valueRange = 0f..100f,
                        onValueChange = { onIntensityChange(it.toInt()) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.eye_protection_schedule),
                        description = stringResource(R.string.eye_protection_schedule_summary),
                        checked = schedule,
                        onCheckedChange = onScheduleChange,
                    )
                    AnimatedVisibility(visible = schedule) {
                        Column {
                            ClickableSettingItem(
                                title = stringResource(R.string.eye_protection_start_time),
                                option = startTime,
                                onClick = { editingStartTime = true },
                            )
                            ClickableSettingItem(
                                title = stringResource(R.string.eye_protection_end_time),
                                option = endTime,
                                onClick = { editingEndTime = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingStartTime) {
        TimePickerDialog(
            title = stringResource(R.string.eye_protection_start_time),
            currentValue = startTime,
            onDismissRequest = { editingStartTime = false },
            onConfirm = {
                onStartTimeChange(it)
                editingStartTime = false
            },
        )
    }
    if (editingEndTime) {
        TimePickerDialog(
            title = stringResource(R.string.eye_protection_end_time),
            currentValue = endTime,
            onDismissRequest = { editingEndTime = false },
            onConfirm = {
                onEndTimeChange(it)
                editingEndTime = false
            },
        )
    }
}
