package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.IntentHelp
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.utils.TTSCacheUtils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.toastOnUi

@Composable
fun ReadAloudConfigSheet(
    onDismissRequest: () -> Unit,
    onSelectSpeakEngine: () -> Unit,
    onOpenPreDownloadNumPicker: () -> Unit,
    onOpenCacheCleanTimePicker: () -> Unit,
) {
    val context = LocalContext.current
    var ignoreAudioFocus by remember { mutableStateOf(context.getPrefBoolean(PreferKey.ignoreAudioFocus)) }
    var pauseReadAloudWhilePhoneCalls by remember { mutableStateOf(context.getPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls)) }
    var readAloudWakeLock by remember { mutableStateOf(context.getPrefBoolean(PreferKey.readAloudWakeLock)) }
    var mediaButtonPerNext by remember { mutableStateOf(context.getPrefBoolean("mediaButtonPerNext")) }
    var readAloudByPage by remember { mutableStateOf(context.getPrefBoolean(PreferKey.readAloudByPage)) }
    var systemMediaControlCompatibilityChange by remember {
        mutableStateOf(
            context.getPrefBoolean(
                PreferKey.systemMediaControlCompatibilityChange
            )
        )
    }
    var streamReadAloudAudio by remember { mutableStateOf(context.getPrefBoolean(PreferKey.streamReadAloudAudio)) }

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
                checked = ignoreAudioFocus,
                onCheckedChange = {
                    ignoreAudioFocus = it
                    context.putPrefBoolean(PreferKey.ignoreAudioFocus, it)
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.pause_read_aloud_while_phone_calls_title),
                description = stringResource(R.string.pause_read_aloud_while_phone_calls_summary),
                checked = pauseReadAloudWhilePhoneCalls,
                enabled = ignoreAudioFocus,
                onCheckedChange = {
                    pauseReadAloudWhilePhoneCalls = it
                    context.putPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, it)
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.read_aloud_wake_lock),
                description = stringResource(R.string.read_aloud_wake_lock_summary),
                checked = readAloudWakeLock,
                onCheckedChange = {
                    readAloudWakeLock = it
                    context.putPrefBoolean(PreferKey.readAloudWakeLock, it)
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.pref_media_button_per_next),
                description = stringResource(R.string.pref_media_button_per_next_summary),
                checked = mediaButtonPerNext,
                onCheckedChange = {
                    mediaButtonPerNext = it
                    context.putPrefBoolean("mediaButtonPerNext", it)
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.read_aloud_by_page),
                description = stringResource(R.string.read_aloud_by_page_summary),
                checked = readAloudByPage,
                onCheckedChange = {
                    readAloudByPage = it
                    context.putPrefBoolean(PreferKey.readAloudByPage, it)
                    if (readAloudByPage) {
                        postEvent(EventBus.MEDIA_BUTTON, false)
                    }
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.system_media_control_compatibility_change),
                description = stringResource(R.string.system_media_control_compatibility_change_summary),
                checked = systemMediaControlCompatibilityChange,
                onCheckedChange = {
                    systemMediaControlCompatibilityChange = it
                    context.putPrefBoolean(PreferKey.systemMediaControlCompatibilityChange, it)
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.stream_read_aloud_audio),
                description = stringResource(R.string.stream_read_aloud_audio_summary),
                checked = streamReadAloudAudio,
                onCheckedChange = {
                    streamReadAloudAudio = it
                    context.putPrefBoolean(PreferKey.streamReadAloudAudio, it)
                    if (streamReadAloudAudio) {
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
