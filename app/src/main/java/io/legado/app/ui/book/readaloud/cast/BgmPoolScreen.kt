package io.legado.app.ui.book.readaloud.cast

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.NormalCard
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
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 背景音乐池页。
 *
 * 交互与角色声音池完全一致（分组、新建/改名/删除池、长按拖动排序、池内展开挑成员），
 * 列表与对话框都用 [CastPoolWidgets] 里那套公共部件；本页特有的只有下面的「配乐库」
 * （导入进来的音频文件）和右上角的淡入淡出设置。
 *
 * 文本/图标一律显式取色，理由和角色声音池页一样。
 */
@Composable
fun BgmPoolRouteScreen(
    onBackClick: () -> Unit,
    viewModel: BgmPoolViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BgmPoolScreen(
        state = state,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBackClick = onBackClick,
    )
}

@Composable
fun BgmPoolScreen(
    state: BgmPoolUiState,
    onIntent: (BgmPoolIntent) -> Unit,
    effects: Flow<BgmPoolEffect>,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(BgmPoolIntent.MoveItem(from.index, to.index))
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    // 松手才落库。刚进页面时 isAnyItemDragging 也是 false，所以要先真的拖过一把。
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (reorderableState.isAnyItemDragging) {
            dragged = true
        } else if (dragged) {
            dragged = false
            onIntent(BgmPoolIntent.SaveSortOrder)
        }
    }

    var showFadeDialog by remember { mutableStateOf(false) }
    // 导入/导出弹窗只是入口，选完文件就把 uri 交回 VM，页面自己不留状态（与角色声音池页一致）
    var showIoSheet by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onIntent(BgmPoolIntent.FilesPicked(uris))
    }
    // 音乐包是一个 zip（清单 + 音频），所以走单选，不像配乐库那样多选音频文件
    val packagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onIntent(BgmPoolIntent.ImportPackagePicked(it)) }
    }
    val packageExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { onIntent(BgmPoolIntent.ExportPackageTo(it)) }
    }
    val player = remember {
        MediaPlayer().apply {
            setOnCompletionListener { onIntent(BgmPoolIntent.PlayFinished) }
            setOnErrorListener { _, _, _ ->
                onIntent(BgmPoolIntent.PlayFinished)
                true
            }
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val playFailedMsg = stringResource(R.string.cast_bgm_play_failed)
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is BgmPoolEffect.ShowToast -> context.toastOnUi(effect.message)
                BgmPoolEffect.OpenFilePicker -> picker.launch(arrayOf("audio/*"))
                BgmPoolEffect.OpenPackagePicker -> packagePicker.launch(
                    arrayOf("application/zip", "application/octet-stream", "*/*")
                )
                is BgmPoolEffect.SavePackageTo -> packageExporter.launch(effect.fileName)
                BgmPoolEffect.Stop -> runCatching {
                    if (player.isPlaying) player.stop()
                    player.reset()
                }
                is BgmPoolEffect.Play -> runCatching {
                    if (player.isPlaying) player.stop()
                    player.reset()
                    player.setDataSource(effect.path)
                    player.prepare()
                    // 试听用这条配乐自己的音量，不然调完音量在这页听不出差别
                    player.setVolume(effect.volume, effect.volume)
                    player.start()
                }.onFailure {
                    onIntent(BgmPoolIntent.PlayFinished)
                    context.toastOnUi(playFailedMsg)
                }
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentColor = LegadoTheme.colorScheme.onSurface,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.cast_bgm_pool),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionsRow {
                        IconButton(onClick = { showIoSheet = true }) {
                            Icon(
                                Icons.Default.ImportExport,
                                contentDescription = stringResource(R.string.cast_bgm_io),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { showFadeDialog = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cast_bgm_fade),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(BgmPoolIntent.SetSearch(!state.searchActive)) }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(BgmPoolIntent.ShowCreateGroup("")) }) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = stringResource(R.string.cast_group_create),
                                tint = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onIntent(BgmPoolIntent.ShowCreateDialog) }) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                BgmLibraryCard(state, onIntent)
                PoolTreeList(
                    view = state,
                    wording = BgmPoolWording,
                    actions = bgmPoolActions(onIntent),
                    listState = listState,
                    reorderableState = reorderableState,
                    modifier = Modifier.fillMaxWidth(),
                    // 池内成员行尾多一个试听按钮：背景音乐得先听一下才知道配的是哪首
                    memberTrailing = { member ->
                        val track = state.tracks.firstOrNull { it.id == member.voiceId }
                        IconButton(
                            onClick = { onIntent(BgmPoolIntent.PlayToggle(member.voiceId)) },
                            enabled = track != null && !track.missing,
                        ) {
                            Icon(
                                imageVector = if (state.playingId == member.voiceId) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = stringResource(
                                    if (state.playingId == member.voiceId) {
                                        R.string.cast_bgm_stop
                                    } else {
                                        R.string.cast_bgm_play
                                    }
                                ),
                                tint = if (state.playingId == member.voiceId) {
                                    LegadoTheme.colorScheme.primary
                                } else {
                                    LegadoTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    state.editDialog?.let {
        PoolEditDialog(
            dialog = it,
            groups = state.groups,
            wording = BgmPoolWording,
            onSave = { editingId, name, groupId ->
                onIntent(BgmPoolIntent.SavePool(editingId, name, groupId))
            },
            onDismiss = { onIntent(BgmPoolIntent.DismissDialog) },
        )
    }
    state.deleteTarget?.let { pool ->
        PoolConfirmDialog(
            title = stringResource(R.string.cast_delete_bgm_pool),
            text = stringResource(R.string.cast_delete_bgm_pool_confirm, pool.name),
            onConfirm = { onIntent(BgmPoolIntent.ConfirmDeletePool(pool.id)) },
            onDismiss = { onIntent(BgmPoolIntent.DismissDelete) },
        )
    }
    state.groupDialog?.let {
        GroupEditDialog(
            dialog = it,
            onConfirm = { editingId, parentId, name ->
                onIntent(BgmPoolIntent.ConfirmGroupDialog(editingId, parentId, name))
            },
            onDismiss = { onIntent(BgmPoolIntent.DismissGroupDialog) },
        )
    }
    state.moveGroupTarget?.let { groupId ->
        MoveGroupDialog(
            target = state.groups.firstOrNull { it.id == groupId },
            groups = state.groups,
            onConfirm = { parent -> onIntent(BgmPoolIntent.ConfirmMoveGroup(groupId, parent)) },
            onDismiss = { onIntent(BgmPoolIntent.DismissMoveGroup) },
        )
    }
    state.deleteGroupTarget?.let { groupId ->
        PoolConfirmDialog(
            title = stringResource(R.string.cast_delete_group),
            text = stringResource(
                R.string.cast_delete_group_confirm,
                state.groups.firstOrNull { it.id == groupId }?.name.orEmpty(),
            ),
            onConfirm = { onIntent(BgmPoolIntent.ConfirmDeleteGroup(groupId)) },
            onDismiss = { onIntent(BgmPoolIntent.DismissDeleteGroup) },
        )
    }
    if (state.pickerPoolId != null) {
        MemberPickerDialog(
            pickerQuery = state.pickerQuery,
            candidates = state.pickerCandidates,
            wording = BgmPoolWording,
            onQuery = { onIntent(BgmPoolIntent.UpdatePickerQuery(it)) },
            onToggle = { trackId, checked ->
                onIntent(BgmPoolIntent.TogglePickerSelection(trackId, checked))
            },
            onSave = { onIntent(BgmPoolIntent.SaveMemberPicker) },
            onDismiss = { onIntent(BgmPoolIntent.DismissMemberPicker) },
        )
    }
    state.trackDeleteTarget?.let { target ->
        PoolConfirmDialog(
            title = stringResource(R.string.cast_bgm_delete),
            text = stringResource(R.string.cast_bgm_delete_confirm, target.name),
            onConfirm = { onIntent(BgmPoolIntent.ConfirmDeleteTrack(target.id)) },
            onDismiss = { onIntent(BgmPoolIntent.DismissTrackDelete) },
        )
    }
    state.trackVolumeTarget?.let { target ->
        TrackVolumeDialog(
            track = target,
            onConfirm = { value -> onIntent(BgmPoolIntent.SetTrackVolume(target.id, value)) },
            onDismiss = { onIntent(BgmPoolIntent.DismissTrackVolume) },
        )
    }
    state.trackRenameTarget?.let { target ->
        TrackRenameDialog(
            track = target,
            onConfirm = { name -> onIntent(BgmPoolIntent.ConfirmRenameTrack(target.id, name)) },
            onDismiss = { onIntent(BgmPoolIntent.DismissTrackRename) },
        )
    }
    if (showFadeDialog) {
        FadeSettingsDialog(state, onIntent) { showFadeDialog = false }
    }
    BgmPoolIoSheet(show = showIoSheet, onDismiss = { showIoSheet = false }, onIntent = onIntent)
}

/**
 * 导入/导出入口：一张卡导包、一张卡导出，布局与角色声音池那个弹窗一致（一行两个卡片）。
 *
 * 导的是整个背景音乐池连音频一起（zip：`manifest.json` + `audio/<文件>`），
 * 包内格式与「路径重写成接收端自己的副本」都在
 * [io.legado.app.help.readaloud.cast.BgmPoolTransfer]，这里只负责选文件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BgmPoolIoSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onIntent: (BgmPoolIntent) -> Unit,
) {
    fun send(intent: BgmPoolIntent) {
        onDismiss()
        onIntent(intent)
    }
    OptionSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.cast_bgm_io),
    ) {
        OptionCard(
            icon = Icons.Default.FolderOpen,
            text = stringResource(R.string.cast_bgm_import_package),
            onClick = { send(BgmPoolIntent.AskImportPackage) },
        )
        OptionCard(
            icon = Icons.Default.SaveAlt,
            text = stringResource(R.string.cast_bgm_export_package),
            onClick = { send(BgmPoolIntent.AskExportPackage) },
        )
    }
}

/** 公共部件的动作 → 本页意图（与角色声音池那份一一对应）。 */
private fun bgmPoolActions(onIntent: (BgmPoolIntent) -> Unit) = CastPoolActions(
    onMove = { from, to -> onIntent(BgmPoolIntent.MoveItem(from, to)) },
    onQuery = { onIntent(BgmPoolIntent.UpdateSearch(it)) },
    onToggleGroup = { onIntent(BgmPoolIntent.ToggleGroup(it)) },
    onGroupEnabled = { id, enabled -> onIntent(BgmPoolIntent.SetGroupEnabled(id, enabled)) },
    onRenameGroup = { onIntent(BgmPoolIntent.AskRenameGroup(it)) },
    onCreateGroup = { parent -> onIntent(BgmPoolIntent.ShowCreateGroup(parent)) },
    onMoveGroup = { onIntent(BgmPoolIntent.AskMoveGroup(it)) },
    onDeleteGroup = { onIntent(BgmPoolIntent.AskDeleteGroup(it)) },
    onPoolEnabled = { id, enabled -> onIntent(BgmPoolIntent.SetPoolEnabled(id, enabled)) },
    onEditPool = { onIntent(BgmPoolIntent.ShowEditDialog(it)) },
    onDeletePool = { onIntent(BgmPoolIntent.AskDeletePool(it)) },
    onExpandPool = { onIntent(BgmPoolIntent.TogglePoolExpand(it)) },
    onAddMembers = { poolId -> onIntent(BgmPoolIntent.ShowMemberPicker(poolId)) },
    onMemberToggle = { poolId, trackId, enabled ->
        onIntent(BgmPoolIntent.ToggleMemberEnabled(poolId, trackId, enabled))
    },
    onMemberRemove = { poolId, trackId -> onIntent(BgmPoolIntent.RemoveMember(poolId, trackId)) },
    onMemberQuery = { poolId, query -> onIntent(BgmPoolIntent.UpdateMemberQuery(poolId, query)) },
)

/**
 * 配乐库：导入进来的音频文件。池的成员只能从这里挑，所以把它放在列表最上面，
 * 展开就能导入/开关/试听/删除，收起时只剩一行标题。
 */
@Composable
private fun BgmLibraryCard(
    state: BgmPoolUiState,
    onIntent: (BgmPoolIntent) -> Unit,
) {
    val expanded = state.libraryExpanded
    NormalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 6.dp),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onIntent(BgmPoolIntent.ToggleLibrary) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cast_bgm_library),
                    style = MaterialTheme.typography.titleSmall,
                    color = LegadoTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.cast_bgm_library_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "(${state.tracks.count { it.enabled }}/${state.tracks.size})",
                style = MaterialTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = { onIntent(BgmPoolIntent.AskImport) }) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = stringResource(R.string.cast_bgm_import),
                    tint = LegadoTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 展开/收起整段曲目列表要有过渡，硬蹦 hardest 的就在这一步
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(140)) + expandVertically(tween(200)),
            exit = fadeOut(tween(110)) + shrinkVertically(tween(170)),
        ) {
            if (state.tracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.cast_bgm_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.tracks.forEach { track ->
                    BgmTrackRow(
                        track = track,
                        playing = state.playingId == track.id,
                        onIntent = onIntent,
                    )
                }
            }
            Spacer(Modifier.width(1.dp).padding(bottom = 6.dp))
        }
    }
}

/** 一条导入配乐的音量：只压这一条，池里所有场景都跟着变（试听也用它，改完立刻听得出）。 */
@Composable
private fun TrackVolumeDialog(
    track: BgmTrackUi,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(track.id) { mutableStateOf(track.volume) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast_bgm_track_volume)) },
        text = {
            Column {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0f..1f,
                        steps = 19,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(value * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.cast_bgm_track_volume_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * 改一条导入配乐的显示名。
 *
 * 只改显示名：磁盘上的副本文件不动（`path` 保持原样），所以改名不会让已分配的
 * 场景丢失音频。场景标记是按 `poolName`+`trackName` 引用的，库里那条名字改了标记
 * 也要跟着改，这一致性由 BgmPoolStore.renameTrack 保证；空名/重名会被判非法，
 * 弹窗这里直接把确定按钮禁掉，不给按出歧义的机会。
 */
@Composable
private fun TrackRenameDialog(
    track: BgmTrackUi,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(track.id) { mutableStateOf(track.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast_bgm_rename_track)) },
        text = {
            Column {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.cast_bgm_track_name),
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.cast_bgm_rename_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && name.trim() != track.name,
                onClick = { onConfirm(name) },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun BgmTrackRow(
    track: BgmTrackUi,
    playing: Boolean,
    onIntent: (BgmPoolIntent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !track.missing) { onIntent(BgmPoolIntent.PlayToggle(track.id)) }
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (track.missing) stringResource(R.string.cast_bgm_missing_file) else track.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = track.enabled,
            onCheckedChange = { onIntent(BgmPoolIntent.SetTrackEnabled(track.id, it)) },
        )
        IconButton(
            onClick = { onIntent(BgmPoolIntent.PlayToggle(track.id)) },
            enabled = !track.missing,
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.cast_bgm_stop else R.string.cast_bgm_play
                ),
                tint = if (playing) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(
            onClick = { onIntent(BgmPoolIntent.AskTrackVolume(track.id)) },
            enabled = !track.missing,
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = stringResource(R.string.cast_bgm_track_volume),
                tint = if (track.volume < 0.999f) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = { onIntent(BgmPoolIntent.AskRenameTrack(track.id)) }) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.cast_bgm_rename_track),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onIntent(BgmPoolIntent.AskDeleteTrack(track.id)) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 右上角设置：切场景时的音量渐变。
 *
 * 只开缓入 = 新配乐淡入、旧的直接停；只开缓出 = 旧的淡出、新的立刻起；两个都开就是
 * 交叉淡变。用户要求「如果只开缓出就只做淡出」，所以两个开关各自独立、互不牵连。
 */
@Composable
private fun FadeSettingsDialog(
    state: BgmPoolUiState,
    onIntent: (BgmPoolIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast_bgm_fade)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FadeSwitchRow(
                    title = stringResource(R.string.cast_bgm_fade_in),
                    summary = stringResource(R.string.cast_bgm_fade_in_summary),
                    checked = state.fadeIn,
                    onChange = { onIntent(BgmPoolIntent.SetFadeIn(it)) },
                )
                FadeSwitchRow(
                    title = stringResource(R.string.cast_bgm_fade_out),
                    summary = stringResource(R.string.cast_bgm_fade_out_summary),
                    checked = state.fadeOut,
                    onChange = { onIntent(BgmPoolIntent.SetFadeOut(it)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
private fun FadeSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = LegadoTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
