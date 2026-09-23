package io.legado.app.ui.widget.components.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.legado.app.core.designsystem.res.Res
import io.legado.app.core.designsystem.res.cancel
import io.legado.app.core.designsystem.res.ok
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import org.jetbrains.compose.resources.stringResource

/**
 * M5-10a-pre：从 `:core:ui` 搬进 `:core:designsystem/commonMain`（包名不变 ⇒ 两处消费方
 * ——`ThemeConfigScreen`（`:app`）与 `:feature:settings` 的 `EyeProtectionConfigSheet`
 * ——import 零改动）。与 M5-9b-pre 搬 `CardTabRow` 是同一配方：**共享层出现第一个消费者时再搬**。
 *
 * 与 `CardTabRow` 不同，本文件**不是零改动搬运** —— 它有三处 JVM/Android 专用写法，搬进
 * `commonMain` 必须改写。三处都**等价改写**（语义不变），且下面第一条有既存单测兜底：
 *
 * 1. `String.format(Locale.ROOT, "%02d:%02d", h, m)` → `padStart(2, '0')` 拼接。
 *    原写法用 `Locale.ROOT` 正是为了**恒定输出 ASCII 数字**（否则阿拉伯语等地区会输出
 *    `٢٢:٠٧`）。Kotlin 的 `Int.toString()` 本就恒为 ASCII ⇒ 语义一致。
 *    ⚠️ 原 `:core:ui` 有一份 `TimePickerDialogTest` **专门钉这条**（它在 `ar` 地区断言
 *    `formatTimeValue(22, 7) == "22:07"`）。该测试已随之迁到本模块的 `commonTest`
 *    （去掉 `java.util.Locale`；新实现按构造就是 locale-free 的，那条断言因此恒真，
 *    但「输出形状」仍被断言）。
 * 2. `Character.digit(char, 10)` → `Char.digitToIntOrNull(10)`。原实现**刻意**接受
 *    非拉丁数字（原测试断言 `parseTimeNumber("٢٢") == 22`）。JVM 上
 *    `digitToIntOrNull` 委托给 `Character.digit`，语义相同 ✓；其它平台（native/JS）的
 *    数字表可能只认 ASCII —— 见迁移说明里的「未验证」。
 * 3. `stringResource(R.string.ok / R.string.cancel)` → designsystem 自己的 `Res`
 *    （该模块四语言都有 `ok` / `cancel`，无需新增文案）。
 *
 * 24 小时制，`currentValue` 与回调值均为 `HH:mm`，解析失败时回落到 00:00。
 */
internal fun formatTimeValue(hour: Int, minute: Int): String =
    hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

internal fun parseTimeNumber(value: String): Int? {
    if (value.isEmpty()) return null
    val normalized = buildString(value.length) {
        value.forEach { char ->
            val digit = char.digitToIntOrNull(10)
            if (digit == null) return null
            append(digit)
        }
    }
    return normalized.toIntOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    currentValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = parseTimeNumber(currentValue.substringBefore(':'))
            ?.coerceIn(0, 23) ?: 0,
        initialMinute = parseTimeNumber(currentValue.substringAfter(':', ""))
            ?.coerceIn(0, 59) ?: 0,
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
        confirmText = stringResource(Res.string.ok),
        onConfirm = {
            onConfirm(formatTimeValue(timePickerState.hour, timePickerState.minute))
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = onDismissRequest,
    )
}
