package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookTtsEngineItem
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem

@Composable
fun ReadAloudConfigSheet(
    show: Boolean,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
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
                checked = state.readAloudIgnoreAudioFocus,
                onCheckedChange = {
                    onIntent(ReadBookIntent.SetReadAloudIgnoreAudioFocus(it))
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.pause_read_aloud_while_phone_calls_title),
                description = stringResource(R.string.pause_read_aloud_while_phone_calls_summary),
                checked = state.readAloudPauseOnPhoneCall,
                enabled = state.readAloudIgnoreAudioFocus,
                onCheckedChange = {
                    onIntent(ReadBookIntent.SetReadAloudPauseOnPhoneCall(it))
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.read_aloud_wake_lock),
                description = stringResource(R.string.read_aloud_wake_lock_summary),
                checked = state.readAloudWakeLock,
                onCheckedChange = {
                    onIntent(ReadBookIntent.SetReadAloudWakeLock(it))
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.pref_media_button_per_next),
                description = stringResource(R.string.pref_media_button_per_next_summary),
                checked = state.readAloudMediaButtonPerNext,
                onCheckedChange = {
                    onIntent(ReadBookIntent.SetReadAloudMediaButtonPerNext(it))
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.read_aloud_by_page),
                description = stringResource(R.string.read_aloud_by_page_summary),
                checked = state.readAloudByPage,
                onCheckedChange = {
                    onIntent(ReadBookIntent.SetReadAloudByPage(it))
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.system_media_control_compatibility_change),
                description = stringResource(R.string.system_media_control_compatibility_change_summary),
                checked = state.readAloudSystemMediaCompat,
                onCheckedChange = {
                    onIntent(ReadBookIntent.SetReadAloudSystemMediaCompat(it))
                },
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.stream_read_aloud_audio),
                description = stringResource(R.string.stream_read_aloud_audio_summary),
                checked = state.readAloudStreamAudio,
                onCheckedChange = {
                    onIntent(ReadBookIntent.SetReadAloudStreamAudio(it))
                },
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.speak_engine),
                onClick = { onIntent(ReadBookIntent.SelectSpeakEngine) },
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.sys_tts_config),
                onClick = { onIntent(ReadBookIntent.OpenSystemTtsSettings) },
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.read_aloud_preload),
                onClick = { onIntent(ReadBookIntent.OpenPreDownloadNumPicker) },
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.audio_cache_clean_time),
                onClick = { onIntent(ReadBookIntent.OpenCacheCleanTimePicker) },
            )
            TinyClickableSettingItem(
                title = stringResource(R.string.clear_cache),
                onClick = { onIntent(ReadBookIntent.ClearTtsCache) },
            )
        }
    }
}

@Composable
fun SpeakEngineConfigSheet(
    show: Boolean,
    items: List<ReadBookTtsEngineItem>,
    selectedValue: String?,
    onSelect: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.speak_engine),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            items(items) { item ->
                TinyClickableSettingItem(
                    title = item.title,
                    description = if (item.value == selectedValue) {
                        stringResource(R.string.default_version)
                    } else {
                        null
                    },
                    onClick = { onSelect(item.value) },
                )
            }
        }
    }
}

@Composable
fun ReadAloudNumberConfigSheet(
    show: Boolean,
    title: String,
    description: String,
    value: Int,
    defaultValue: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            SliderSettingItem(
                title = title,
                description = description,
                value = value.toFloat(),
                defaultValue = defaultValue.toFloat(),
                valueRange = valueRange,
                onValueChange = { onValueChange(it.toInt()) },
            )
        }
    }
}
