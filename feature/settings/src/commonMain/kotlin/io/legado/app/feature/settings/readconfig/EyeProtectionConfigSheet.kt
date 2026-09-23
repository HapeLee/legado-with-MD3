package io.legado.app.feature.settings.readconfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.eye_protection
import io.legado.app.feature.settings.res.eye_protection_auto_night
import io.legado.app.feature.settings.res.eye_protection_auto_night_summary
import io.legado.app.feature.settings.res.eye_protection_enabled
import io.legado.app.feature.settings.res.eye_protection_enabled_summary
import io.legado.app.feature.settings.res.eye_protection_end_time
import io.legado.app.feature.settings.res.eye_protection_intensity
import io.legado.app.feature.settings.res.eye_protection_intensity_summary
import io.legado.app.feature.settings.res.eye_protection_schedule
import io.legado.app.feature.settings.res.eye_protection_schedule_summary
import io.legado.app.feature.settings.res.eye_protection_start_time
import io.legado.app.ui.widget.components.dialog.TimePickerDialog
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import org.jetbrains.compose.resources.stringResource

/**
 * M5-10a：从 `:app` 的 `io.legado.app.ui.book.read.sheet` 迁来，为 `readConfig` 页的迁移
 * 铺路（页面本体要用它）。**唯一改动**是资源访问：`androidx.compose.ui.res.stringResource`
 * → CMP 的、`R.string.*` → `Res.string.*`（11 条，无数组）。结构逐字保留。
 *
 * 落位说明：它原住在 `ui/book/read/sheet/`（阅读菜单那一侧），但本页与阅读菜单**共用同一份**，
 * 而它的全部字段都落在 `ThemeSettings` 上 ⇒ 既不是阅读器私有、也不是设置页私有。
 * 本片放 `:feature:settings/readconfig/`（与将迁来的 `ReadConfigScreen` 同包），
 * 阅读器那侧（`:app` 的 `ReadBookScreen`）改为 import 本文件。
 *
 * ⚠️ 这是**临时归属**：将来若有 `:feature:reader` 模块，这两个共用 sheet 应重新划分归属
 * （护眼本质上属于「外观」，阅读菜单只是入口之一）。当前先按「谁先迁移谁持有」放这里。
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
        title = stringResource(Res.string.eye_protection),
        animateContentSize = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            SwitchSettingItem(
                title = stringResource(Res.string.eye_protection_enabled),
                description = stringResource(Res.string.eye_protection_enabled_summary),
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
            SwitchSettingItem(
                title = stringResource(Res.string.eye_protection_auto_night),
                description = stringResource(Res.string.eye_protection_auto_night_summary),
                checked = autoNight,
                onCheckedChange = onAutoNightChange,
            )
            AnimatedVisibility(visible = configured) {
                Column {
                    SliderSettingItem(
                        title = stringResource(Res.string.eye_protection_intensity),
                        description = stringResource(
                            Res.string.eye_protection_intensity_summary,
                            intensity
                        ),
                        value = intensity.toFloat(),
                        defaultValue = 50f,
                        valueRange = 0f..100f,
                        onValueChange = { onIntensityChange(it.toInt()) },
                    )
                    SwitchSettingItem(
                        title = stringResource(Res.string.eye_protection_schedule),
                        description = stringResource(Res.string.eye_protection_schedule_summary),
                        checked = schedule,
                        onCheckedChange = onScheduleChange,
                    )
                    AnimatedVisibility(visible = schedule) {
                        Column {
                            ClickableSettingItem(
                                title = stringResource(Res.string.eye_protection_start_time),
                                option = startTime,
                                onClick = { editingStartTime = true },
                            )
                            ClickableSettingItem(
                                title = stringResource(Res.string.eye_protection_end_time),
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
            title = stringResource(Res.string.eye_protection_start_time),
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
            title = stringResource(Res.string.eye_protection_end_time),
            currentValue = endTime,
            onDismissRequest = { editingEndTime = false },
            onConfirm = {
                onEndTimeChange(it)
                editingEndTime = false
            },
        )
    }
}
