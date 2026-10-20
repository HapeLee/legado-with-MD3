package io.legado.app.ui.book.readaloud

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.readaloud.player.ReadAloudVoiceItemUi
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

/**
 * 音色切换：先选引擎，再选该引擎下的音色。
 *
 * 一个引擎（尤其是 HTTP TTS 源）通常带一整套音色，扁平列表里引擎级条目和具体 speaker
 * 混在一起，既分不清层级，也容易让人以为「选了引擎就等于选了某个音色」。
 */
@Composable
fun ReadAloudVoiceSheet(
    show: Boolean,
    voices: ImmutableList<ReadAloudVoiceItemUi>,
    onDismissRequest: () -> Unit,
    onSelectVoice: (String) -> Unit,
) {
    // 每次都回到引擎列表：二级状态属于一次打开期间的临时导航。
    var selectedEngineKey by remember { mutableStateOf<VoiceEngineKey?>(null) }
    // 预测返回手势的进度：和首页模块管理一样，手指拖动时整页轻微缩小、变淡。
    var backProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(show) {
        if (show) {
            selectedEngineKey = null
            backProgress = 0f
        }
    }
    val engineGroups = remember(voices) {
        voices.groupBy { VoiceEngineKey(it.engineType, it.engineId) }
            .map { (key, items) -> VoiceEngineGroup(key, items.first().engineName, items) }
    }
    val selectedGroup = selectedEngineKey?.let { key -> engineGroups.firstOrNull { it.key == key } }
    val onVoiceSelected: (String) -> Unit = { voiceId ->
        onSelectVoice(voiceId)
        onDismissRequest()
    }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = if (selectedGroup == null) {
            stringResource(R.string.cloud_tts_select_engine)
        } else {
            stringResource(R.string.cloud_tts_select_voice)
        },
        startAction = selectedGroup?.let {
            {
                MediumTonalButton(
                    onClick = { selectedEngineKey = null },
                    icon = AppIcons.Back,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
    ) {
        // 二级页接管返回：先回到引擎列表，不关闭整个 sheet。与首页模块管理一致，
        // 手势进度实时驱动缩放/淡出，松手后才真正回退。
        PredictiveBackHandler(enabled = show && selectedGroup != null) { progress ->
            try {
                progress.collect { event -> backProgress = event.progress }
                selectedEngineKey = null
            } finally {
                backProgress = 0f
            }
        }
        if (voices.isEmpty()) {
            EmptyVoicesHint()
        } else {
            // 引擎 → 音色 是页内下钻：进入时新页从右侧推入，返回时反向，和导航栈方向一致。
            AnimatedContent(
                targetState = selectedGroup,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                    }
                },
                // 只有「引擎 → 音色」这一层变化才播过渡；音色内容刷新不重放动画。
                contentKey = { group -> group?.key?.listKey },
                modifier = Modifier.graphicsLayer {
                    val scale = 1f - backProgress * 0.08f
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - backProgress * 0.1f
                },
                label = "ReadAloudVoiceSheetPage",
            ) { group ->
                if (group == null) {
                    EngineList(
                        groups = engineGroups,
                        onSelectEngine = { key -> selectedEngineKey = key },
                    )
                } else {
                    VoiceList(
                        voices = group.voices,
                        onSelectVoice = onVoiceSelected,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EmptyVoicesHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = stringResource(R.string.cloud_tts_no_saved_voices),
            style = LegadoTheme.typography.bodyMedium,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EngineList(
    groups: List<VoiceEngineGroup>,
    onSelectEngine: (VoiceEngineKey) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(groups, key = { it.key.listKey }) { group ->
            val selectedVoice = group.selectedVoice
            TinyClickableSettingItem(
                title = group.name,
                description = when {
                    selectedVoice == null -> null
                    // 引擎级条目的名字就是引擎名，再显示一遍只会重复标题。
                    selectedVoice.isEngineDefault ->
                        stringResource(R.string.cloud_tts_default_voice)

                    else -> selectedVoice.name
                },
                imageVector = Icons.Default.RecordVoiceOver,
                color = if (selectedVoice != null) {
                    LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    LegadoTheme.colorScheme.surfaceContainerLow
                },
                onClick = { onSelectEngine(group.key) },
            )
        }
    }
}

@Composable
private fun VoiceList(
    voices: List<ReadAloudVoiceItemUi>,
    onSelectVoice: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(voices, key = { it.id }) { voice ->
            TinyClickableSettingItem(
                title = voice.name,
                description = if (voice.isEngineDefault) {
                    stringResource(R.string.cloud_tts_default_voice)
                } else {
                    null
                },
                imageVector = Icons.Default.RecordVoiceOver,
                color = if (voice.isSelected) {
                    LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    LegadoTheme.colorScheme.surfaceContainerLow
                },
                trailingContent = {
                    if (voice.isSelected) {
                        AppIcon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.cloud_tts_selected),
                            tint = LegadoTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                onClick = { onSelectVoice(voice.id) },
            )
        }
    }
}

private data class VoiceEngineKey(
    val engineType: String,
    val engineId: String,
) {
    /** LazyColumn 的 key 需要稳定且可比较，直接用字符串避免依赖 data class 的 equals。 */
    val listKey: String = "$engineType\u0000$engineId"
}

private data class VoiceEngineGroup(
    val key: VoiceEngineKey,
    val name: String,
    val voices: List<ReadAloudVoiceItemUi>,
) {
    val selectedVoice: ReadAloudVoiceItemUi? get() = voices.firstOrNull { it.isSelected }
}
