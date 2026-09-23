package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.core.designsystem.res.Res
import io.legado.app.core.designsystem.res.a11y_decrease
import io.legado.app.core.designsystem.res.a11y_increase
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.card.TextCard
import org.jetbrains.compose.resources.stringResource

/**
 * M5-15b：从 `:core:ui` 搬进 `:core:designsystem/commonMain`（包名不变 ⇒ 消费者 import 零改动）。
 * 触发条件同 `CardTabRow`(M5-9b-pre) / `TimePickerDialog`(M5-10a-pre)：**共享层出现第一个消费者**
 * —— `CompactSettingItems`（同在本次搬运内）依赖它，而 `CompactSettingItems` 是 `themeManage` 与
 * `themeConfig` 两块共用的前置。
 *
 * ⚠️ **不是零改动搬运**（与 `TimePickerDialog` 同一课：看着纯 Compose，藏着平台写法），两处改写：
 *
 * 1. `androidx.compose.ui.res.stringResource` → `org.jetbrains.compose.resources.stringResource`。
 *    前者是 Android 侧的 `Compose Ui` 实现（读 Android res），`commonMain` 里只能用 CMP 的版本
 *    （读 `composeResources`）。
 * 2. `io.legado.app.core.ui.R` → `:core:designsystem` 自己的 `Res`。语义与行为一致，只是取值来源
 *    从「Android 资源合并」换成「打进 assets 的 composeResources」。
 *    配套：`a11y_decrease` / `a11y_increase` 两条文案**逐字**从 `:core:ui` 的 4 个语言目录搬入
 *    designsystem 的 4 个语言目录（值未改），并从 `:core:ui` 删除（本文件是它们唯一的消费者）。
 */
@Composable
fun ValueStepper(
    value: Float,
    displayValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stepSize: Float = 1f,
    showDecimal: Boolean = false,
    valueFormat: ((Float) -> String)? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SmallOutlinedButton(
            onClick = {
                val newValue = (value - stepSize).coerceIn(valueRange)
                onValueChange(newValue)
            },
            enabled = enabled,
            icon = Icons.Default.Remove,
            contentDescription = stringResource(Res.string.a11y_decrease),
        )
        if (content != null) {
            content()
        } else {
            val displayText = valueFormat?.invoke(displayValue) ?: if (showDecimal) {
                displayValue.toString()
            } else {
                displayValue.toInt().toString()
            }
            TextCard(
                cornerRadius = 8.dp,
                horizontalPadding = 8.dp,
                verticalPadding = 4.dp,
                text = displayText,
                backgroundColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                contentColor = LegadoTheme.colorScheme.onSurface
            )
        }
        SmallOutlinedButton(
            onClick = {
                val newValue = (value + stepSize).coerceIn(valueRange)
                onValueChange(newValue)
            },
            enabled = enabled,
            icon = Icons.Default.Add,
            contentDescription = stringResource(Res.string.a11y_increase),
        )
    }
}
