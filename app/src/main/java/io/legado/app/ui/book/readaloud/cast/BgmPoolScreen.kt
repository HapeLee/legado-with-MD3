package io.legado.app.ui.book.readaloud.cast

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
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
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.tabRow.rememberTabPagerState
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
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
    // tab 是唯一事实源（在 VM 里）：点 tab / 滑页都回到同一个 SelectTab，见 rememberTabPagerState
    val pagerState = rememberTabPagerState(
        selectedIndex = state.selectedTab.ordinal,
        pageCount = BgmPoolTab.entries.size,
        onPageSelected = { page ->
            BgmPoolTab.entries.getOrNull(page)
                ?.let { onIntent(BgmPoolIntent.SelectTab(it)) }
        },
    )
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
                // 顶栏已经用 TopBarActionsRow 包好了，这里只放按钮本身
                actions = {
                    TopBarActionButton(
                        onClick = { showIoSheet = true },
                        imageVector = Icons.Default.ImportExport,
                        contentDescription = stringResource(R.string.cast_bgm_io),
                    )
                    TopBarActionButton(
                        onClick = { showFadeDialog = true },
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.cast_bgm_fade),
                    )
                    // 搜索与新建分组只服务音乐池那一页：配乐库没有分组，也不按池筛选
                    if (state.selectedTab == BgmPoolTab.Pools) {
                        TopBarActionButton(
                            onClick = { onIntent(BgmPoolIntent.SetSearch(!state.searchActive)) },
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                        TopBarActionButton(
                            onClick = { onIntent(BgmPoolIntent.ShowCreateGroup("")) },
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = stringResource(R.string.cast_group_create),
                        )
                    }
                },
                bottomContent = {
                    AppTabRow(
                        tabTitles = listOf(
                            stringResource(R.string.cast_bgm_pool),
                            stringResource(R.string.cast_bgm_library),
                        ),
                        selectedTabIndex = state.selectedTab.ordinal,
                        // 点 tab 只派发意图；滚页交给上面那个 LaunchedEffect，保持一条同步路径
                        onTabSelected = { page ->
                            onIntent(BgmPoolIntent.SelectTab(BgmPoolTab.entries[page]))
                        },
                        isScrollable = false,
                    )
                },
            )
        },
        floatingActionButton = {
            // 新建池与导入音频都是「这一页的主动作」，放右下角，比顶栏那颗加号好按
            when (state.selectedTab) {
                BgmPoolTab.Pools -> AppFloatingActionButton(
                    onClick = { onIntent(BgmPoolIntent.ShowCreateDialog) },
                    icon = Icons.Default.Add,
                    tooltipText = stringResource(R.string.cast_create_bgm_pool),
                )

                BgmPoolTab.Library -> AppFloatingActionButton(
                    onClick = { onIntent(BgmPoolIntent.AskImport) },
                    icon = Icons.Default.CloudDownload,
                    tooltipText = stringResource(R.string.cast_bgm_import),
                )
            }
        },
    ) { paddingValues ->
        CompositionLocalProvider(LocalContentColor provides LegadoTheme.colorScheme.onSurface) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) { page ->
                when (BgmPoolTab.entries[page]) {
                    BgmPoolTab.Pools -> PoolTreeList(
                        view = state,
                        wording = BgmPoolWording,
                        actions = bgmPoolActions(onIntent),
                        listState = listState,
                        reorderableState = reorderableState,
                        modifier = Modifier.fillMaxSize(),
                        // 池内成员行尾多一个试听按钮：背景音乐得先听一下才知道配的是哪首
                        memberTrailing = { member ->
                            val track = state.tracks.firstOrNull { it.id == member.voiceId }
                            val playing = state.playingId == member.voiceId
                            SmallPlainButton(
                                onClick = { onIntent(BgmPoolIntent.PlayToggle(member.voiceId)) },
                                enabled = track != null && !track.missing,
                                selected = playing,
                                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (playing) R.string.cast_bgm_stop else R.string.cast_bgm_play
                                ),
                            )
                        },
                    )

                    BgmPoolTab.Library -> BgmTrackLibrary(
                        state = state,
                        onIntent = onIntent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    PoolEditDialog(
        dialog = state.editDialog,
        groups = state.groups,
        wording = BgmPoolWording,
        onSave = { editingId, name, groupId ->
            onIntent(BgmPoolIntent.SavePool(editingId, name, groupId))
        },
        onDismiss = { onIntent(BgmPoolIntent.DismissDialog) },
    )
    // 下面这批弹窗一律「常驻组合 + show/data」：miuix 的窗口靠 show 驱动退场动画，
    // 条件组合会在关闭那一刻把子树摘掉，动画与草稿一起丢
    PoolConfirmDialog(
        show = state.deleteTarget != null,
        title = stringResource(R.string.cast_delete_bgm_pool),
        text = state.deleteTarget?.let {
            stringResource(R.string.cast_delete_bgm_pool_confirm, it.name)
        }.orEmpty(),
        onConfirm = { onIntent(BgmPoolIntent.ConfirmDeletePool(state.deleteTarget?.id.orEmpty())) },
        onDismiss = { onIntent(BgmPoolIntent.DismissDelete) },
    )
    GroupEditDialog(
        dialog = state.groupDialog,
        onConfirm = { editingId, parentId, name ->
            onIntent(BgmPoolIntent.ConfirmGroupDialog(editingId, parentId, name))
        },
        onDismiss = { onIntent(BgmPoolIntent.DismissGroupDialog) },
    )
    MoveGroupDialog(
        target = state.moveGroupTarget?.let { id -> state.groups.firstOrNull { it.id == id } },
        groups = state.groups,
        onConfirm = { parent ->
            onIntent(BgmPoolIntent.ConfirmMoveGroup(state.moveGroupTarget.orEmpty(), parent))
        },
        onDismiss = { onIntent(BgmPoolIntent.DismissMoveGroup) },
    )
    PoolConfirmDialog(
        show = state.deleteGroupTarget != null,
        title = stringResource(R.string.cast_delete_group),
        text = state.deleteGroupTarget?.let { id ->
            stringResource(
                R.string.cast_delete_group_confirm,
                state.groups.firstOrNull { it.id == id }?.name.orEmpty(),
            )
        }.orEmpty(),
        onConfirm = { onIntent(BgmPoolIntent.ConfirmDeleteGroup(state.deleteGroupTarget.orEmpty())) },
        onDismiss = { onIntent(BgmPoolIntent.DismissDeleteGroup) },
    )
    MemberPickerDialog(
        show = state.pickerPoolId != null,
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
    PoolConfirmDialog(
        show = state.trackDeleteTarget != null,
        title = stringResource(R.string.cast_bgm_delete),
        text = state.trackDeleteTarget?.let {
            stringResource(R.string.cast_bgm_delete_confirm, it.name)
        }.orEmpty(),
        onConfirm = { onIntent(BgmPoolIntent.ConfirmDeleteTrack(state.trackDeleteTarget?.id.orEmpty())) },
        onDismiss = { onIntent(BgmPoolIntent.DismissTrackDelete) },
    )
    TrackVolumeDialog(
        show = state.trackVolumeTarget != null,
        track = state.trackVolumeTarget,
        onConfirm = { value ->
            onIntent(BgmPoolIntent.SetTrackVolume(state.trackVolumeTarget?.id.orEmpty(), value))
        },
        onDismiss = { onIntent(BgmPoolIntent.DismissTrackVolume) },
    )
    TrackRenameDialog(
        show = state.trackRenameTarget != null,
        track = state.trackRenameTarget,
        onConfirm = { name ->
            onIntent(BgmPoolIntent.ConfirmRenameTrack(state.trackRenameTarget?.id.orEmpty(), name))
        },
        onDismiss = { onIntent(BgmPoolIntent.DismissTrackRename) },
    )
    FadeSettingsDialog(state, onIntent, show = showFadeDialog) { showFadeDialog = false }
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
 * 配乐库页：导入进来的音频文件。池的成员只能从这里挑，所以它是独立一页
 * （不再挂在池列表顶上）；开关/试听/音量/改名/删除都在行内，导入走右下角那颗 FAB。
 */
@Composable
private fun BgmTrackLibrary(
    state: BgmPoolUiState,
    onIntent: (BgmPoolIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.tracks.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            EmptyMessage(
                messageResId = R.string.cast_bgm_empty,
                buttonText = stringResource(R.string.cast_bgm_import),
                buttonImageVector = Icons.Default.CloudDownload,
                onButtonClick = { onIntent(BgmPoolIntent.AskImport) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = adaptiveContentPadding(top = 8.dp, bottom = 96.dp),
    ) {
        item {
            AppText(
                text = stringResource(R.string.cast_bgm_library_summary),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(state.tracks, key = { it.id }) { track ->
            BgmTrackRow(
                track = track,
                playing = state.playingId == track.id,
                onIntent = onIntent,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/** 一条导入配乐的音量：只压这一条，池里所有场景都跟着变（试听也用它，改完立刻听得出）。 */
@Composable
private fun TrackVolumeDialog(
    show: Boolean,
    track: BgmTrackUi?,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    // 常驻组合 + show：miuix 的窗口靠 show驱动退场动画，条件组合会直接丢动画
    var cached by remember { mutableStateOf(track) }
    if (track != null) cached = track
    val current = cached ?: return
    var value by remember(current.id) { mutableStateOf(current.volume) }
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.cast_bgm_track_volume),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onConfirm(value) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppText(
                    text = current.name,
                    style = LegadoTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TinySliderSettingItem(
                    title = stringResource(R.string.cast_bgm_track_volume),
                    value = value,
                    valueRange = 0f..1f,
                    steps = 19,
                    showDecimal = false,
                    stepSize = 0.05f,
                    valueFormat = { "${(it * 100).toInt()}%" },
                    description = stringResource(R.string.cast_bgm_track_volume_hint),
                    onValueChange = { value = it },
                )
            }
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
    show: Boolean,
    track: BgmTrackUi?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 常驻组合 + show：miuix 的窗口靠 show 驱动退场动画，条件组合会直接丢动画
    var cached by remember { mutableStateOf(track) }
    if (track != null) cached = track
    val current = cached ?: return
    var name by remember(current.id) { mutableStateOf(current.name) }
    val trimmed = name.trim()
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.cast_bgm_rename_track),
        confirmText = stringResource(R.string.ok),
        // 空名/重名（库里有同名那条）不给出按的机会：AppAlertDialog 的 confirmEnabled
        // 直接把确定按钮置灰，而不是点了才弹一句「名字为空，或已经有同名的配乐」
        confirmEnabled = trimmed.isNotBlank() && trimmed != current.name,
        onConfirm = { onConfirm(name) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.cast_bgm_track_name),
                    singleLine = true,
                    // 弹层里的输入框用 onSheetContent 底色（半透明），与弹层底色分层
                    backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                )
                AppText(
                    text = stringResource(R.string.cast_bgm_rename_hint),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun BgmTrackRow(
    track: BgmTrackUi,
    playing: Boolean,
    onIntent: (BgmPoolIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // tiny 系列：与预设行、朗读设置里的开关项同一套排布；
    // 行点击仍是「试听/停止」，开关与各动作依次排在尾部
    TinySettingItem(
        title = track.name,
        description = if (track.missing) {
            stringResource(R.string.cast_bgm_missing_file)
        } else {
            track.subtitle
        },
        color = Color.Transparent,
        enabled = !track.missing,
        modifier = modifier.fillMaxWidth(),
        trailingContent = {
            TinySwitch(
                checked = track.enabled,
                onCheckedChange = { onIntent(BgmPoolIntent.SetTrackEnabled(track.id, it)) },
                enabled = !track.missing,
            )
            SmallPlainButton(
                onClick = { onIntent(BgmPoolIntent.PlayToggle(track.id)) },
                enabled = !track.missing,
                selected = playing,
                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.cast_bgm_stop else R.string.cast_bgm_play
                ),
            )
            SmallPlainButton(
                onClick = { onIntent(BgmPoolIntent.AskTrackVolume(track.id)) },
                enabled = !track.missing,
                // 音量被压过时点亮：行内就看得出来，不用点进弹窗才发现
                selected = track.volume < 0.999f,
                icon = Icons.Default.Tune,
                contentDescription = stringResource(R.string.cast_bgm_track_volume),
            )
            SmallPlainButton(
                onClick = { onIntent(BgmPoolIntent.AskRenameTrack(track.id)) },
                icon = AppIcons.Edit,
                contentDescription = stringResource(R.string.cast_bgm_rename_track),
            )
            SmallPlainButton(
                onClick = { onIntent(BgmPoolIntent.AskDeleteTrack(track.id)) },
                icon = AppIcons.Delete,
                contentDescription = stringResource(R.string.delete),
            )
        },
        onClick = { onIntent(BgmPoolIntent.PlayToggle(track.id)) },
    )
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
    show: Boolean,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.cast_bgm_fade),
        // 改动即时落库，这颗「确定」只是收尾：不给取消按钮，免得让人以为还能反悔
        confirmText = stringResource(R.string.ok),
        onConfirm = onDismiss,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TinySwitchSettingItem(
                    title = stringResource(R.string.cast_bgm_fade_in),
                    description = stringResource(R.string.cast_bgm_fade_in_summary),
                    checked = state.fadeIn,
                    onCheckedChange = { onIntent(BgmPoolIntent.SetFadeIn(it)) },
                )
                TinySwitchSettingItem(
                    title = stringResource(R.string.cast_bgm_fade_out),
                    description = stringResource(R.string.cast_bgm_fade_out_summary),
                    checked = state.fadeOut,
                    onCheckedChange = { onIntent(BgmPoolIntent.SetFadeOut(it)) },
                )
            }
        },
    )
}
