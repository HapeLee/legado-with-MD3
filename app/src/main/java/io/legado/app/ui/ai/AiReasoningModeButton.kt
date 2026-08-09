package io.legado.app.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun AiReasoningModeButton(
    level: AiReasoningLevel,
    enabled: Boolean,
    onLevelChange: (AiReasoningLevel) -> Unit,
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    MediumTonalButton(
        onClick = { showSheet = true },
        icon = Icons.Default.Lightbulb,
        enabled = enabled,
        selected = level != AiReasoningLevel.OFF,
        contentDescription = stringResource(R.string.ai_thinking_mode),
    )
    AppModalBottomSheet(
        show = showSheet,
        onDismissRequest = { showSheet = false },
        title = stringResource(R.string.ai_thinking_mode)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            TextCard(
                text = level.label(),
                textStyle = LegadoTheme.typography.titleLargeEmphasized,
                backgroundColor = LegadoTheme.colorScheme.onSheetContent
            )
            Spacer(Modifier.height(32.dp))
            AppSlider(
                value = level.ordinal.toFloat(),
                onValueChange = { value ->
                    AiReasoningLevel.entries[value.toInt().coerceIn(
                        0,
                        AiReasoningLevel.entries.lastIndex,
                    )].let(onLevelChange)
                },
                modifier = Modifier.fillMaxWidth(),
                valueRange = 0f..AiReasoningLevel.entries.lastIndex.toFloat(),
                steps = AiReasoningLevel.entries.size - 2,
                accessibilityLabel = stringResource(R.string.ai_thinking_mode),
                accessibilityValue = level.label(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AiReasoningLevel.label(): String = when (this) {
    AiReasoningLevel.OFF -> stringResource(R.string.ai_thinking_off)
    AiReasoningLevel.AUTO -> stringResource(R.string.ai_thinking_auto)
    AiReasoningLevel.LOW -> stringResource(R.string.ai_reasoning_level_low)
    AiReasoningLevel.MEDIUM -> stringResource(R.string.ai_reasoning_level_medium)
    AiReasoningLevel.HIGH -> stringResource(R.string.ai_reasoning_level_high)
    AiReasoningLevel.XHIGH -> stringResource(R.string.ai_reasoning_level_xhigh)
    AiReasoningLevel.MAX -> stringResource(R.string.ai_reasoning_level_max)
}
