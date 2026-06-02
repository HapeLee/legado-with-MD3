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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ReadAloudSheet(
    onDismissRequest: () -> Unit,
    onOpenChapterList: () -> Unit,
    onShowMainMenu: () -> Unit,
    onStopAutoPage: () -> Unit,
    onShowReadAloudConfig: () -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.aloud_config),
    ) {
        ReadAloudContent(
            onDismissRequest = onDismissRequest,
            onOpenChapterList = onOpenChapterList,
            onShowMainMenu = onShowMainMenu,
            onStopAutoPage = onStopAutoPage,
            onShowReadAloudConfig = onShowReadAloudConfig,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
fun ReadAloudContent(
    onDismissRequest: () -> Unit,
    onOpenChapterList: () -> Unit,
    onShowMainMenu: () -> Unit,
    onStopAutoPage: () -> Unit,
    onShowReadAloudConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readAloudSettingsRepository: ReadAloudSettingsRepository = koinInject()
    val preferences by readAloudSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = io.legado.app.data.repository.ReadAloudPreferences()
    )
    var isPaused by remember { mutableStateOf(BaseReadAloudService.pause) }
    var ttsFollowSys by remember(preferences.ttsFollowSys) {
        mutableStateOf(preferences.ttsFollowSys)
    }
    var ttsSpeechRate by remember(preferences.ttsSpeechRate) {
        mutableFloatStateOf(preferences.ttsSpeechRate.toFloat())
    }
    var timerMinute by remember(preferences.ttsTimer) {
        mutableFloatStateOf(
            if (BaseReadAloudService.timeMinute > 0) {
                BaseReadAloudService.timeMinute.toFloat()
            } else {
                preferences.ttsTimer.toFloat()
            }
        )
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
            FilledTonalIconButton(
                onClick = { ReadAloud.prevParagraph(context) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = stringResource(R.string.prev_sentence),
                )
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = {
                    if (isPaused) {
                        ReadAloud.resume(context)
                    } else {
                        ReadAloud.pause(context)
                    }
                    isPaused = !isPaused
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (isPaused) R.drawable.ic_play else R.drawable.ic_pause
                    ),
                    contentDescription = stringResource(
                        if (isPaused) R.string.audio_play else R.string.pause
                    ),
                )
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = {
                    ReadAloud.stop(context)
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
                onClick = { ReadAloud.nextParagraph(context) },
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
            description = stringResource(R.string.timer_m, timerMinute.toInt()),
            value = timerMinute,
            valueRange = 0f..180f,
            steps = 179,
            onValueChange = {
                timerMinute = it
                scope.launch {
                    readAloudSettingsRepository.setTtsTimer(it.toInt())
                }
                ReadAloud.setTimer(context, it.toInt())
            },
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { ReadBook.moveToPrevChapter(upContent = true, toLast = false) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.previous_chapter))
            }
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        readAloudSettingsRepository.setTtsTimer(timerMinute.toInt())
                    }
                    ReadAloud.setTimer(
                        context,
                        timerMinute.toInt()
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.timer_m, timerMinute.toInt()))
            }
            OutlinedButton(
                onClick = { ReadBook.moveToNextChapter(true) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.next_chapter))
            }
        }

        Spacer(Modifier.height(12.dp))

        TinySwitchSettingItem(
            title = stringResource(R.string.flow_sys),
            checked = ttsFollowSys,
            onCheckedChange = {
                ttsFollowSys = it
                scope.launch {
                    readAloudSettingsRepository.setTtsFollowSys(it)
                }
            },
        )

        TinySliderSettingItem(
            title = stringResource(R.string.read_aloud_speed),
            value = ttsSpeechRate,
            valueRange = 0f..80f,
            steps = 79,
            enabled = !ttsFollowSys,
            onValueChange = {
                ttsSpeechRate = it
                scope.launch {
                    readAloudSettingsRepository.setTtsSpeechRate(it.toInt())
                    ReadAloud.upTtsSpeechRate(context)
                }
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
                icon = R.drawable.ic_menu,
                label = stringResource(R.string.main_menu),
                onClick = {
                    onShowMainMenu()
                    onDismissRequest()
                },
            )
            ActionButton(
                icon = R.drawable.ic_visibility_off,
                label = stringResource(R.string.to_backstage),
                onClick = onStopAutoPage,
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
