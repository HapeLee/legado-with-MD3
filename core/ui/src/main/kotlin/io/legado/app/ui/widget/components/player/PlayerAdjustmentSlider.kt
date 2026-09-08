package io.legado.app.ui.widget.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.text.AppText

/**
 * 播放器通用的调节滑块卡片（倍速/定时/增益等）：标题 + 数值标签 + 滑块 + 两端说明。
 */
@Composable
fun PlayerAdjustmentSlider(
    title: String,
    value: Float,
    valueLabel: String,
    startLabel: String,
    endLabel: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
) {
    NormalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        cornerRadius = 24.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    modifier = Modifier.weight(1f),
                    text = title,
                    style = LegadoTheme.typography.labelMediumEmphasized,
                )
                TextCard(
                    text = valueLabel,
                )
            }

            AppSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = startLabel,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                AppText(
                    text = endLabel,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
