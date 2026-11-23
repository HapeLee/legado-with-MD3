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
import io.legado.app.ui.widget.components.text.AppText
import kotlin.math.roundToInt

/**
 * 行内档位入口：一颗图标按钮 + 点开的二级面板。
 *
 * 只用于**正文行内**（AI 对话输入栏、改写要求那种横向排布），这类位置没有 Sheet 头部槽位的
 * 约束；面板头部请用 [AiReasoningLevelSection] 直接铺在正文里。
 */
@Composable
fun AiReasoningLevelButton(
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
        AiReasoningLevelControl(level, onLevelChange)
    }
}

/**
 * 面板正文里的思考档位选择。
 *
 * 旧实现住在头部按钮里、点开一个二级面板；头部操作槽只能表达单颗按钮（COUI 下是库按钮），
 * 这类「按钮 + 二级面板」的交互放不进模型，因此整块挪进正文：标题 + 当前档位 + 滑杆，
 * 四套引擎共用同一份控件。
 */
@Composable
fun AiReasoningLevelSection(
    level: AiReasoningLevel,
    enabled: Boolean,
    onLevelChange: (AiReasoningLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppText(
            text = stringResource(R.string.ai_thinking_mode),
            style = LegadoTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AiReasoningLevelControl(level = level, onLevelChange = onLevelChange, enabled = enabled)
    }
}

@Composable
internal fun AiReasoningLevelControl(
    level: AiReasoningLevel,
    onLevelChange: (AiReasoningLevel) -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        TextCard(
            text = level.label(),
            textStyle = LegadoTheme.typography.titleLargeEmphasized,
            backgroundColor = LegadoTheme.colorScheme.onSheetContent
        )
        Spacer(Modifier.height(12.dp))
        AppSlider(
            value = level.ordinal.toFloat(),
            onValueChange = { value ->
                // Pixel-to-step conversion can yield 1.9999999 for the LOW tick.
                AiReasoningLevel.entries[value.roundToInt().coerceIn(
                    0,
                    AiReasoningLevel.entries.lastIndex,
                )].let(onLevelChange)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = 0f..AiReasoningLevel.entries.lastIndex.toFloat(),
            steps = AiReasoningLevel.entries.size - 2,
            accessibilityLabel = stringResource(R.string.ai_thinking_mode),
            accessibilityValue = level.label(),
        )
        Spacer(Modifier.height(12.dp))
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
