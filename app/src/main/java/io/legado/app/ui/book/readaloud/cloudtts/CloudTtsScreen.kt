package io.legado.app.ui.book.readaloud.cloudtts

import android.content.ClipData
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.profile
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CloudTtsScreen(
    state: CloudTtsUiState,
    onIntent: (CloudTtsIntent) -> Unit,
    effects: Flow<CloudTtsEffect>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val player = remember { MediaPlayer() }
    val errorTitle = stringResource(R.string.cloud_tts_error)
    val errorCopied = stringResource(R.string.cloud_tts_error_copied)
    val previewPlaybackFailed = stringResource(R.string.cloud_tts_preview_playback_failed)
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is CloudTtsEffect.ShowToast -> context.toastOnUi(effect.message)
                is CloudTtsEffect.CopyText -> {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(errorTitle, effect.text)))
                    context.toastOnUi(errorCopied)
                }
                is CloudTtsEffect.PlayPreview -> runCatching {
                    player.reset()
                    player.setDataSource(effect.path)
                    player.prepare()
                    player.start()
                }.onFailure { onIntent(CloudTtsIntent.ReportError(
                    "$previewPlaybackFailed\n\n${it::class.java.simpleName}: ${it.localizedMessage.orEmpty()}"
                )) }
            }
        }
    }
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.read_aloud_engines_and_voices),
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = {
                    onIntent(
                        if (state.selectedTab == CloudTtsTab.Engines) CloudTtsIntent.AddEngine
                        else CloudTtsIntent.AddVoice
                    )
                },
                icon = if (state.selectedTab == CloudTtsTab.Engines) Icons.Default.Add
                    else Icons.Default.RecordVoiceOver,
                tooltipText = stringResource(
                    if (state.selectedTab == CloudTtsTab.Engines) R.string.cloud_tts_add_engine
                    else R.string.cloud_tts_add_voice
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item {
                AppTabRow(
                    tabTitles = listOf(
                        stringResource(R.string.cloud_tts_voices_tab),
                        stringResource(R.string.cloud_tts_engines_tab),
                    ),
                    selectedTabIndex = state.selectedTab.ordinal,
                    onTabSelected = { onIntent(CloudTtsIntent.SelectTab(CloudTtsTab.entries[it])) },
                    isScrollable = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.selectedTab == CloudTtsTab.Voices) {
                if (state.voices.isEmpty() && !state.loading) {
                    item { AppText(stringResource(R.string.cloud_tts_no_saved_voices), Modifier.padding(24.dp)) }
                }
                items(state.voices, key = { "voice:${it.id}" }) { voice ->
                    TinyClickableSettingItem(
                        title = voice.title,
                        description = voice.summary,
                        trailingContent = if (voice.deletable) {{
                            MediumTonalButton(
                                onClick = { onIntent(CloudTtsIntent.DeleteVoice(voice.id)) },
                                icon = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }} else null,
                        onClick = {
                            if (voice.editable) onIntent(CloudTtsIntent.EditVoice(voice.id))
                        },
                    )
                }
            } else {
                item { AppText(stringResource(R.string.cloud_tts_engines), Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
                if (state.engines.isEmpty() && !state.loading) {
                    item { AppText(stringResource(R.string.cloud_tts_no_engines), Modifier.padding(24.dp)) }
                }
                items(state.engines, key = { "engine:${it.id}" }) { engine ->
                    TinyClickableSettingItem(
                        title = engine.title,
                        description = engine.summary,
                        trailingContent = {
                            MediumTonalButton(
                                onClick = { onIntent(CloudTtsIntent.DeleteEngine(engine.id)) },
                                icon = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                            )
                        },
                        onClick = { onIntent(CloudTtsIntent.EditEngine(engine.id)) },
                    )
                }
                item { AppText(stringResource(R.string.cloud_tts_available_engines), Modifier.padding(24.dp, 12.dp)) }
                items(state.availableEngines, key = { "engine-info:${it.engineType}:${it.engineId}" }) { engine ->
                    TinyClickableSettingItem(
                        title = engine.title,
                        description = listOf(engine.summary, engine.catalogHint)
                            .filter(String::isNotBlank)
                            .joinToString(" | "),
                        onClick = { onIntent(CloudTtsIntent.AddVoiceForEngine(engine.engineType, engine.engineId)) },
                    )
                }
            }
        }
    }
    CloudTtsEngineEditorSheet(state, state.engineEditor, onIntent)
    VoiceEnginePickerSheet(state.showVoiceEnginePicker, state, onIntent)
    TtsVoicePresetEditorSheet(state, state.voiceEditor, onIntent)
    AppModalBottomSheet(
        data = state.activeDialog as? CloudTtsDialog.Error,
        onDismissRequest = { onIntent(CloudTtsIntent.DismissError) },
        title = stringResource(R.string.cloud_tts_error),
        startAction = {
            MediumTonalButton(
                onClick = { onIntent(CloudTtsIntent.DismissError) },
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
            )
        },
        endAction = {
            MediumTonalButton(
                onClick = { onIntent(CloudTtsIntent.CopyError) },
                icon = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.cloud_tts_copy_error),
            )
        },
    ) { error ->
        AppText(
            text = error.message,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun CloudTtsEngineEditorSheet(
    state: CloudTtsUiState,
    editor: CloudTtsEngineEditorUi?,
    onIntent: (CloudTtsIntent) -> Unit,
) {
    AppModalBottomSheet(
        data = editor,
        onDismissRequest = { onIntent(CloudTtsIntent.DismissEngineEditor) },
        title = stringResource(
            if (editor?.editingEngineId == null) R.string.cloud_tts_add_engine
            else R.string.cloud_tts_edit_engine
        ),
        startAction = {
            MediumTonalButton(
                onClick = { onIntent(CloudTtsIntent.DismissEngineEditor) },
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel),
            )
        },
        endAction = {
            MediumTonalButton(
                onClick = { onIntent(CloudTtsIntent.Save) },
                icon = Icons.Default.Check,
                contentDescription = stringResource(R.string.save),
            )
        },
    ) { currentEditor ->
        CloudTtsEngineEditorContent(state, currentEditor, onIntent)
    }
}

@Composable
private fun CloudTtsEngineEditorContent(
    state: CloudTtsUiState,
    editor: CloudTtsEngineEditorUi,
    onIntent: (CloudTtsIntent) -> Unit,
) {
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var regionMenu by remember { mutableStateOf(false) }
    var customRegion by remember(editor.provider, editor.editingEngineId) {
        val options = CloudTtsProviderType.entries.firstOrNull {
            it.storageValue == editor.provider
        }?.profile?.regionOptions.orEmpty()
        mutableStateOf(editor.region.isNotBlank() && editor.region !in options)
    }
    fun update(value: CloudTtsEngineEditorUi) = onIntent(CloudTtsIntent.UpdateEngineEditor(value))
    val provider = CloudTtsProviderType.entries.firstOrNull { it.storageValue == editor.provider }
        ?: CloudTtsProviderType.Mimo
    val profile = provider.profile
    LazyColumn {
                item { Column(Modifier.padding(horizontal = 16.dp)) {
                    MenuButton(profile.displayName) { providerMenu = true }
                    RoundDropdownMenu(providerMenu, { providerMenu = false }) {
                        CloudTtsProviderType.entries.forEach { option ->
                            RoundDropdownMenuItem(
                                text = option.profile.displayName,
                                onClick = {
                                    providerMenu = false
                                    update(editor.copy(
                                        provider = option.storageValue,
                                        name = if (editor.name.isBlank() || editor.name == profile.displayName) {
                                            option.profile.displayName
                                        } else editor.name,
                                        model = option.profile.defaultModel,
                                        region = option.profile.regionOptions.firstOrNull().orEmpty(),
                                    ))
                                },
                            )
                        }
                    }
                    Field(editor.name, { update(editor.copy(name = it)) }, stringResource(R.string.cloud_tts_engine_name))
                    Field(editor.baseUrl, { update(editor.copy(baseUrl = it)) }, stringResource(R.string.cloud_tts_custom_base_url))
                    Field(editor.apiKey, { update(editor.copy(apiKey = it)) }, profile.apiKeyLabel)
                    if (provider == CloudTtsProviderType.AwsPolly) {
                        Field(editor.secretKey, { update(editor.copy(secretKey = it)) }, "Secret Access Key")
                    }
                    if (profile.regionOptions.isNotEmpty()) {
                        MenuButton(stringResource(R.string.cloud_tts_region_value, editor.region.ifBlank { stringResource(R.string.cloud_tts_please_select) })) { regionMenu = true }
                        RoundDropdownMenu(regionMenu, { regionMenu = false }) {
                            profile.regionOptions.forEach { region ->
                                RoundDropdownMenuItem(
                                    text = region,
                                    onClick = {
                                        regionMenu = false
                                        customRegion = false
                                        update(editor.copy(region = region))
                                    },
                                )
                            }
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.cloud_tts_custom_region_action),
                                onClick = {
                                    regionMenu = false
                                    customRegion = true
                                    update(editor.copy(region = ""))
                                },
                            )
                        }
                        if (customRegion) {
                            Field(editor.region, { update(editor.copy(region = it)) }, stringResource(R.string.cloud_tts_custom_region))
                        }
                    }
                    if (provider == CloudTtsProviderType.Volcengine) {
                        Field(editor.appId, { update(editor.copy(appId = it)) }, "App ID")
                    }
                    if (profile.modelOptions.isNotEmpty()) {
                        MenuButton(stringResource(R.string.cloud_tts_model_value, editor.model.ifBlank { "neural" })) { modelMenu = true }
                        RoundDropdownMenu(modelMenu, { modelMenu = false }) {
                            profile.modelOptions.forEach { model ->
                                RoundDropdownMenuItem(
                                    text = model,
                                    onClick = { modelMenu = false; update(editor.copy(model = model)) },
                                )
                            }
                        }
                    } else {
                        Field(editor.model, { update(editor.copy(model = it)) }, profile.modelLabel)
                    }
                    if (provider == CloudTtsProviderType.Volcengine) {
                        Field(editor.optionsJson, { update(editor.copy(optionsJson = it)) }, stringResource(R.string.cloud_tts_options_json))
                    }
                    MediumTonalButton(
                        onClick = { onIntent(CloudTtsIntent.TestEngine) },
                        enabled = !state.testing,
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(if (state.testing) R.string.cloud_tts_testing else R.string.cloud_tts_connection_test),
                    )
                } }
    }
}

@Composable
private fun TtsVoicePresetEditorSheet(
    state: CloudTtsUiState,
    editor: TtsVoicePresetEditorUi?,
    onIntent: (CloudTtsIntent) -> Unit,
) {
    AppModalBottomSheet(
        data = editor,
        onDismissRequest = { onIntent(CloudTtsIntent.DismissVoiceEditor) },
        title = stringResource(
            if (editor?.editingVoiceId == null) R.string.cloud_tts_add_voice
            else R.string.cloud_tts_edit_voice
        ),
        startAction = {
            MediumTonalButton(
                onClick = { onIntent(CloudTtsIntent.DismissVoiceEditor) },
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel),
            )
        },
        endAction = {
            MediumTonalButton(
                onClick = { onIntent(CloudTtsIntent.Save) },
                enabled = editor?.voiceId?.isNotBlank() == true,
                icon = Icons.Default.Check,
                contentDescription = stringResource(R.string.save),
            )
        },
    ) { currentEditor ->
        TtsVoicePresetEditorContent(state, currentEditor, onIntent)
    }
}

@Composable
private fun TtsVoicePresetEditorContent(
    state: CloudTtsUiState,
    editor: TtsVoicePresetEditorUi,
    onIntent: (CloudTtsIntent) -> Unit,
) {
    var styleMenu by remember { mutableStateOf(false) }
    var roleMenu by remember { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }
    var voiceQuery by remember { mutableStateOf("") }
    var manualVoiceIdVisible by remember(editor.engineId) { mutableStateOf(false) }
    val selectedVoice = state.discoveredVoices.firstOrNull { it.id == editor.voiceId }
    val selectedEngine = state.availableEngines.firstOrNull {
        it.engineType == editor.engineType && it.engineId == editor.engineId
    }
    val filteredVoices = state.discoveredVoices.filter { voice ->
        voiceQuery.isBlank() || voice.label.contains(voiceQuery, ignoreCase = true) ||
            voice.id.contains(voiceQuery, ignoreCase = true)
    }
    fun update(value: TtsVoicePresetEditorUi) = onIntent(CloudTtsIntent.UpdateVoiceEditor(value))

    LazyColumn(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (editor.engineId.isNotBlank()) {
                item {
                    AppText(stringResource(
                        R.string.cloud_tts_step_voice),
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    if (editor.editingVoiceId == null && state.discoveredVoices.isNotEmpty()) {
                        SearchBar(
                            query = voiceQuery,
                            onQueryChange = { voiceQuery = it },
                            placeholder = stringResource(R.string.cloud_tts_search_voice),
                            autoFocus = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }
                    if (state.discovering) {
                        AppText(stringResource(R.string.cloud_tts_loading_voices), Modifier.padding(24.dp, 12.dp))
                    } else if (editor.editingVoiceId == null && filteredVoices.isEmpty()) {
                        AppText(stringResource(R.string.cloud_tts_no_matching_voice), Modifier.padding(24.dp, 12.dp))
                    }
                }
                if (editor.editingVoiceId == null) {
                    items(filteredVoices, key = { it.id }) { voice ->
                        TinyClickableSettingItem(
                            title = voice.label.substringBefore(" · "),
                            description = buildString {
                                voice.label.substringAfter(" · ", "").takeIf(String::isNotBlank)?.let(::append)
                                if (isNotEmpty()) append(" | ")
                                append(voice.id)
                                if (voice.id == editor.voiceId) {
                                    append(" | ")
                                    append(stringResource(R.string.cloud_tts_selected))
                                }
                            },
                            onClick = { onIntent(CloudTtsIntent.SelectVoice(voice.id)) },
                        )
                    }
                } else {
                    item {
                        TinyClickableSettingItem(
                            title = editor.voiceName.ifBlank { editor.voiceId },
                            description = "${editor.voiceId} | ${stringResource(R.string.cloud_tts_current_voice)}",
                            onClick = {},
                        )
                    }
                }
                item {
                    if (selectedEngine?.remoteCatalog == true) {
                        MediumTonalButton(
                            onClick = { onIntent(CloudTtsIntent.DiscoverVoices) },
                            enabled = !state.discovering,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            text = stringResource(R.string.cloud_tts_refresh_catalog),
                        )
                    }
                    if (editor.editingVoiceId == null && editor.engineType == ReadAloudVoice.ENGINE_CLOUD) {
                        MediumTonalButton(
                            onClick = { manualVoiceIdVisible = !manualVoiceIdVisible },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            text = stringResource(if (manualVoiceIdVisible) R.string.cloud_tts_hide_manual_voice else R.string.cloud_tts_manual_voice_action),
                        )
                    }
                    if (manualVoiceIdVisible && editor.editingVoiceId == null &&
                        editor.engineType == ReadAloudVoice.ENGINE_CLOUD) {
                        Field(
                            editor.voiceId,
                            { update(editor.copy(voiceId = it.trim())) },
                            stringResource(R.string.cloud_tts_native_voice_id),
                        )
                        AppText(
                            stringResource(R.string.cloud_tts_manual_voice_hint),
                            Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    AppText(stringResource(R.string.cloud_tts_step_preset), Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                    Field(editor.voiceName, { update(editor.copy(voiceName = it)) }, stringResource(R.string.cloud_tts_preset_name))
                    if (editor.engineType == ReadAloudVoice.ENGINE_CLOUD) {
                        if (!selectedVoice?.styles.isNullOrEmpty()) {
                            MenuButton(stringResource(R.string.cloud_tts_style_value, editor.style.ifBlank { stringResource(R.string.cloud_tts_default) })) { styleMenu = true }
                            RoundDropdownMenu(styleMenu, { styleMenu = false }) {
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.cloud_tts_default),
                                    onClick = { styleMenu = false; update(editor.copy(style = "")) },
                                )
                                selectedVoice.styles.forEach { style ->
                                    RoundDropdownMenuItem(
                                        text = style,
                                        onClick = { styleMenu = false; update(editor.copy(style = style)) },
                                    )
                                }
                            }
                        }
                        if (!selectedVoice?.roles.isNullOrEmpty()) {
                            MenuButton(stringResource(R.string.cloud_tts_role_value, editor.role.ifBlank { stringResource(R.string.cloud_tts_default) })) { roleMenu = true }
                            RoundDropdownMenu(roleMenu, { roleMenu = false }) {
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.cloud_tts_default),
                                    onClick = { roleMenu = false; update(editor.copy(role = "")) },
                                )
                                selectedVoice.roles.forEach { role ->
                                    RoundDropdownMenuItem(
                                        text = role,
                                        onClick = { roleMenu = false; update(editor.copy(role = role)) },
                                    )
                                }
                            }
                        }
                        Field(editor.instructions, { update(editor.copy(instructions = it)) }, stringResource(R.string.cloud_tts_instructions))
                        TinySwitchSettingItem(
                            title = stringResource(R.string.cloud_tts_automatic_emotion),
                            description = stringResource(R.string.cloud_tts_automatic_emotion_summary),
                            checked = editor.automaticEmotion,
                            onCheckedChange = { update(editor.copy(automaticEmotion = it)) },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.cloud_tts_character_personality),
                            description = stringResource(R.string.cloud_tts_character_personality_summary),
                            checked = editor.characterPersonality,
                            onCheckedChange = { update(editor.copy(characterPersonality = it)) },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.cloud_tts_thought_performance),
                            description = stringResource(R.string.cloud_tts_thought_performance_summary),
                            checked = editor.thoughtPerformance,
                            onCheckedChange = { update(editor.copy(thoughtPerformance = it)) },
                        )
                        Field(editor.speed, { update(editor.copy(speed = it)) }, stringResource(R.string.cloud_tts_speed))
                        Field(editor.pitch, { update(editor.copy(pitch = it)) }, stringResource(R.string.cloud_tts_pitch))
                        Field(editor.volume, { update(editor.copy(volume = it)) }, stringResource(R.string.cloud_tts_volume))
                        MenuButton(stringResource(R.string.cloud_tts_audio_format, editor.format)) { formatMenu = true }
                        RoundDropdownMenu(formatMenu, { formatMenu = false }) {
                            editor.formatOptions.forEach { format ->
                                RoundDropdownMenuItem(
                                    text = format,
                                    onClick = { formatMenu = false; update(editor.copy(format = format)) },
                                )
                            }
                        }
                    } else if (editor.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
                        AppText(stringResource(R.string.cloud_tts_system_defaults_hint))
                        Field(editor.speed, { update(editor.copy(speed = it)) }, stringResource(R.string.cloud_tts_speed_multiplier))
                        Field(editor.pitch, { update(editor.copy(pitch = it)) }, stringResource(R.string.cloud_tts_pitch_multiplier))
                    }
                    if (editor.engineType == ReadAloudVoice.ENGINE_HTTP) {
                        AppText(stringResource(R.string.cloud_tts_http_voice_hint))
                    } else {
                        MediumTonalButton(
                            onClick = { onIntent(CloudTtsIntent.Preview) },
                            enabled = editor.voiceId.isNotBlank() && !state.testing,
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(if (state.testing) R.string.cloud_tts_preview_generating else R.string.cloud_tts_preview),
                        )
                    }
                }
            }
    }
}

@Composable
private fun Field(value: String, onValueChange: (String) -> Unit, label: String) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun MenuButton(
    text: String,
    onClick: () -> Unit,
) {
    MediumTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        text = text,
    )
}

@Composable
private fun VoiceEnginePickerSheet(
    show: Boolean,
    state: CloudTtsUiState,
    onIntent: (CloudTtsIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = { onIntent(CloudTtsIntent.DismissVoiceEnginePicker) },
        title = stringResource(R.string.cloud_tts_select_engine),
        startAction = {
            MediumTonalButton(
                onClick = { onIntent(CloudTtsIntent.DismissVoiceEnginePicker) },
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel),
            )
        },
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(
                items = state.availableEngines,
                key = { "picker:${it.engineType}:${it.engineId}" },
            ) { engine ->
                TinyClickableSettingItem(
                    title = engine.title,
                    description = listOf(engine.summary, engine.catalogHint)
                        .filter(String::isNotBlank)
                        .joinToString(" | "),
                    onClick = {
                        onIntent(CloudTtsIntent.AddVoiceForEngine(engine.engineType, engine.engineId))
                    },
                )
            }
        }
    }
}
