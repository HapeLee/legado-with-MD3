package io.legado.app.ui.book.readaloud.cast

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.help.readaloud.effect.VoiceEffectAudio
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionsRow
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

/**
 * 变声器管理页（朗读规则 → 变声器）。
 *
 * 四个内置预设（魔王/哥布林/机器人/心声混响）是免费方案：音高与语速走 Media3 自带的
 * Sonic 变调，混响与金属感走平台 AudioEffect，没有引入第三方引擎。删干净了可以一键恢复，
 * 也可以把别人分享的一组预设 JSON 导进来（同名覆盖）。
 *
 * 但**混响/金属感只在有我们持有的播放器时挂得上**：系统 TTS 直读路径（[ReadAloud.supportsSessionAudioEffect]
 * 为 false）没有会话号，那一层会被整个跳过，所以列表要把「当前引擎不生效」标出来。
 */
@Composable
fun VoiceEffectRouteScreen(
    onBackClick: () -> Unit,
    viewModel: VoiceEffectViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VoiceEffectScreen(
        state = state,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBackClick = onBackClick,
    )
}

@Composable
fun VoiceEffectScreen(
    state: VoiceEffectUiState,
    onIntent: (VoiceEffectIntent) -> Unit,
    effects: Flow<VoiceEffectEffect>,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            readEffectDocument(context, it) { onIntent(VoiceEffectIntent.ImportFrom(it)) }
        }
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { onIntent(VoiceEffectIntent.ExportTo(it)) }
    }
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is VoiceEffectEffect.ShowToast -> context.toastOnUi(effect.message)
                VoiceEffectEffect.OpenImporter -> importer.launch(arrayOf("application/json", "*/*"))
                is VoiceEffectEffect.SaveExporter -> exporter.launch(effect.fileName)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Miuix 引擎分支不套 contentColor，隐式取色在深色下会发黑
        contentColor = LegadoTheme.colorScheme.onSurface,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.voice_effect),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionsRow {
                        IconButton(onClick = { onIntent(VoiceEffectIntent.ShowIoSheet) }) {
                            Icon(
                                Icons.Default.ImportExport,
                                contentDescription = stringResource(R.string.cast_pool_io),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(VoiceEffectIntent.ShowCreate) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.voice_effect_create),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = adaptiveContentPadding(top = 0.dp, bottom = 120.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.voice_effect_summary),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.rows.any { it.sessionLayerDropped }) {
                item {
                    Text(
                        text = stringResource(R.string.voice_effect_session_dropped_hint),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            items(state.rows.size, key = { state.rows[it].preset.name }) { index ->
                val row = state.rows[index]
                ListItem(
                    headlineContent = { Text(row.preset.name) },
                    supportingContent = {
                        Text(
                            text = row.summary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = row.preset.enabled,
                                onCheckedChange = {
                                    onIntent(VoiceEffectIntent.Toggle(row.preset.name, it))
                                },
                            )
                            IconButton(onClick = { onIntent(VoiceEffectIntent.ShowDelete(row.preset)) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                )
                            }
                        }
                    },
                    // 整行点开编辑，不需要额外的按钮
                    modifier = Modifier.clickable {
                        onIntent(VoiceEffectIntent.ShowEdit(row.preset))
                    },
                )
            }
        }
    }

    OptionSheet(
        show = state.showIoSheet,
        onDismissRequest = { onIntent(VoiceEffectIntent.DismissIoSheet) },
        title = stringResource(R.string.cast_pool_io),
    ) {
        OptionCard(
            icon = Icons.Default.CloudDownload,
            text = stringResource(R.string.voice_effect_import),
            onClick = {
                onIntent(VoiceEffectIntent.DismissIoSheet)
                onIntent(VoiceEffectIntent.OpenImporter)
            },
        )
        OptionCard(
            icon = Icons.Default.SaveAlt,
            text = stringResource(R.string.voice_effect_export),
            onClick = {
                onIntent(VoiceEffectIntent.DismissIoSheet)
                onIntent(VoiceEffectIntent.RequestExporter)
            },
        )
        OptionCard(
            icon = Icons.Default.Restore,
            text = stringResource(R.string.voice_effect_restore),
            onClick = {
                onIntent(VoiceEffectIntent.DismissIoSheet)
                onIntent(VoiceEffectIntent.RestoreBuiltins)
            },
        )
    }

    state.editTarget?.let { target ->
        VoiceEffectEditDialog(
            initial = target,
            isNew = state.isNew,
            onSave = { onIntent(VoiceEffectIntent.Save(it)) },
            onDismiss = { onIntent(VoiceEffectIntent.DismissEdit) },
        )
    }

    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { onIntent(VoiceEffectIntent.DismissDelete) },
            title = { Text(stringResource(R.string.voice_effect_delete)) },
            text = { Text(stringResource(R.string.voice_effect_delete_tip)) },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(VoiceEffectIntent.Delete(target.name))
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(VoiceEffectIntent.DismissDelete) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * 编辑一个预设：音高/语速是倍率，混响是平台预设号，金属感是中频带通开关。
 *
 * 音高与语速各自有上下限（见 [VoiceEffectAudio]），滑过头只会到边界，不会把声音推成噪音。
 */
@Composable
private fun VoiceEffectEditDialog(
    initial: VoiceEffectPreset,
    isNew: Boolean,
    onSave: (VoiceEffectPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val reverbNames = listOf(
        stringResource(R.string.voice_effect_reverb_none),
        stringResource(R.string.voice_effect_reverb_small_room),
        stringResource(R.string.voice_effect_reverb_medium_room),
        stringResource(R.string.voice_effect_reverb_large_room),
        stringResource(R.string.voice_effect_reverb_medium_hall),
        stringResource(R.string.voice_effect_reverb_large_hall),
        stringResource(R.string.voice_effect_reverb_plate),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.voice_effect_create else R.string.voice_effect_edit,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.voice_effect_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EffectSliderRow(
                    label = stringResource(R.string.voice_effect_pitch),
                    value = draft.pitch,
                    valueRange = VoiceEffectAudio.MIN_PITCH..VoiceEffectAudio.MAX_PITCH,
                    steps = 20,
                    onValueChange = { draft = draft.copy(pitch = it) },
                )
                EffectSliderRow(
                    label = stringResource(R.string.voice_effect_speed),
                    value = draft.speed,
                    valueRange = VoiceEffectAudio.MIN_SPEED..VoiceEffectAudio.MAX_SPEED,
                    steps = 15,
                    onValueChange = { draft = draft.copy(speed = it) },
                )
                Text(
                    text = reverbNames.getOrElse(draft.reverbPreset) {
                        reverbNames[VoiceEffectStore.REVERB_NONE]
                    }.let { stringResource(R.string.voice_effect_reverb) + " · " + it },
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = draft.reverbPreset.toFloat(),
                    onValueChange = {
                        draft = draft.copy(reverbPreset = it.toInt().coerceIn(0, 6))
                    },
                    valueRange = 0f..6f,
                    steps = 5,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.metal,
                        onCheckedChange = { draft = draft.copy(metal = it) },
                    )
                    Text(stringResource(R.string.voice_effect_metal))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun EffectSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp),
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "×" + String.format(java.util.Locale.getDefault(), "%.2f", value),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp),
        )
    }
}

private fun readEffectDocument(context: Context, uri: Uri, onText: (String) -> Unit) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
    }.getOrNull()?.let(onText)
}
