package io.legado.app.ui.book.readaloud.cloudtts

import android.content.ClipData
import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.profile
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
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
    AppScaffold(topBar = {
        GlassMediumFlexibleTopAppBar(
            title = stringResource(R.string.read_aloud_engines_and_voices),
            navigationIcon = { TopBarNavigationButton(onClick = onBack) },
            actions = {
                if (state.selectedTab == CloudTtsTab.Engines) {
                    TopBarActionButton(
                        onClick = { onIntent(CloudTtsIntent.AddEngine) },
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cloud_tts_add_engine),
                    )
                } else {
                    TopBarActionButton(
                        onClick = { onIntent(CloudTtsIntent.AddVoice) },
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = stringResource(R.string.cloud_tts_add_voice),
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
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
                item { Text(stringResource(R.string.cloud_tts_choose_engine), Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
                items(
                    items = state.availableEngines,
                    key = { "available:${it.engineType}:${it.engineId}" },
                ) { engine ->
                    ClickableSettingItem(
                        title = engine.title,
                        description = "${engine.summary}\n${engine.catalogHint}",
                        option = stringResource(R.string.cloud_tts_select_voice),
                        onClick = { onIntent(CloudTtsIntent.AddVoiceForEngine(engine.engineType, engine.engineId)) },
                    )
                }
                item { HorizontalDivider(); Text(stringResource(R.string.cloud_tts_saved_voices), Modifier.padding(24.dp, 12.dp)) }
                if (state.voices.isEmpty() && !state.loading) {
                    item { Text(stringResource(R.string.cloud_tts_no_saved_voices), Modifier.padding(24.dp)) }
                }
                items(state.voices, key = { "voice:${it.id}" }) { voice ->
                    ListItem(
                        supportingContent = { Text(voice.summary) },
                        trailingContent = if (voice.deletable) {{
                            TextButton(onClick = { onIntent(CloudTtsIntent.DeleteVoice(voice.id)) }) {
                                Text(stringResource(R.string.delete))
                            }
                        }} else null,
                        modifier = if (voice.editable) Modifier.clickable {
                            onIntent(CloudTtsIntent.EditVoice(voice.id))
                        } else Modifier,
                    ) { Text(voice.title) }
                }
            } else {
                item { Text(stringResource(R.string.cloud_tts_engines), Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
                if (state.engines.isEmpty() && !state.loading) {
                    item { Text(stringResource(R.string.cloud_tts_no_engines), Modifier.padding(24.dp)) }
                }
                items(state.engines, key = { "engine:${it.id}" }) { engine ->
                    ListItem(
                        supportingContent = { Text(engine.summary) },
                        trailingContent = { TextButton(onClick = {
                            onIntent(CloudTtsIntent.DeleteEngine(engine.id))
                        }) { Text(stringResource(R.string.delete)) } },
                        modifier = Modifier.clickable { onIntent(CloudTtsIntent.EditEngine(engine.id)) },
                    ) { Text(engine.title) }
                }
                item { HorizontalDivider(); Text(stringResource(R.string.cloud_tts_available_engines), Modifier.padding(24.dp, 12.dp)) }
                items(state.availableEngines, key = { "engine-info:${it.engineType}:${it.engineId}" }) { engine ->
                    ClickableSettingItem(
                        title = engine.title,
                        description = engine.summary,
                        option = engine.catalogHint,
                        onClick = { onIntent(CloudTtsIntent.AddVoiceForEngine(engine.engineType, engine.engineId)) },
                    )
                }
            }
        }
    }
    state.engineEditor?.let { CloudTtsEngineEditorDialog(state, it, onIntent) }
    state.voiceEditor?.let { TtsVoicePresetEditorSheet(state, it, onIntent) }
    AppAlertDialog(
        data = state.activeDialog as? CloudTtsDialog.Error,
        onDismissRequest = { onIntent(CloudTtsIntent.DismissError) },
        title = stringResource(R.string.cloud_tts_error),
        textProvider = { message },
        confirmText = stringResource(R.string.cloud_tts_copy_error),
        onConfirm = { onIntent(CloudTtsIntent.CopyError) },
        dismissText = stringResource(R.string.close),
        onDismiss = { onIntent(CloudTtsIntent.DismissError) },
    )
}

@Composable
private fun CloudTtsEngineEditorDialog(
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
    AlertDialog(
        onDismissRequest = { onIntent(CloudTtsIntent.DismissEngineEditor) },
        title = { Text(stringResource(if (editor.editingEngineId == null) R.string.cloud_tts_add_engine else R.string.cloud_tts_edit_engine)) },
        text = {
            LazyColumn {
                item {
                    Button(onClick = { providerMenu = true }, Modifier.fillMaxWidth()) {
                        Text(profile.displayName)
                    }
                    DropdownMenu(providerMenu, { providerMenu = false }) {
                        CloudTtsProviderType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.profile.displayName) },
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
                        Button(onClick = { regionMenu = true }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cloud_tts_region_value, editor.region.ifBlank { stringResource(R.string.cloud_tts_please_select) }))
                        }
                        DropdownMenu(regionMenu, { regionMenu = false }) {
                            profile.regionOptions.forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(region) },
                                    onClick = {
                                        regionMenu = false
                                        customRegion = false
                                        update(editor.copy(region = region))
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cloud_tts_custom_region_action)) },
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
                        Button(onClick = { modelMenu = true }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cloud_tts_model_value, editor.model.ifBlank { "neural" }))
                        }
                        DropdownMenu(modelMenu, { modelMenu = false }) {
                            profile.modelOptions.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
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
                    Button(
                        onClick = { onIntent(CloudTtsIntent.TestEngine) },
                        enabled = !state.testing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (state.testing) R.string.cloud_tts_testing else R.string.cloud_tts_connection_test)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onIntent(CloudTtsIntent.Save) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { onIntent(CloudTtsIntent.DismissEngineEditor) }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TtsVoicePresetEditorSheet(
    state: CloudTtsUiState,
    editor: TtsVoicePresetEditorUi,
    onIntent: (CloudTtsIntent) -> Unit,
) {
    var engineMenu by remember { mutableStateOf(false) }
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

    AppModalBottomSheet(
        show = true,
        onDismissRequest = { onIntent(CloudTtsIntent.DismissVoiceEditor) },
        title = stringResource(if (editor.editingVoiceId == null) R.string.cloud_tts_add_voice else R.string.cloud_tts_edit_voice),
        startAction = {
            TextButton(onClick = { onIntent(CloudTtsIntent.DismissVoiceEditor) }) {
                Text(stringResource(R.string.cancel))
            }
        },
        endAction = {
            TextButton(
                onClick = { onIntent(CloudTtsIntent.Save) },
                enabled = editor.voiceId.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(stringResource(R.string.cloud_tts_step_engine), Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                if (selectedEngine == null && editor.editingVoiceId == null) {
                    state.availableEngines.forEach { engine ->
                        ClickableSettingItem(
                            title = engine.title,
                            description = "${engine.summary}\n${engine.catalogHint}",
                            option = stringResource(R.string.cloud_tts_select),
                            onClick = {
                                onIntent(CloudTtsIntent.SelectEngine(engine.engineType, engine.engineId))
                            },
                        )
                    }
                } else {
                    Button(
                        onClick = { engineMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editor.editingVoiceId == null,
                    ) {
                        Text(editor.engineName.ifBlank { stringResource(R.string.cloud_tts_select_engine) })
                    }
                    DropdownMenu(engineMenu, { engineMenu = false }) {
                        state.availableEngines.forEach { engine ->
                            DropdownMenuItem(
                                text = { Column { Text(engine.title); Text(engine.summary) } },
                                onClick = {
                                    engineMenu = false
                                    onIntent(CloudTtsIntent.SelectEngine(engine.engineType, engine.engineId))
                                },
                            )
                        }
                    }
                    selectedEngine?.let {
                        Text(
                            it.catalogHint,
                            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    if (editor.editingVoiceId != null) {
                        Text(
                            stringResource(R.string.cloud_tts_edit_voice_binding_notice),
                            Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
            }
            if (editor.engineId.isNotBlank()) {
                item {
                    Text(stringResource(R.string.cloud_tts_step_voice), Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                    if (editor.editingVoiceId == null && state.discoveredVoices.isNotEmpty()) {
                        OutlinedTextField(
                            value = voiceQuery,
                            onValueChange = { voiceQuery = it },
                            label = { Text(stringResource(R.string.cloud_tts_search_voice)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            singleLine = true,
                        )
                    }
                    if (state.discovering) {
                        Text(stringResource(R.string.cloud_tts_loading_voices), Modifier.padding(24.dp, 12.dp))
                    } else if (editor.editingVoiceId == null && filteredVoices.isEmpty()) {
                        Text(stringResource(R.string.cloud_tts_no_matching_voice), Modifier.padding(24.dp, 12.dp))
                    }
                }
                if (editor.editingVoiceId == null) {
                    items(filteredVoices, key = { it.id }) { voice ->
                        ClickableSettingItem(
                            title = voice.label.substringBefore(" · "),
                            description = buildString {
                                voice.label.substringAfter(" · ", "").takeIf(String::isNotBlank)?.let(::append)
                                if (isNotEmpty()) append('\n')
                                append(voice.id)
                            },
                            option = if (voice.id == editor.voiceId) stringResource(R.string.cloud_tts_selected) else null,
                            onClick = { onIntent(CloudTtsIntent.SelectVoice(voice.id)) },
                        )
                    }
                } else {
                    item {
                        ClickableSettingItem(
                            title = editor.voiceName.ifBlank { editor.voiceId },
                            description = editor.voiceId,
                            option = stringResource(R.string.cloud_tts_current_voice),
                            onClick = {},
                        )
                    }
                }
                item {
                    if (selectedEngine?.remoteCatalog == true) {
                        TextButton(
                            onClick = { onIntent(CloudTtsIntent.DiscoverVoices) },
                            enabled = !state.discovering,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) { Text(stringResource(R.string.cloud_tts_refresh_catalog)) }
                    }
                    if (editor.editingVoiceId == null && editor.engineType == ReadAloudVoice.ENGINE_CLOUD) {
                        TextButton(
                            onClick = { manualVoiceIdVisible = !manualVoiceIdVisible },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) { Text(stringResource(if (manualVoiceIdVisible) R.string.cloud_tts_hide_manual_voice else R.string.cloud_tts_manual_voice_action)) }
                    }
                    if (manualVoiceIdVisible && editor.editingVoiceId == null &&
                        editor.engineType == ReadAloudVoice.ENGINE_CLOUD) {
                        Field(
                            editor.voiceId,
                            { update(editor.copy(voiceId = it.trim())) },
                            stringResource(R.string.cloud_tts_native_voice_id),
                        )
                        Text(
                            stringResource(R.string.cloud_tts_manual_voice_hint),
                            Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    Text(stringResource(R.string.cloud_tts_step_preset), Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                    Field(editor.voiceName, { update(editor.copy(voiceName = it)) }, stringResource(R.string.cloud_tts_preset_name))
                    if (editor.engineType == ReadAloudVoice.ENGINE_CLOUD) {
                        if (!selectedVoice?.styles.isNullOrEmpty()) {
                            Button(onClick = { styleMenu = true }, Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cloud_tts_style_value, editor.style.ifBlank { stringResource(R.string.cloud_tts_default) }))
                            }
                            DropdownMenu(styleMenu, { styleMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cloud_tts_default)) },
                                    onClick = { styleMenu = false; update(editor.copy(style = "")) },
                                )
                                selectedVoice.styles.forEach { style ->
                                    DropdownMenuItem(
                                        text = { Text(style) },
                                        onClick = { styleMenu = false; update(editor.copy(style = style)) },
                                    )
                                }
                            }
                        }
                        if (!selectedVoice?.roles.isNullOrEmpty()) {
                            Button(onClick = { roleMenu = true }, Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cloud_tts_role_value, editor.role.ifBlank { stringResource(R.string.cloud_tts_default) }))
                            }
                            DropdownMenu(roleMenu, { roleMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cloud_tts_default)) },
                                    onClick = { roleMenu = false; update(editor.copy(role = "")) },
                                )
                                selectedVoice.roles.forEach { role ->
                                    DropdownMenuItem(
                                        text = { Text(role) },
                                        onClick = { roleMenu = false; update(editor.copy(role = role)) },
                                    )
                                }
                            }
                        }
                        Field(editor.instructions, { update(editor.copy(instructions = it)) }, stringResource(R.string.cloud_tts_instructions))
                        SwitchSettingItem(
                            title = stringResource(R.string.cloud_tts_automatic_emotion),
                            description = stringResource(R.string.cloud_tts_automatic_emotion_summary),
                            checked = editor.automaticEmotion,
                            onCheckedChange = { update(editor.copy(automaticEmotion = it)) },
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.cloud_tts_character_personality),
                            description = stringResource(R.string.cloud_tts_character_personality_summary),
                            checked = editor.characterPersonality,
                            onCheckedChange = { update(editor.copy(characterPersonality = it)) },
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.cloud_tts_thought_performance),
                            description = stringResource(R.string.cloud_tts_thought_performance_summary),
                            checked = editor.thoughtPerformance,
                            onCheckedChange = { update(editor.copy(thoughtPerformance = it)) },
                        )
                        Field(editor.speed, { update(editor.copy(speed = it)) }, stringResource(R.string.cloud_tts_speed))
                        Field(editor.pitch, { update(editor.copy(pitch = it)) }, stringResource(R.string.cloud_tts_pitch))
                        Field(editor.volume, { update(editor.copy(volume = it)) }, stringResource(R.string.cloud_tts_volume))
                        Button(onClick = { formatMenu = true }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cloud_tts_audio_format, editor.format))
                        }
                        DropdownMenu(formatMenu, { formatMenu = false }) {
                            editor.formatOptions.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(format) },
                                    onClick = { formatMenu = false; update(editor.copy(format = format)) },
                                )
                            }
                        }
                    } else if (editor.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
                        Text(stringResource(R.string.cloud_tts_system_defaults_hint))
                        Field(editor.speed, { update(editor.copy(speed = it)) }, stringResource(R.string.cloud_tts_speed_multiplier))
                        Field(editor.pitch, { update(editor.copy(pitch = it)) }, stringResource(R.string.cloud_tts_pitch_multiplier))
                    }
                    if (editor.engineType == ReadAloudVoice.ENGINE_HTTP) {
                        Text(stringResource(R.string.cloud_tts_http_voice_hint))
                    } else {
                        Button(
                            onClick = { onIntent(CloudTtsIntent.Preview) },
                            enabled = editor.voiceId.isNotBlank() && !state.testing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(if (state.testing) R.string.cloud_tts_preview_generating else R.string.cloud_tts_preview)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
