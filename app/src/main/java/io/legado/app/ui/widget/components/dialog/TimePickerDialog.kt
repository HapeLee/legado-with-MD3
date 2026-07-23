package io.legado.app.ui.widget.components.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.alert.AppAlertDialog

/**
 * 24 小时制时间选择对话框。[currentValue] 与回调值均为 `HH:mm`，
 * 解析失败时回落到 00:00。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    currentValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = currentValue.substringBefore(':').toIntOrNull()?.coerceIn(0, 23) ?: 0,
        initialMinute = currentValue.substringAfter(':', "").toIntOrNull()?.coerceIn(0, 59) ?: 0,
        is24Hour = true,
    )
    AppAlertDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        title = title,
        content = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            onConfirm("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
    )
}
