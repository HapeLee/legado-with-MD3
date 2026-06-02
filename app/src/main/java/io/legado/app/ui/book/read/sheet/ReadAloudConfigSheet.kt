package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.repository.ReadAloudPreferences
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.help.IntentHelp
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.utils.TTSCacheUtils
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ReadAloudConfigSheet(
    onDismissRequest: () -> Unit,
    onSelectSpeakEngine: () -> Unit,
    onOpenPreDownloadNumPicker: () -> Unit,
    onOpenCacheCleanTimePicker: () -> Unit,
) {
    val context = LocalContext.current
    val readAloudSettingsRepository: ReadAloudSettingsRepository = koinInject()
    val preferences by readAloudSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadAloudPreferences()
    )
    val scope = rememberCoroutineScope()

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.aloud_config),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TinySwitchSettingItem(
                title = stringResource(R.string.ignore_audio_focus_title),
                description = stringResource(R.string.ignore_audio_focus_summary),
                checked = preferences.ignoreAudioFocus,
                onCheckedChange = {
                    scope.launch { readAloudSettingsRepository.setIgnoreAudioFocus(it) }
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.pause_read_aloud_while_phone_calls_title),
                description = stringResource(R.string.pause_read_aloud_while_phone_calls_summary),
                checked = preferences.pauseReadAloudWhilePhoneCalls,
                enabled = preferences.ignoreAudioFocus,
                onCheckedChange = {
                    scope.launch {
                        readAloudSettingsRepository.setPauseReadAloudWhilePhoneCalls(it)
                    }
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.read_aloud_wake_lock),
                description = stringResource(R.string.read_aloud_wake_lock_summary),
                checked = preferences.readAloudWakeLock,
                onCheckedChange = {
                    scope.launch { readAloudSettingsRepository.setReadAloudWakeLock(it) }
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.pref_media_button_per_next),
                description = stringResource(R.string.pref_media_button_per_next_summary),
                checked = preferences.mediaButtonPerNext,
                onCheckedChange = {
                    scope.launch { readAloudSettingsRepository.setMediaButtonPerNext(it) }
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.read_aloud_by_page),
                description = stringResource(R.string.read_aloud_by_page_summary),
                checked = preferences.readAloudByPage,
                onCheckedChange = {
                    scope.launch { readAloudSettingsRepository.setReadAloudByPage(it) }
                    if (it) {
                        postEvent(EventBus.MEDIA_BUTTON, false)
                    }
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.system_media_control_compatibility_change),
                description = stringResource(R.string.system_media_control_compatibility_change_summary),
                checked = preferences.systemMediaControlCompatibilityChange,
                onCheckedChange = {
                    scope.launch {
                        readAloudSettingsRepository.setSystemMediaControlCompatibilityChange(it)
                    }
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.stream_read_aloud_audio),
                description = stringResource(R.string.stream_read_aloud_audio_summary),
                checked = preferences.streamReadAloudAudio,
                onCheckedChange = {
                    scope.launch { readAloudSettingsRepository.setStreamReadAloudAudio(it) }
                    if (it) {
                        postEvent(EventBus.MEDIA_BUTTON, false)
                    }
                },
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.speak_engine),
                onClick = onSelectSpeakEngine,
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.sys_tts_config),
                onClick = { IntentHelp.openTTSSetting() },
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.read_aloud_preload),
                onClick = onOpenPreDownloadNumPicker,
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.audio_cache_clean_time),
                onClick = onOpenCacheCleanTimePicker,
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.clear_cache),
                onClick = {
                    TTSCacheUtils.clearTtsCache()
                    context.toastOnUi("音频缓存已清理")
                },
            )
        }
    }
}
