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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem

@Composable
fun ReadAloudContent(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
    onOpenChapterList: () -> Unit,
    onGoToBackground: () -> Unit,
    onShowReadAloudConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timerMinute = state.readAloudTtsTimer
    val ttsSpeechRate = state.readAloudTtsSpeechRate

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

        TinySliderSettingItem(
            title = stringResource(R.string.set_timer),
            description = stringResource(R.string.timer_m, timerMinute),
            value = timerMinute.toFloat(),
            valueRange = 0f..180f,
            steps = 179,
            onValueChange = {
                onIntent(ReadBookIntent.SetReadAloudTtsTimer(it.toInt()))
            },
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
                onClick = { onIntent(ReadBookIntent.SaveReadAloudTtsTimer(timerMinute)) },
                text = stringResource(R.string.save_tts_timer),
                modifier = Modifier.weight(1f),
            )
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudNextChapter) },
                text = stringResource(R.string.next_chapter),
                modifier = Modifier.weight(1f),
            )
        }

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
            value = ttsSpeechRate.toFloat(),
            valueRange = 0f..80f,
            steps = 79,
            enabled = !state.readAloudTtsFollowSys,
            onValueChange = {
                onIntent(ReadBookIntent.SetReadAloudTtsSpeechRate(it.toInt()))
            },
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
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
        }
    }
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
