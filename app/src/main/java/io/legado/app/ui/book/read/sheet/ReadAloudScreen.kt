package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.ReadAloudTimerMode
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem

/**
 * 经典朗读控制面板的内容。
 *
 * 它自身不是弹层：宿主提供层级与动画。阅读界面用 `AppModalBottomSheet` 承载
 * （保留自下而上的进出动画与下拉关闭手势），因此不会再被塞进阅读菜单的一页里。
 */
@Composable
fun ReadAloudContent(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
    onOpenChapterList: () -> Unit,
    onGoToBackground: () -> Unit,
    onOpenMainMenu: () -> Unit,
    onShowReadAloudConfig: () -> Unit,
    onShowTimerSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ttsSpeechRate = state.readAloudTtsSpeechRate
    var speechRatePreview by remember(ttsSpeechRate) { mutableFloatStateOf(ttsSpeechRate.toFloat()) }
    // 总音量同样只在松手时提交：拖动中每像素写 prefs 并回灌会让滑块来回跳
    var bgmVolumePreview by remember(state.bgmVolume) {
        mutableFloatStateOf(state.bgmVolume * 100f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Media controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudPrevParagraph) },
                icon = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.prev_sentence),
            )
            Spacer(Modifier.width(6.dp))
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudTogglePause) },
                icon = if (state.isReadAloudPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(
                    if (state.isReadAloudPaused) R.string.audio_play else R.string.pause
                ),
            )
            Spacer(Modifier.width(6.dp))
            MediumTonalButton(
                onClick = {
                    onIntent(ReadBookIntent.ReadAloudStop)
                    onDismissRequest()
                },
                icon = Icons.Default.Stop,
                contentDescription = stringResource(R.string.stop),
            )
            Spacer(Modifier.width(6.dp))
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudNextParagraph) },
                icon = Icons.Default.SkipNext,
                text = stringResource(R.string.next_sentence),
            )
        }

        Spacer(Modifier.height(12.dp))

        TinyClickableSettingItem(
            title = stringResource(R.string.set_timer),
            description = readAloudTimerSummary(state),
            onClick = onShowTimerSettings,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudPrevChapter) },
                text = stringResource(R.string.previous_chapter),
                modifier = Modifier.weight(1f),
            )
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudNextChapter) },
                text = stringResource(R.string.next_chapter),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // 多角色两开关并排成一行（selected 高亮 = 开），下面再一行两个入口按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.SetUseMultiSpeaker(!state.useMultiSpeaker)) },
                selected = state.useMultiSpeaker,
                icon = Icons.Default.RecordVoiceOver,
                text = stringResource(R.string.use_multi_speaker),
                modifier = Modifier.weight(1f),
            )
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.SetMultiRoleCast(!state.multiRoleCast)) },
                selected = state.multiRoleCast,
                icon = Icons.Default.Face,
                text = stringResource(R.string.multi_role_cast),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        // 背景音乐：开关 + 分配表同一行（都是「这一章的配乐怎么安排」的入口），
        // 总音量单独一行滑块；本章配乐一览与 AI 识别场景再一行。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.SetBgmAssign(!state.bgmAssign)) },
                selected = state.bgmAssign,
                icon = Icons.Default.MusicNote,
                text = stringResource(R.string.bgm_assign),
                modifier = Modifier.weight(1f),
            )
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.OpenBookVoiceCasting) },
                icon = Icons.Default.TableRows,
                text = stringResource(R.string.book_voice_casting),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 本章配乐区间一览：不用一段一段点胶囊才能看全
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.BgmSceneTable)) },
                icon = Icons.Default.TableRows,
                text = stringResource(R.string.cast_bgm_scene_table),
                modifier = Modifier.weight(1f),
            )
            // 场景识别只依赖「背景音乐分配」这个副开关，与开不开多角色朗读无关
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.OpenAiSceneDialog) },
                icon = Icons.Default.AutoAwesome,
                text = stringResource(R.string.ai_scene_assign_entry),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // 总音量：朗读设置里的这一根是「背景音乐相对人声整体多响」，
        // 与配乐库每条曲子的音量、段内音量相乘
        TinySliderSettingItem(
            title = stringResource(R.string.cast_bgm_master_volume),
            value = state.bgmVolume * 100f,
            valueRange = 0f..100f,
            steps = 19,
            valueFormat = { "${it.toInt()}%" },
            onValueChange = { bgmVolumePreview = it },
            onValueChangeFinished = {
                onIntent(ReadBookIntent.SetBgmVolume(bgmVolumePreview / 100f))
            },
        )
        Spacer(Modifier.height(12.dp))
        TinySwitchSettingItem(
            title = stringResource(R.string.flow_sys),
            checked = state.readAloudTtsFollowSys,
            onCheckedChange = {
                onIntent(ReadBookIntent.SetReadAloudTtsFollowSys(it))
            },
        )

        TinySliderSettingItem(
            title = stringResource(R.string.read_aloud_speed),
            description = stringResource(R.string.read_aloud_speed_summary),
            value = ttsSpeechRate.toFloat(),
            valueRange = 0f..80f,
            steps = 79,
            enabled = !state.readAloudTtsFollowSys,
            // 拖动中写设置会与外部 value 回流打架（滑块来回跳），松手才提交
            onValueChange = { speechRatePreview = it },
            onValueChangeFinished = {
                onIntent(ReadBookIntent.SetReadAloudTtsSpeechRate(speechRatePreview.toInt()))
            },
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionButton(
                icon = Icons.Default.Menu,
                label = stringResource(R.string.main_menu),
                onClick = onOpenMainMenu,
            )
            ActionButton(
                icon = Icons.AutoMirrored.Filled.List,
                label = stringResource(R.string.chapter_list),
                onClick = onOpenChapterList,
            )
            ActionButton(
                icon = Icons.Default.VisibilityOff,
                label = stringResource(R.string.to_backstage),
                onClick = onGoToBackground,
            )
            ActionButton(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.setting),
                onClick = onShowReadAloudConfig,
            )
            ActionButton(
                icon = Icons.Default.Headphones,
                label = stringResource(R.string.switch_to_read_aloud_player),
                onClick = { onIntent(ReadBookIntent.OpenReadAloudPlayer) },
            )
        }
    }
}

/**
 * 定时入口的摘要：按当前模式显示剩余分钟或剩余章数，未开启显示「关闭」。
 */
@Composable
private fun readAloudTimerSummary(state: ReadBookUiState): String = when {
    state.readAloudTimerMode == ReadAloudTimerMode.Chapter.storageValue &&
            state.readAloudTimerChapters > 0 ->
        stringResource(R.string.timer_chapters, state.readAloudTimerChapters)

    state.readAloudTimerMode == ReadAloudTimerMode.Minute.storageValue &&
            state.readAloudTtsTimer > 0 ->
        stringResource(R.string.timer_m, state.readAloudTtsTimer)

    else -> stringResource(R.string.close)
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediumTonalButton(
            onClick = onClick,
            icon = icon,
            contentDescription = label,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
