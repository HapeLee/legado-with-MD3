package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

private const val CHINESE_CONVERT_ID = -1L
private const val RE_SEGMENT_ID = -2L

@Composable
fun EffectiveReplacesSheet(
    show: Boolean,
    effectiveRules: List<ReplaceRule>,
    chineseConvertActive: Boolean,
    reSegmentActive: Boolean,
    onDismissRequest: () -> Unit,
    onOpenReplaceEditor: (id: Long, pattern: String?) -> Unit,
    onReplaceRuleChanged: () -> Unit,
    onNavigateToTextEffects: () -> Unit,
    onOpenContentProcesses: () -> Unit,
    onDisableRule: (ReplaceRule) -> Unit,
    onDisableChineseConverter: () -> Unit,
    onDisableReSegment: () -> Unit,
) {
    val chineseConvertItem = remember { ReplaceRule(CHINESE_CONVERT_ID, "繁简转换") }
    val reSegmentItem = remember { ReplaceRule(RE_SEGMENT_ID, "") }

    val items = remember(show, effectiveRules, chineseConvertActive, reSegmentActive) {
        buildList {
            addAll(effectiveRules)
            if (chineseConvertActive) add(chineseConvertItem)
            if (reSegmentActive) add(reSegmentItem)
        }
    }

    var isEdited by remember(show) { mutableStateOf(false) }
    var disabledIds by remember(show) { mutableStateOf(emptySet<Long>()) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            if (isEdited) onReplaceRuleChanged()
            onDismissRequest()
        },
        title = stringResource(R.string.effective_replaces),
    ) {
        MediumTonalButton(
            onClick = onOpenContentProcesses,
            icon = Icons.Default.Edit,
            text = stringResource(R.string.content_processes),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { it.id }) { rule ->
                if (rule.id !in disabledIds) {
                    NormalCard(
                        onClick = {
                            if (rule.id == CHINESE_CONVERT_ID) {
                                onNavigateToTextEffects()
                            } else if (rule.id != RE_SEGMENT_ID) {
                                onOpenReplaceEditor(rule.id, rule.pattern)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        containerColor = LegadoTheme.colorScheme.onSheetContent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = if (rule.id == RE_SEGMENT_ID) {
                                        stringResource(R.string.re_segment)
                                    } else {
                                        rule.name
                                    },
                                    style = LegadoTheme.typography.labelLargeEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (rule.id >= 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AppText(
                                            text = rule.pattern,
                                            style = LegadoTheme.typography.bodySmall,
                                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        AppIcon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = LegadoTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        AppText(
                                            text = rule.replacement.ifEmpty { "∅" },
                                            style = LegadoTheme.typography.bodySmall,
                                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            MediumTonalButton(
                                onClick = {
                                    disabledIds = disabledIds + rule.id
                                    isEdited = true
                                    when (rule.id) {
                                        RE_SEGMENT_ID -> {
                                            onDisableReSegment()
                                        }

                                        CHINESE_CONVERT_ID -> onDisableChineseConverter()
                                        else -> onDisableRule(rule)
                                    }
                                },
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.close)
                            )
                        }
                    }
                }
            }
        }
    }
}
