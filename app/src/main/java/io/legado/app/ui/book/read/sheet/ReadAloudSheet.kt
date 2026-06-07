package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem

@Composable
fun ReadAloudSheet(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
    onOpenChapterList: () -> Unit,
    onGoToBackground: () -> Unit,
    onShowReadAloudConfig: () -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.aloud_config),
    ) {
        ReadAloudContent(
            state = state,
            onIntent = onIntent,
            onDismissRequest = onDismissRequest,
            onOpenChapterList = onOpenChapterList,
            onGoToBackground = onGoToBackground,
            onShowReadAloudConfig = onShowReadAloudConfig,
            modifier = Modifier
                .padding(bottom = 16.dp),
        )
    }
}

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
            FilledTonalIconButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudPrevParagraph) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = stringResource(R.string.prev_sentence),
                )
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = {
                    onIntent(ReadBookIntent.ReadAloudTogglePause)
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (state.isReadAloudPaused) R.drawable.ic_play else R.drawable.ic_pause
                    ),
                    contentDescription = stringResource(
                        if (state.isReadAloudPaused) R.string.audio_play else R.string.pause
                    ),
                )
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = {
                    onIntent(ReadBookIntent.ReadAloudStop)
                    onDismissRequest()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stop_black_24dp),
                    contentDescription = stringResource(R.string.stop),
                )
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudNextParagraph) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.next_sentence))
            }
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
            OutlinedButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudPrevChapter) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.previous_chapter))
            }
            FilledTonalButton(
                onClick = {
                    onIntent(ReadBookIntent.SetReadAloudTtsTimer(timerMinute))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.timer_m, timerMinute))
            }
            OutlinedButton(
                onClick = { onIntent(ReadBookIntent.ReadAloudNextChapter) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.next_chapter))
            }
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
                icon = R.drawable.ic_toc,
                label = stringResource(R.string.chapter_list),
                onClick = onOpenChapterList,
            )
            ActionButton(
                icon = R.drawable.ic_visibility_off,
                label = stringResource(R.string.to_backstage),
                onClick = onGoToBackground,
            )
            ActionButton(
                icon = R.drawable.ic_settings,
                label = stringResource(R.string.setting),
                onClick = onShowReadAloudConfig,
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
