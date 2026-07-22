package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.data.entities.HttpTTS
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerIntent
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerUiState
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.utils.GSON
import kotlinx.coroutines.launch

@Composable
fun ReadAloudConfigContent(
    state: ReadBookUiState,
    playerState: ReadAloudPlayerUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onPlayerIntent: (ReadAloudPlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        CardTabRow(
            modifier = modifier,
            tabTitles = listOf(
                stringResource(R.string.read_aloud_settings_general_tab),
                stringResource(R.string.read_aloud_settings_voice_tab),
            ),
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            }
        )
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            ) {
                if (page == 0) {
                    TinyDropdownSettingItem(
                        title = stringResource(R.string.default_read_aloud_interface),
                        selectedValue = state.defaultReadAloudInterface,
                        displayEntries = arrayOf(
                            stringResource(R.string.read_aloud_interface_classic),
                            stringResource(R.string.read_aloud_interface_player),
                        ),
                        entryValues = arrayOf("classic", "player"),
                        description = stringResource(R.string.default_read_aloud_interface_summary),
                        onValueChange = { onIntent(ReadBookIntent.SetDefaultReadAloudInterface(it)) },
                    )
                    TinyDropdownSettingItem(
                        title = stringResource(R.string.read_aloud_player_background),
                        selectedValue = playerState.bgMode.toString(),
                        displayEntries = arrayOf(
                            stringResource(R.string.read_aloud_bg_solid),
                            stringResource(R.string.read_aloud_bg_blur),
                            stringResource(R.string.read_aloud_bg_flowing_light),
                            stringResource(R.string.read_aloud_bg_transparent),
                        ),
                        entryValues = arrayOf(
                            ReadAloudBgMode.Solid.toString(),
                            ReadAloudBgMode.Blur.toString(),
                            ReadAloudBgMode.FlowingLight.toString(),
                            ReadAloudBgMode.Transparent.toString(),
                        ),
                        onValueChange = { onPlayerIntent(ReadAloudPlayerIntent.SetBgMode(it.toInt())) },
                    )
                    TinySwitchSettingItem(
                        title = stringResource(R.string.show_read_aloud_capsule),
                        description = stringResource(R.string.show_read_aloud_capsule_summary),
                        checked = state.showReadAloudCapsule,
                        onCheckedChange = {
                            onIntent(ReadBookIntent.SetShowReadAloudCapsule(it))
                        },
                    )
                    if (state.showReadAloudCapsule) {
                        TinySwitchSettingItem(
                            title = stringResource(R.string.capsule_auto_collapse),
                            description = stringResource(R.string.capsule_auto_collapse_summary),
                            checked = state.capsuleAutoCollapse,
                            onCheckedChange = {
                                onIntent(ReadBookIntent.SetCapsuleAutoCollapse(it))
                            },
                        )
                    }
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
                        title = stringResource(R.string.reset_read_aloud_capsule_position),
                        description = stringResource(R.string.reset_read_aloud_capsule_position_summary),
                        onClick = { onIntent(ReadBookIntent.ResetReadAloudCapsulePosition) },
                    )
                } else {
                    TinyClickableSettingItem(
                        title = stringResource(R.string.read_aloud_engines_and_voices),
                        description = stringResource(R.string.read_aloud_engines_and_voices_summary),
                        onClick = { onIntent(ReadBookIntent.OpenTtsEnginesAndVoices) },
                    )
                    TinyClickableSettingItem(
                        title = stringResource(R.string.tts_cache_manage),
                        description = stringResource(R.string.tts_cache_manage_summary),
                        onClick = { onIntent(ReadBookIntent.OpenTtsCache) },
                    )
                    TinyClickableSettingItem(
                        title = stringResource(R.string.read_aloud_character_casting),
                        description = stringResource(R.string.book_voice_casting_entry_summary),
                        onClick = { onIntent(ReadBookIntent.OpenBookVoiceCasting) },
                    )
                    TinyDropdownSettingItem(
                        title = stringResource(R.string.speech_analysis_mode),
                        selectedValue = state.speechAnalysisMode,
                        displayEntries = arrayOf(
                            stringResource(R.string.speech_analysis_rule),
                            stringResource(R.string.speech_analysis_rule_ai),
                            stringResource(R.string.speech_analysis_ai),
                        ),
                        entryValues = arrayOf("rule", "rule_with_ai", "ai_understanding"),
                        description = when (state.speechAnalysisMode) {
                            "rule_with_ai" -> stringResource(R.string.speech_analysis_rule_ai_summary)
                            "ai_understanding" -> stringResource(R.string.speech_analysis_ai_summary)
                            else -> stringResource(R.string.speech_analysis_rule_summary)
                        },
                        onValueChange = { onIntent(ReadBookIntent.SetSpeechAnalysisMode(it)) },
                    )
                    TinySwitchSettingItem(
                        title = stringResource(R.string.use_multi_speaker),
                        description = stringResource(R.string.use_multi_speaker_summary),
                        checked = state.useMultiSpeaker,
                        onCheckedChange = {
                            onIntent(ReadBookIntent.SetUseMultiSpeaker(it))
                        },
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
                        title = stringResource(R.string.tts_pre_synthesis_concurrency),
                        onClick = { onIntent(ReadBookIntent.OpenPreSynthesisConcurrencyPicker) },
                    )
                    TinyClickableSettingItem(
                        title = stringResource(R.string.tts_paragraph_interval),
                        onClick = { onIntent(ReadBookIntent.OpenParagraphIntervalPicker) },
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
    }
}

@Composable
fun SpeakEngineConfigSheet(
    show: Boolean,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val items = state.ttsEngineItems
    val selectedValue = state.selectedTtsEngine
    var pendingEngineSelection by remember { mutableStateOf<PendingSpeakEngineSelection?>(null) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }

    AppAlertDialog(
        show = pendingEngineSelection != null,
        onDismissRequest = { pendingEngineSelection = null },
        title = stringResource(R.string.read_aloud_default_engine),
        text = stringResource(R.string.speak_engine_apply_scope),
        confirmText = stringResource(R.string.general),
        onConfirm = {
            onIntent(ReadBookIntent.ApplySpeakEngine(pendingEngineSelection?.value))
            pendingEngineSelection = null
        },
        dismissText = stringResource(R.string.book),
        onDismiss = {
            onIntent(ReadBookIntent.ApplySpeakEnginePerBook(pendingEngineSelection?.value))
            pendingEngineSelection = null
        },
    )
    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(ReadBookIntent.ImportHttpTtsSource(it))
        },
    )
    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(R.string.import_tts),
        onSelectSysFile = {
            showImportSheet = false
            onIntent(ReadBookIntent.ImportHttpTtsFile)
        },
        onManualInput = {
            showImportSheet = false
            showUrlInput = true
        },
        allowExtensions = arrayOf("json", "txt"),
    )
    BatchImportDialog(
        title = stringResource(R.string.import_tts),
        importState = state.httpTtsImportState,
        onDismissRequest = { onIntent(ReadBookIntent.CancelHttpTtsImport) },
        onToggleItem = { onIntent(ReadBookIntent.ToggleHttpTtsImportSelection(it)) },
        onToggleAll = { onIntent(ReadBookIntent.ToggleHttpTtsImportAll(it)) },
        onUpdateItem = { index, httpTTS ->
            onIntent(ReadBookIntent.UpdateHttpTtsImportItem(index, httpTTS))
        },
        onConfirm = { onIntent(ReadBookIntent.SaveImportedHttpTts) },
        itemTitle = { it.name },
        itemSubtitle = { it.url },
    )

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_aloud_default_engine),
        startAction = {
            SmallTonalButton(
                onClick = { onIntent(ReadBookIntent.EditHttpTts()) },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.add)
            )
        },
        endAction = {
            var expanded by remember { mutableStateOf(false) }
            Box {
                SmallTonalButton(
                    onClick = { expanded = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_menu)
                )
                RoundDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.import_tts),
                        onClick = {
                            expanded = false
                            showImportSheet = true
                        },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.export),
                        onClick = {
                            expanded = false
                            onIntent(ReadBookIntent.ExportAllHttpTts)
                        },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.copy_url),
                        onClick = {
                            expanded = false
                            onIntent(ReadBookIntent.ExportAllHttpTtsAsUrl)
                        },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.clear_cache),
                        onClick = {
                            expanded = false
                            onIntent(ReadBookIntent.ClearTtsCache)
                        },
                    )
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(bottom = 8.dp),
        ) {
            items(items) { item ->
                val httpTtsId = item.value?.toLongOrNull()
                val isSelected = item.value == selectedValue
                TinyClickableSettingItem(
                    title = item.title,
                    description = if (isSelected) {
                        stringResource(R.string.read_aloud_current_default_summary)
                    } else {
                        null
                    },
                    onClick = { pendingEngineSelection = PendingSpeakEngineSelection(item.value) },
                    onLongClick = if (httpTtsId != null && !item.loginUrl.isNullOrBlank()) {
                        { onIntent(ReadBookIntent.OpenHttpTtsLogin(httpTtsId)) }
                    } else null,
                    trailingContent = if (httpTtsId != null) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                SmallTonalButton(
                                    onClick = { onIntent(ReadBookIntent.EditHttpTts(httpTtsId)) },
                                    icon = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                )
                                SmallTonalButton(
                                    onClick = { onIntent(ReadBookIntent.DeleteHttpTts(httpTtsId)) },
                                    icon = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                )
                            }
                        }
                    } else null,
                )
            }
        }
    }
}

private data class PendingSpeakEngineSelection(val value: String?)

@Composable
fun HttpTtsEditSheet(
    show: Boolean,
    httpTTS: HttpTTS?,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val tts = httpTTS ?: return
    val clipboardManager = LocalClipboardManager.current
    var name by remember(httpTTS) { mutableStateOf(tts.name) }
    var url by remember(httpTTS) { mutableStateOf(tts.url) }
    var contentType by remember(httpTTS) { mutableStateOf(tts.contentType ?: "") }
    var concurrentRate by remember(httpTTS) { mutableStateOf(tts.concurrentRate ?: "0") }
    var header by remember(httpTTS) { mutableStateOf(tts.header ?: "") }
    var loginUrl by remember(httpTTS) { mutableStateOf(tts.loginUrl ?: "") }
    var loginUi by remember(httpTTS) { mutableStateOf(tts.loginUi ?: "") }
    var loginCheckJs by remember(httpTTS) { mutableStateOf(tts.loginCheckJs ?: "") }
    var jsLib by remember(httpTTS) { mutableStateOf(tts.jsLib ?: "") }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.speak_engine),
        startAction = {
            var expanded by remember { mutableStateOf(false) }
            Box {
                SmallTonalButton(
                    onClick = { expanded = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_menu)
                )
                RoundDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.copy_text),
                        onClick = {
                            expanded = false
                            val json = GSON.toJson(
                                tts.copy(
                                    name = name, url = url, contentType = contentType,
                                    concurrentRate = concurrentRate, header = header,
                                    loginUrl = loginUrl, loginUi = loginUi,
                                    loginCheckJs = loginCheckJs, jsLib = jsLib,
                                )
                            )
                            clipboardManager.setText(AnnotatedString(json))
                        },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.paste_source),
                        onClick = {
                            expanded = false
                            clipboardManager.getText()?.text?.let { text ->
                                HttpTTS.fromJson(text).getOrNull()?.let { imported ->
                                    name = imported.name
                                    url = imported.url
                                    contentType = imported.contentType ?: ""
                                    concurrentRate = imported.concurrentRate ?: "0"
                                    header = imported.header ?: ""
                                    loginUrl = imported.loginUrl ?: ""
                                    loginUi = imported.loginUi ?: ""
                                    loginCheckJs = imported.loginCheckJs ?: ""
                                    jsLib = imported.jsLib ?: ""
                                }
                            }
                        },
                    )
                }
            }
        },
        endAction = {
            SmallTonalButton(
                icon = Icons.Default.Save,
                contentDescription = stringResource(R.string.save),
                onClick = {
                    onIntent(
                        ReadBookIntent.SaveHttpTts(
                            tts.copy(
                                name = name, url = url,
                                contentType = contentType.ifBlank { null },
                                concurrentRate = concurrentRate,
                                header = header.ifBlank { null },
                                loginUrl = loginUrl.ifBlank { null },
                                loginUi = loginUi.ifBlank { null },
                                loginCheckJs = loginCheckJs.ifBlank { null },
                                jsLib = jsLib.ifBlank { null },
                            )
                        )
                    )
                },
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.name),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "URL",
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppTextField(
                    value = contentType,
                    onValueChange = { contentType = it },
                    label = "Content-Type",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppTextField(
                    value = concurrentRate,
                    onValueChange = { concurrentRate = it },
                    label = stringResource(R.string.concurrent_rate),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppTextField(
                    value = header,
                    onValueChange = { header = it },
                    label = stringResource(R.string.source_http_header),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppTextField(
                    value = loginUrl,
                    onValueChange = { loginUrl = it },
                    label = stringResource(R.string.login_url),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppTextField(
                    value = loginUi,
                    onValueChange = { loginUi = it },
                    label = stringResource(R.string.login_ui),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppTextField(
                    value = loginCheckJs,
                    onValueChange = { loginCheckJs = it },
                    label = stringResource(R.string.login_check_js),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AppTextField(
                    value = jsLib,
                    onValueChange = { jsLib = it },
                    label = "jsLib",
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
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
