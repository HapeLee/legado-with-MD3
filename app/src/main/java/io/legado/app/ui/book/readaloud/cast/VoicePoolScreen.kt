package io.legado.app.ui.book.readaloud.cast

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.readaloud.cast.VoiceAudition
import io.legado.app.help.readaloud.cast.VoicePoolTransfer
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionsRow
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.VoiceAuditionButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 声音池页（多角色规则 → 声音池）。
 *
 * 分组是可嵌套实体，和池一起拉平成一条列表渲染，所以整页共用一个
 * [rememberReorderableLazyListState]：长按任意一行即可拖动排序，池拖到哪个分组头
 * 下面即归入该分组（松手才落库，与书源/字典/标签规则页一致）。
 *
 * 本页所有文本/图标必须显式取色：AppScaffold 的默认 contentColor 由
 * `contentColorFor(MiuixTheme.colorScheme.surface)` 推导，两套主题不一致时结果为
 * Color.Unspecified，隐式取色的 Text/Icon 会画成黑色（深色模式下整页看不见）。
 */
@Composable
fun VoicePoolRouteScreen(
    onBackClick: () -> Unit,
    viewModel: MultiRoleRuleViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VoicePoolScreen(
        state = state,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBackClick = onBackClick,
    )
}

@Composable
fun VoicePoolScreen(
    state: MultiRoleRuleUiState,
    onIntent: (MultiRoleRuleIntent) -> Unit,
    effects: Flow<MultiRoleRuleEffect>,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val auditionText = remember(context) { VoiceAudition.defaultPreviewText(context) }
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(MultiRoleRuleIntent.MoveItem(from.index, to.index))
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    // 松手才落库。刚进页面时 isAnyItemDragging 也是 false，所以要先真的拖过一把。
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (reorderableState.isAnyItemDragging) {
            dragged = true
        } else if (dragged) {
            dragged = false
            onIntent(MultiRoleRuleIntent.SaveSortOrder)
        }
    }

    // 导入/导出弹窗只是入口，选完文件就把内容交回 VM，页面自己不留状态
    var showIoSheet by remember { mutableStateOf(false) }
    val ttsServerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { readDocument(context, it) { onIntent(MultiRoleRuleIntent.TtsServerFileRead(it)) } }
    }
    val poolsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { readDocument(context, it) { onIntent(MultiRoleRuleIntent.ImportPoolsText(it)) } }
    }
    // multitts 给的是 zip（里面才是 voice_pool.yaml），所以不走 readDocument 的整份按文本读
    val multiTtsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        runCatching {
            uri?.let { file ->
                context.contentResolver.openInputStream(file)
                    ?.use { VoicePoolTransfer.readMultiTtsYaml(it) }
            }
        }.getOrNull()?.let { onIntent(MultiRoleRuleIntent.MultiTtsFileRead(it)) }
    }
    val poolsExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { onIntent(MultiRoleRuleIntent.ExportPoolsTo(it)) }
    }
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is MultiRoleRuleEffect.ShowToast -> context.toastOnUi(effect.message)
                MultiRoleRuleEffect.OpenTtsServerPicker -> ttsServerPicker.launch(arrayOf("application/json", "*/*"))
                MultiRoleRuleEffect.OpenMultiTtsPicker -> multiTtsPicker.launch(
                    arrayOf("application/zip", "application/octet-stream", "text/plain", "*/*")
                )
                MultiRoleRuleEffect.OpenPoolsPicker -> poolsPicker.launch(arrayOf("application/json", "*/*"))
                is MultiRoleRuleEffect.SavePoolsTo -> poolsExporter.launch(effect.fileName)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentColor = LegadoTheme.colorScheme.onSurface,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.cast_voice_pool),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionsRow {
                        IconButton(onClick = { showIoSheet = true }) {
                            Icon(
                                Icons.Default.ImportExport,
                                contentDescription = stringResource(R.string.cast_pool_io),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(MultiRoleRuleIntent.SetSearch(!state.searchActive)) }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(MultiRoleRuleIntent.ShowCreateGroup("")) }) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = stringResource(R.string.cast_group_create),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(MultiRoleRuleIntent.ShowCreateDialog) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.add),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        CompositionLocalProvider(LocalContentColor provides LegadoTheme.colorScheme.onSurface) {
            PoolTreeList(
                view = state,
                wording = VoicePoolWording,
                actions = voicePoolActions(onIntent),
                listState = listState,
                reorderableState = reorderableState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                // 展开池后每条音色行尾一个试听按钮：不点进去就得先分配角色才听得到
                memberTrailing = { member ->
                    VoiceAuditionButton(
                        voiceId = member.voiceId,
                        text = auditionText,
                        label = "",
                    )
                },
            )
        }
    }

    state.editDialog?.let {
        PoolEditDialog(
            dialog = it,
            groups = state.groups,
            wording = VoicePoolWording,
            onSave = { editingId, name, groupId ->
                onIntent(MultiRoleRuleIntent.SavePool(editingId, name, groupId))
            },
            onDismiss = { onIntent(MultiRoleRuleIntent.DismissDialog) },
        )
    }
    state.deleteTarget?.let { pool ->
        PoolConfirmDialog(
            title = stringResource(R.string.cast_delete_pool),
            text = stringResource(R.string.cast_delete_pool_confirm, pool.name),
            onConfirm = { onIntent(MultiRoleRuleIntent.ConfirmDeletePool(pool.id)) },
            onDismiss = { onIntent(MultiRoleRuleIntent.DismissDelete) },
        )
    }
    state.groupDialog?.let {
        GroupEditDialog(
            dialog = it,
            onConfirm = { editingId, parentId, name ->
                onIntent(MultiRoleRuleIntent.ConfirmGroupDialog(editingId, parentId, name))
            },
            onDismiss = { onIntent(MultiRoleRuleIntent.DismissGroupDialog) },
        )
    }
    state.moveGroupTarget?.let { groupId ->
        MoveGroupDialog(
            target = state.groups.firstOrNull { it.id == groupId },
            groups = state.groups,
            onConfirm = { parent -> onIntent(MultiRoleRuleIntent.ConfirmMoveGroup(groupId, parent)) },
            onDismiss = { onIntent(MultiRoleRuleIntent.DismissMoveGroup) },
        )
    }
    state.deleteGroupTarget?.let { groupId ->
        PoolConfirmDialog(
            title = stringResource(R.string.cast_delete_group),
            text = stringResource(
                R.string.cast_delete_group_confirm,
                state.groups.firstOrNull { it.id == groupId }?.name.orEmpty(),
            ),
            onConfirm = { onIntent(MultiRoleRuleIntent.ConfirmDeleteGroup(groupId)) },
            onDismiss = { onIntent(MultiRoleRuleIntent.DismissDeleteGroup) },
        )
    }
    if (state.pickerPoolId != null) {
        MemberPickerDialog(
            pickerQuery = state.pickerQuery,
            candidates = state.pickerCandidates,
            wording = VoicePoolWording,
            onQuery = { onIntent(MultiRoleRuleIntent.UpdatePickerQuery(it)) },
            onToggle = { voiceId, checked ->
                onIntent(MultiRoleRuleIntent.TogglePickerSelection(voiceId, checked))
            },
            onSave = { onIntent(MultiRoleRuleIntent.SaveMemberPicker) },
            onDismiss = { onIntent(MultiRoleRuleIntent.DismissMemberPicker) },
        )
    }
    VoicePoolIoSheet(show = showIoSheet, onDismiss = { showIoSheet = false }, onIntent = onIntent)
    state.ttsServerPreview?.let { preview ->
        TtsServerImportDialog(preview, state.ttsServerEngine, onIntent)
    }
}

/** 公共部件的动作 → 本页意图。部件本身不认识 MultiRoleRuleIntent。 */
private fun voicePoolActions(onIntent: (MultiRoleRuleIntent) -> Unit) = CastPoolActions(
    onMove = { from, to -> onIntent(MultiRoleRuleIntent.MoveItem(from, to)) },
    onQuery = { onIntent(MultiRoleRuleIntent.UpdateSearch(it)) },
    onToggleGroup = { onIntent(MultiRoleRuleIntent.ToggleGroup(it)) },
    onGroupEnabled = { id, enabled -> onIntent(MultiRoleRuleIntent.SetGroupEnabled(id, enabled)) },
    onRenameGroup = { onIntent(MultiRoleRuleIntent.AskRenameGroup(it)) },
    onCreateGroup = { parent -> onIntent(MultiRoleRuleIntent.ShowCreateGroup(parent)) },
    onMoveGroup = { onIntent(MultiRoleRuleIntent.AskMoveGroup(it)) },
    onDeleteGroup = { onIntent(MultiRoleRuleIntent.AskDeleteGroup(it)) },
    onPoolEnabled = { id, enabled -> onIntent(MultiRoleRuleIntent.SetPoolEnabled(id, enabled)) },
    onEditPool = { onIntent(MultiRoleRuleIntent.ShowEditDialog(it)) },
    onDeletePool = { onIntent(MultiRoleRuleIntent.AskDeletePool(it)) },
    onExpandPool = { onIntent(MultiRoleRuleIntent.TogglePoolExpand(it)) },
    onAddMembers = { poolId -> onIntent(MultiRoleRuleIntent.ShowMemberPicker(poolId)) },
    onMemberToggle = { poolId, voiceId, enabled ->
        onIntent(MultiRoleRuleIntent.ToggleMemberEnabled(poolId, voiceId, enabled))
    },
    onMemberRemove = { poolId, voiceId ->
        onIntent(MultiRoleRuleIntent.RemoveMember(poolId, voiceId))
    },
    onMemberQuery = { poolId, query ->
        onIntent(MultiRoleRuleIntent.UpdateMemberQuery(poolId, query))
    },
)

/**
 * 导入/导出入口，布局沿用官方「备份」那个弹窗（一行两个卡片）。
 *
 * 上面一行是别的软件的格式（TTS Server / multitts），下面一行是我们自己的
 * 导入与导出——把它们放同一个窗口里，用户不用先猜该去哪个菜单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePoolIoSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onIntent: (MultiRoleRuleIntent) -> Unit,
) {
    fun send(intent: MultiRoleRuleIntent) {
        onDismiss()
        onIntent(intent)
    }
    OptionSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.cast_pool_io),
    ) {
        OptionCard(
            icon = Icons.Default.CloudDownload,
            text = stringResource(R.string.cast_import_tts_server),
            onClick = { send(MultiRoleRuleIntent.AskImportTtsServer) },
        )
        OptionCard(
            icon = Icons.Default.PhoneAndroid,
            text = stringResource(R.string.cast_import_multitts),
            onClick = { send(MultiRoleRuleIntent.AskImportMultiTts) },
        )
        OptionCard(
            icon = Icons.Default.FolderOpen,
            text = stringResource(R.string.cast_import_pools),
            onClick = { send(MultiRoleRuleIntent.AskImportPools) },
        )
        OptionCard(
            icon = Icons.Default.SaveAlt,
            text = stringResource(R.string.cast_export_pools),
            onClick = { send(MultiRoleRuleIntent.AskExportPools) },
        )
    }
}

/** 外部格式导入确认：先把「导什么、排除了什么」摆出来，落库要点确认。 */
@Composable
private fun TtsServerImportDialog(
    preview: TtsServerPreviewUi,
    engine: String,
    onIntent: (MultiRoleRuleIntent) -> Unit,
) {
    var engineText by remember(preview) { mutableStateOf(engine) }
    val multiTts = preview.source == CastImportSource.MultiTts
    AlertDialog(
        onDismissRequest = { onIntent(MultiRoleRuleIntent.DismissTtsServerImport) },
        title = {
            Text(
                stringResource(
                    if (multiTts) R.string.cast_import_multitts else R.string.cast_import_tts_server
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        R.string.cast_import_tts_server_counts,
                        preview.voiceCount,
                        preview.skippedEffects,
                        preview.skippedDisabled,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurface,
                )
                Text(
                    text = preview.poolNames.joinToString(stringResource(R.string.cast_list_separator)),
                    style = MaterialTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = engineText,
                    onValueChange = { onIntent(MultiRoleRuleIntent.UpdateTtsServerEngine(it)) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cast_import_tts_server_engine)) },
                    supportingText = {
                        Text(
                            text = stringResource(
                                if (multiTts) {
                                    R.string.cast_import_multitts_engine_hint
                                } else {
                                    R.string.cast_import_tts_server_engine_hint
                                }
                            ),
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(MultiRoleRuleIntent.ConfirmTtsServerImport) }) {
                Text(stringResource(R.string.cast_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(MultiRoleRuleIntent.DismissTtsServerImport) }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** 系统选择器回来的文件只有读成文本才能进 VM（VM 不碰 ContentResolver 以外的平台细节）。 */
private fun readDocument(context: Context, uri: Uri, onText: (String) -> Unit) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
    }.getOrNull()?.let(onText)
}
