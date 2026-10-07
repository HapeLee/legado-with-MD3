package io.legado.app.ui.book.readaloud.cast

import android.app.Application
import android.net.Uri
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.help.readaloud.cast.BgmPoolStore
import io.legado.app.help.readaloud.cast.BgmPoolTransfer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * 背景音乐池 ViewModel。
 *
 * 数据读写收口在 [BgmPoolStore]（架构护栏：VM 不直连 DAO）；整包导出/导入走
 * [BgmPoolTransfer]，VM 只管开流和报计数。池/分组/拖动这套逻辑与
 * 角色声音池同源，树规则走 [CastPoolTree]，所以两页行为不会各自漂移。
 * 播放本身是纯界面行为（MediaPlayer 跟着组合走），这里只记「哪条在放」并把它变成图标与 Effect。
 */
class BgmPoolViewModel(
    application: Application,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(BgmPoolUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BgmPoolEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    /** 配乐库缓存（trackId → 名称），成员列表与添加对话框刷新时更新。 */
    private var allTracks: List<Pair<String, String>> = emptyList()

    /** 本次拖动的行 key，只在松手回写时用来定位「哪一行换了父级」。 */
    private var draggedKey: String? = null

    init {
        refreshPools()
        refreshTracks()
        _uiState.update { it.copy(fadeIn = BgmPoolStore.fadeIn(), fadeOut = BgmPoolStore.fadeOut()) }
    }

    fun onIntent(intent: BgmPoolIntent) {
        when (intent) {
            BgmPoolIntent.Refresh -> {
                refreshPools()
                refreshTracks()
                _uiState.value.expandedPools.forEach { refreshMembers(it.poolId) }
            }

            is BgmPoolIntent.ToggleGroup -> _uiState.update { state ->
                val collapsed = if (state.collapsedGroups.contains(intent.group)) {
                    state.collapsedGroups - intent.group
                } else {
                    state.collapsedGroups + intent.group
                }
                state.copy(
                    collapsedGroups = collapsed.toImmutableSet(),
                    rows = CastPoolTree.buildRows(state.groups, state.pools, collapsed, state.searchQuery)
                        .toImmutableList(),
                )
            }

            is BgmPoolIntent.SetSearch -> _uiState.update { state ->
                val query = if (intent.active) state.searchQuery else ""
                state.copy(
                    searchActive = intent.active,
                    searchQuery = query,
                    rows = CastPoolTree.buildRows(state.groups, state.pools, state.collapsedGroups, query)
                        .toImmutableList(),
                )
            }

            is BgmPoolIntent.UpdateSearch -> _uiState.update { state ->
                state.copy(
                    searchQuery = intent.query,
                    rows = CastPoolTree.buildRows(
                        state.groups,
                        state.pools,
                        state.collapsedGroups,
                        intent.query.trim(),
                    ).toImmutableList(),
                )
            }

            is BgmPoolIntent.MoveItem -> {
                val dragged = _uiState.value.rows.getOrNull(intent.from)
                if (dragged != null && !dragged.isUngroupedHeader &&
                    intent.from != intent.to && intent.to in _uiState.value.rows.indices
                ) {
                    draggedKey = dragged.key
                    // 与角色声音池同理：绝对下标必须原样交给拖动库，夹一下就错位抽搐。
                    _uiState.update { state ->
                        val moved = state.rows.toMutableList()
                        moved.add(intent.to, moved.removeAt(intent.from))
                        state.copy(
                            rows = moved.toImmutableList(),
                            dragTargetGroupId = CastPoolTree.dropParentOf(
                                moved,
                                intent.to,
                                state.groups.associate { it.id to it.parentId },
                            ),
                            dragSourceGroupId = dragged.pool?.groupId
                                ?: dragged.group?.parentId
                                ?: CastPoolTree.UNGROUPED_ID,
                        )
                    }
                }
            }

            BgmPoolIntent.SaveSortOrder -> execute {
                saveSortOrder()
                refreshPools()
            }

            BgmPoolIntent.ShowCreateDialog ->
                _uiState.update { it.copy(editDialog = PoolEditDialogState()) }

            is BgmPoolIntent.ShowEditDialog -> _uiState.update {
                it.copy(
                    editDialog = PoolEditDialogState(
                        editingId = intent.pool.id,
                        name = intent.pool.name,
                        groupId = intent.pool.groupId,
                    ),
                )
            }

            BgmPoolIntent.DismissDialog -> _uiState.update { it.copy(editDialog = null) }

            is BgmPoolIntent.SavePool -> execute {
                val ok = if (intent.editingId == null) {
                    BgmPoolStore.createPool(intent.name, intent.groupId)
                } else {
                    BgmPoolStore.updatePool(intent.editingId, intent.name, intent.groupId)
                }
                if (ok) {
                    _uiState.update { it.copy(editDialog = null) }
                    refreshPools()
                } else {
                    _uiState.update {
                        it.copy(editDialog = it.editDialog?.copy(errorRes = R.string.cast_pool_name_invalid))
                    }
                }
            }

            is BgmPoolIntent.SetPoolEnabled -> execute {
                BgmPoolStore.setPoolEnabled(intent.poolId, intent.enabled)
                refreshPools()
            }

            is BgmPoolIntent.SetGroupEnabled -> execute {
                BgmPoolStore.setGroupEnabled(intent.groupId, intent.enabled)
                refreshPools()
            }

            is BgmPoolIntent.AskDeletePool ->
                _uiState.update { it.copy(deleteTarget = intent.pool) }

            BgmPoolIntent.DismissDelete -> _uiState.update { it.copy(deleteTarget = null) }

            is BgmPoolIntent.ConfirmDeletePool -> execute {
                BgmPoolStore.deletePool(intent.poolId)
                _uiState.update {
                    it.copy(
                        deleteTarget = null,
                        expandedPools = it.expandedPools
                            .filterNot { pool -> pool.poolId == intent.poolId }
                            .toImmutableList(),
                    )
                }
                refreshPools()
            }

            is BgmPoolIntent.ShowCreateGroup -> _uiState.update {
                it.copy(groupDialog = GroupEditDialogState(parentId = intent.parentId))
            }

            is BgmPoolIntent.AskRenameGroup -> _uiState.update { state ->
                val group = state.groups.firstOrNull { it.id == intent.groupId }
                state.copy(
                    groupDialog = group?.let {
                        GroupEditDialogState(editingId = it.id, parentId = it.parentId, name = it.name)
                    } ?: state.groupDialog,
                )
            }

            BgmPoolIntent.DismissGroupDialog -> _uiState.update { it.copy(groupDialog = null) }

            is BgmPoolIntent.ConfirmGroupDialog -> execute {
                val ok = if (intent.editingId == null) {
                    BgmPoolStore.createGroup(intent.name, intent.parentId)
                } else {
                    BgmPoolStore.renameGroup(intent.editingId, intent.name)
                }
                if (ok) {
                    _uiState.update { it.copy(groupDialog = null) }
                } else {
                    _uiState.update {
                        it.copy(groupDialog = it.groupDialog?.copy(errorRes = R.string.cast_group_name_invalid))
                    }
                }
                refreshPools()
            }

            is BgmPoolIntent.AskMoveGroup ->
                _uiState.update { it.copy(moveGroupTarget = intent.groupId) }

            BgmPoolIntent.DismissMoveGroup -> _uiState.update { it.copy(moveGroupTarget = null) }

            is BgmPoolIntent.ConfirmMoveGroup -> execute {
                BgmPoolStore.moveGroupToParent(intent.groupId, intent.newParentId)
                _uiState.update { it.copy(moveGroupTarget = null) }
                refreshPools()
            }

            is BgmPoolIntent.AskDeleteGroup ->
                _uiState.update { it.copy(deleteGroupTarget = intent.groupId) }

            BgmPoolIntent.DismissDeleteGroup -> _uiState.update { it.copy(deleteGroupTarget = null) }

            is BgmPoolIntent.ConfirmDeleteGroup -> execute {
                BgmPoolStore.dissolveGroup(intent.groupId)
                _uiState.update { it.copy(deleteGroupTarget = null) }
                refreshPools()
            }

            is BgmPoolIntent.TogglePoolExpand -> {
                // 与角色声音池同理：展开状态按池各存一份，点开一个不把别的顶掉
                val opening = _uiState.value.expandedPools.none { it.poolId == intent.poolId }
                _uiState.update { state ->
                    state.copy(
                        expandedPools = if (opening) {
                            (state.expandedPools + ExpandedPoolUi(intent.poolId)).toImmutableList()
                        } else {
                            state.expandedPools
                                .filterNot { it.poolId == intent.poolId }
                                .toImmutableList()
                        },
                    )
                }
                if (opening) refreshMembers(intent.poolId)
            }

            is BgmPoolIntent.UpdateMemberQuery -> _uiState.update { state ->
                // 只记筛选词，成员列表原样留着，筛选在界面里做（见 CastPoolCard）
                state.copy(
                    expandedPools = state.expandedPools
                        .withExpandedPool(intent.poolId) { pool -> pool.copy(memberQuery = intent.query) }
                        .toImmutableList(),
                )
            }

            is BgmPoolIntent.ToggleMemberEnabled -> {
                _uiState.update { state ->
                    state.copy(
                        expandedPools = state.expandedPools
                            .withExpandedPool(intent.poolId) { pool ->
                                pool.copy(
                                    members = pool.members.map { m ->
                                        if (m.voiceId == intent.trackId) {
                                            m.copy(checked = intent.enabled)
                                        } else {
                                            m
                                        }
                                    },
                                )
                            }
                            .toImmutableList(),
                    )
                }
                execute {
                    BgmPoolStore.setMemberEnabled(intent.poolId, intent.trackId, intent.enabled)
                    refreshPools()
                }
            }

            is BgmPoolIntent.RemoveMember -> execute {
                val poolId = intent.poolId
                val keep = BgmPoolStore.memberTrackIds(poolId) - intent.trackId
                BgmPoolStore.setMembers(poolId, keep)
                refreshMembers(poolId)
                refreshPools()
            }

            is BgmPoolIntent.ShowMemberPicker -> execute {
                val poolId = intent.poolId
                allTracks = BgmPoolStore.allTrackPairs()
                val inPool = BgmPoolStore.memberTrackIds(poolId)
                _uiState.update {
                    it.copy(
                        pickerPoolId = poolId,
                        pickerQuery = "",
                        pickerCandidates = allTracks
                            .filterNot { (id, _) -> inPool.contains(id) }
                            .map { (id, name) ->
                                CastMemberUi(voiceId = id, displayName = name, checked = false)
                            }
                            .toImmutableList(),
                    )
                }
            }

            BgmPoolIntent.DismissMemberPicker -> _uiState.update {
                it.copy(pickerPoolId = null, pickerCandidates = persistentListOf())
            }

            is BgmPoolIntent.UpdatePickerQuery -> _uiState.update { state ->
                state.copy(
                    pickerQuery = intent.query,
                    pickerCandidates = filterRows(state.pickerCandidates, intent.query).toImmutableList(),
                )
            }

            is BgmPoolIntent.TogglePickerSelection -> _uiState.update { state ->
                state.copy(
                    pickerCandidates = state.pickerCandidates.map { c ->
                        if (c.voiceId == intent.trackId) c.copy(checked = intent.checked) else c
                    }.toImmutableList(),
                )
            }

            BgmPoolIntent.SaveMemberPicker -> {
                val poolId = _uiState.value.pickerPoolId ?: return
                val picked = _uiState.value.pickerCandidates
                    .filter { it.checked }
                    .map { it.voiceId }
                    .toSet()
                execute {
                    val merged = BgmPoolStore.memberTrackIds(poolId) + picked
                    BgmPoolStore.setMembers(poolId, merged)
                    _uiState.update {
                        it.copy(pickerPoolId = null, pickerCandidates = persistentListOf())
                    }
                    refreshMembers(poolId)
                    refreshPools()
                }
            }

            BgmPoolIntent.ToggleLibrary -> _uiState.update {
                it.copy(libraryExpanded = !it.libraryExpanded)
            }

            BgmPoolIntent.AskImport -> _effects.tryEmit(BgmPoolEffect.OpenFilePicker)

            is BgmPoolIntent.FilesPicked -> import(intent)

            BgmPoolIntent.AskImportPackage -> _effects.tryEmit(BgmPoolEffect.OpenPackagePicker)

            BgmPoolIntent.AskExportPackage ->
                _effects.tryEmit(BgmPoolEffect.SavePackageTo(EXPORT_FILE_NAME))

            is BgmPoolIntent.ImportPackagePicked -> importPackage(intent.uri)

            is BgmPoolIntent.ExportPackageTo -> exportPackage(intent.uri)

            is BgmPoolIntent.AskTrackVolume -> {
                val target = _uiState.value.tracks.firstOrNull { it.id == intent.id } ?: return
                _uiState.update { it.copy(trackVolumeTarget = target) }
            }

            BgmPoolIntent.DismissTrackVolume -> _uiState.update { it.copy(trackVolumeTarget = null) }

            is BgmPoolIntent.SetTrackVolume -> execute {
                BgmPoolStore.setTrackVolume(intent.id, intent.volume)
                refreshTracks()
            }

            is BgmPoolIntent.AskRenameTrack -> {
                val target = _uiState.value.tracks.firstOrNull { it.id == intent.id } ?: return
                _uiState.update { it.copy(trackRenameTarget = target) }
            }

            BgmPoolIntent.DismissTrackRename ->
                _uiState.update { it.copy(trackRenameTarget = null) }

            is BgmPoolIntent.ConfirmRenameTrack -> execute {
                // 改名会连带同步场景标记里的曲目名，判非法时一行都不动，见 BgmPoolStore.renameTrack
                if (BgmPoolStore.renameTrack(intent.id, intent.name)) {
                    refreshTracks()
                } else {
                    toast(context.getString(R.string.cast_bgm_rename_failed))
                }
                _uiState.update { it.copy(trackRenameTarget = null) }
            }

            is BgmPoolIntent.SetTrackEnabled -> execute {
                BgmPoolStore.setEnabled(intent.id, intent.enabled)
                refreshTracks()
                _uiState.value.expandedPools.forEach { refreshMembers(it.poolId) }
            }

            is BgmPoolIntent.AskDeleteTrack -> {
                val target = _uiState.value.tracks.firstOrNull { it.id == intent.id } ?: return
                _uiState.update { it.copy(trackDeleteTarget = target) }
            }

            BgmPoolIntent.DismissTrackDelete -> _uiState.update { it.copy(trackDeleteTarget = null) }

            is BgmPoolIntent.ConfirmDeleteTrack -> execute {
                val track = BgmPoolStore.list().firstOrNull { it.id == intent.id }
                if (_uiState.value.playingId == intent.id) {
                    _effects.tryEmit(BgmPoolEffect.Stop)
                }
                track?.let { BgmPoolStore.delete(it) }
                _uiState.update {
                    it.copy(trackDeleteTarget = null, playingId = null)
                }
                refreshTracks()
                refreshPools()
                _uiState.value.expandedPools.forEach { refreshMembers(it.poolId) }
            }

            is BgmPoolIntent.PlayToggle -> {
                if (_uiState.value.playingId == intent.id) {
                    _effects.tryEmit(BgmPoolEffect.Stop)
                    _uiState.update { it.copy(playingId = null) }
                    return
                }
                val track = _uiState.value.tracks.firstOrNull { it.id == intent.id } ?: return
                if (track.missing) {
                    toast(context.getString(R.string.cast_bgm_missing_file))
                    return
                }
                _effects.tryEmit(BgmPoolEffect.Play(intent.id, track.path, track.volume))
                _uiState.update { it.copy(playingId = intent.id) }
            }

            BgmPoolIntent.PlayFinished -> _uiState.update { it.copy(playingId = null) }

            is BgmPoolIntent.SetFadeIn -> {
                BgmPoolStore.setFade(intent.enabled, fadeIn = true)
                _uiState.update { it.copy(fadeIn = intent.enabled) }
            }

            is BgmPoolIntent.SetFadeOut -> {
                BgmPoolStore.setFade(intent.enabled, fadeIn = false)
                _uiState.update { it.copy(fadeOut = intent.enabled) }
            }
        }
    }

    private fun import(intent: BgmPoolIntent.FilesPicked) = execute {
        if (intent.uris.isEmpty()) return@execute
        val result = BgmPoolStore.import(intent.uris)
        _effects.tryEmit(
            BgmPoolEffect.ShowToast(
                context.getString(R.string.cast_bgm_import_result, result.added, result.skipped)
            )
        )
        refreshTracks()
    }

    private fun toast(message: String) {
        _effects.tryEmit(BgmPoolEffect.ShowToast(message))
    }

    /**
     * 解一个音乐包（zip）：清单字段、音频落盘、按名字回解池归属全在
     * [BgmPoolTransfer.importZip] 里，这里只负责开流和把三个计数报成 toast。
     *
     * 不是本软件导出的包（没有清单 / kind 对不上 / 读不出来）统一报「不是本软件导出的音乐包」，
     * 解包一半失败则已经落库的部分保留——与角色声音池导入同一口径，导入只增不删。
     */
    private fun importPackage(uri: Uri) = execute {
        val summary = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.use { BgmPoolTransfer.importZip(it) }
                ?: error("cannot open $uri")
        }.getOrElse {
            toast(context.getString(R.string.cast_bgm_import_package_invalid))
            return@execute
        }
        toast(
            context.getString(
                R.string.cast_bgm_import_package_result,
                summary.pools,
                summary.tracks,
                summary.skippedMissing,
            )
        )
        refreshTracks()
        refreshPools()
        _uiState.value.expandedPools.forEach { refreshMembers(it.poolId) }
    }

    /** 写一个音乐包：VM 开流、[BgmPoolTransfer.exportZip] 出内容，与角色声音池导出同一分工。 */
    private fun exportPackage(uri: Uri) = execute {
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                BgmPoolTransfer.exportZip(out)
            } != null
        }.getOrDefault(false)
        toast(
            context.getString(
                if (written) R.string.cast_bgm_export_done else R.string.cast_export_pools_failed
            )
        )
    }

    private fun refreshPools() {
        execute {
            val pools = BgmPoolStore.listPools()
            val groups = BgmPoolStore.listGroups()
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    pools = pools.toImmutableList(),
                    groups = groups.toImmutableList(),
                    rows = CastPoolTree.buildRows(groups, pools, state.collapsedGroups, state.searchQuery)
                        .toImmutableList(),
                    dragTargetGroupId = null,
                    dragSourceGroupId = null,
                )
            }
        }
    }

    /** 松手回写：只重算被拖那一行的父级，其余行原样保留，见 [CastPoolTree.savePlan]。 */
    private suspend fun saveSortOrder() {
        val state = _uiState.value
        val draggedRowKey = draggedKey
        draggedKey = null
        if (state.searchQuery.isNotBlank()) return
        val plan = CastPoolTree.savePlan(
            rows = state.rows,
            pools = state.pools,
            groups = state.groups,
            draggedKey = draggedRowKey,
        ) ?: return
        BgmPoolStore.saveSlots(plan.first, plan.second)
    }

    /**
     * 重新取某个展开池的成员：存筛选前的全量，筛选在界面里做。
     * 池已经收起时 withExpandedPool 原样返回，不会给它挂上一份没人看的列表。
     */
    private fun refreshMembers(poolId: String) {
        execute {
            allTracks = BgmPoolStore.allTrackPairs()
            val detail = BgmPoolStore.poolDetail(poolId)
            val rows = detail.members.map { m ->
                CastMemberUi(voiceId = m.id, displayName = m.displayName, checked = m.enabled)
            }
            _uiState.update { state ->
                state.copy(
                    expandedPools = state.expandedPools
                        .withExpandedPool(poolId) { pool -> pool.copy(members = rows) }
                        .toImmutableList(),
                )
            }
        }
    }

    private fun refreshTracks() {
        execute {
            val tracks = BgmPoolStore.list()
            _uiState.update { state ->
                state.copy(
                    tracks = tracks.map { entity ->
                        BgmTrackUi(
                            id = entity.id,
                            name = entity.name,
                            subtitle = formatSize(entity.sizeBytes) +
                                if (entity.durationMs > 0) " · " + formatDuration(entity.durationMs) else "",
                            enabled = entity.enabled,
                            missing = !File(entity.path).exists(),
                            path = entity.path,
                            volume = entity.volume,
                        )
                    }.toImmutableList(),
                    // 列表刷新后正在播的那条可能已被删掉
                    playingId = state.playingId?.takeIf { playing ->
                        tracks.any { it.id == playing }
                    },
                )
            }
        }
    }

    /** 池内成员与添加候选共用一套按显示名筛选的规则。 */
    private fun filterRows(rows: List<CastMemberUi>, query: String): List<CastMemberUi> {
        val q = query.trim()
        return if (q.isEmpty()) rows else rows.filter { it.displayName.contains(q, ignoreCase = true) }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
        bytes >= 1 shl 10 -> "%d KB".format(bytes / 1024)
        else -> "$bytes B"
    }

    companion object {
        /** CreateDocument 的建议文件名；扩展名要和 Screen 里 `application/zip` 的 mime 对上。 */
        private const val EXPORT_FILE_NAME = "bgm_pools.zip"
    }
}
