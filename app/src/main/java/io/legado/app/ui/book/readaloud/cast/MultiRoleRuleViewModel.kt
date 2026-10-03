package io.legado.app.ui.book.readaloud.cast

import android.app.Application
import android.net.Uri
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.help.readaloud.cast.CastGroupRow
import io.legado.app.help.readaloud.cast.CastPoolRow
import io.legado.app.help.readaloud.cast.VoicePoolStore
import io.legado.app.help.readaloud.cast.VoicePoolTransfer
import io.legado.app.help.readaloud.playback.SystemTtsVoiceCatalog
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 多角色规则页 ViewModel。
 *
 * 数据读写收口在 VoicePoolStore / VoiceAssignmentStore（BaseViewModel.execute 跑后台线程），
 * UiState 全 immutable（架构护栏 + AGENTS.md Compose 边界约束）。
 * 树的拉平/拖动规则与背景音乐池共用，见 [CastPoolTree]。
 */
class MultiRoleRuleViewModel(
    application: Application,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(MultiRoleRuleUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MultiRoleRuleEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    /** 全量音色缓存（voiceId → displayName），picker/成员列表刷新时更新。 */
    private var allVoices: List<Pair<String, String>> = emptyList()

    /** 本次待确认的外部格式解析结果（只活在确认对话框这一趟里，不落库）。 */
    private var pendingImport: VoicePoolTransfer.TtsServerPreview? = null

    /** [pendingImport] 是哪一种导出文件，决定确认时的根分组与界面文案。 */
    private var pendingImportSource = CastImportSource.TtsServer

    /** 本次拖动的行（key），只在松手回写时用来定位「哪一行换了父级」。 */
    private var draggedKey: String? = null

    init {
        refreshPools()
    }

    fun onIntent(intent: MultiRoleRuleIntent) {
        when (intent) {
            MultiRoleRuleIntent.Refresh -> {
                refreshPools()
                _uiState.value.expandedPools.forEach { refreshMembers(it.poolId) }
                if (_uiState.value.tab == CastTab.Assignments) loadAssignments()
            }

            is MultiRoleRuleIntent.SwitchTab -> {
                _uiState.update { it.copy(tab = intent.tab) }
                if (intent.tab == CastTab.Assignments) loadAssignments()
            }

            is MultiRoleRuleIntent.ToggleGroup -> _uiState.update { state ->
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

            is MultiRoleRuleIntent.SetSearch -> _uiState.update { state ->
                val query = if (intent.active) state.searchQuery else ""
                state.copy(
                    searchActive = intent.active,
                    searchQuery = query,
                    rows = CastPoolTree.buildRows(state.groups, state.pools, state.collapsedGroups, query)
                        .toImmutableList(),
                )
            }

            is MultiRoleRuleIntent.UpdateSearch -> _uiState.update { state ->
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

            is MultiRoleRuleIntent.MoveItem -> {
                val dragged = _uiState.value.rows.getOrNull(intent.from)
                if (dragged != null && !dragged.isUngroupedHeader &&
                    intent.from != intent.to && intent.to in _uiState.value.rows.indices
                ) {
                    draggedKey = dragged.key
                    // 严格照搬库给的绝对下标：它内部记住「被拖项现在在下标 to」，模型里
                    // rows[to] 必须正好是它。若为把顶部「未分组」表头钉在第 0 格而夹紧下标，
                    // 等于吞掉一次移动，库和模型错位后下一帧就要求移回去——来回抽搐。
                    // 该表头允许在拖动时暂时让位，松手回写重算列表时它会自己回到首位。
                    _uiState.update { state ->
                        val moved = state.rows.toMutableList()
                        moved.add(intent.to, moved.removeAt(intent.from))
                        state.copy(
                            rows = moved.toImmutableList(),
                            // 实时告诉界面「松手会归到哪个组」，界面据此点亮目标分组头
                            dragTargetGroupId = CastPoolTree.dropParentOf(
                                moved,
                                intent.to,
                                state.groups.associate { it.id to it.parentId },
                            ),
                            // 出发前的父组：界面拿它和落点一比就能写清「放入 / 拖出 / 只调顺序」
                            dragSourceGroupId = dragged.pool?.groupId
                                ?: dragged.group?.parentId
                                ?: CastPoolTree.UNGROUPED_ID,
                        )
                    }
                }
            }

            MultiRoleRuleIntent.SaveSortOrder -> execute {
                saveSortOrder()
                refreshPools()
            }

            MultiRoleRuleIntent.ShowCreateDialog ->
                _uiState.update { it.copy(editDialog = PoolEditDialogState()) }

            is MultiRoleRuleIntent.ShowEditDialog -> _uiState.update {
                it.copy(
                    editDialog = PoolEditDialogState(
                        editingId = intent.pool.id,
                        name = intent.pool.name,
                        groupId = intent.pool.groupId,
                    ),
                )
            }

            MultiRoleRuleIntent.DismissDialog -> _uiState.update { it.copy(editDialog = null) }

            is MultiRoleRuleIntent.SavePool -> execute {
                val ok = if (intent.editingId == null) {
                    VoicePoolStore.createPool(intent.name, intent.groupId)
                } else {
                    VoicePoolStore.updatePool(intent.editingId, intent.name, intent.groupId)
                }
                if (ok) {
                    _uiState.update { it.copy(editDialog = null) }
                    refreshPools()
                } else {
                    _uiState.update {
                        it.copy(
                            editDialog = it.editDialog?.copy(errorRes = R.string.cast_pool_name_invalid)
                        )
                    }
                }
            }

            is MultiRoleRuleIntent.SetPoolEnabled -> execute {
                VoicePoolStore.setEnabled(intent.poolId, intent.enabled)
                refreshPools()
            }

            is MultiRoleRuleIntent.SetGroupEnabled -> execute {
                VoicePoolStore.setGroupEnabled(intent.groupId, intent.enabled)
                refreshPools()
            }

            is MultiRoleRuleIntent.AskDeletePool ->
                _uiState.update { it.copy(deleteTarget = intent.pool) }

            MultiRoleRuleIntent.DismissDelete ->
                _uiState.update { it.copy(deleteTarget = null) }

            is MultiRoleRuleIntent.ConfirmDeletePool -> execute {
                VoicePoolStore.deletePool(intent.poolId)
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

            is MultiRoleRuleIntent.ShowCreateGroup -> _uiState.update {
                it.copy(groupDialog = GroupEditDialogState(parentId = intent.parentId))
            }

            is MultiRoleRuleIntent.AskRenameGroup -> _uiState.update { state ->
                val group = state.groups.firstOrNull { it.id == intent.groupId }
                state.copy(
                    groupDialog = group?.let {
                        GroupEditDialogState(editingId = it.id, parentId = it.parentId, name = it.name)
                    } ?: state.groupDialog,
                )
            }

            MultiRoleRuleIntent.DismissGroupDialog ->
                _uiState.update { it.copy(groupDialog = null) }

            is MultiRoleRuleIntent.ConfirmGroupDialog -> execute {
                val ok = if (intent.editingId == null) {
                    VoicePoolStore.createGroup(intent.name, intent.parentId)
                } else {
                    VoicePoolStore.renameGroup(intent.editingId, intent.name)
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

            is MultiRoleRuleIntent.AskMoveGroup ->
                _uiState.update { it.copy(moveGroupTarget = intent.groupId) }

            MultiRoleRuleIntent.DismissMoveGroup ->
                _uiState.update { it.copy(moveGroupTarget = null) }

            is MultiRoleRuleIntent.ConfirmMoveGroup -> execute {
                VoicePoolStore.moveGroupToParent(intent.groupId, intent.newParentId)
                _uiState.update { it.copy(moveGroupTarget = null) }
                refreshPools()
            }

            is MultiRoleRuleIntent.AskDeleteGroup ->
                _uiState.update { it.copy(deleteGroupTarget = intent.groupId) }

            MultiRoleRuleIntent.DismissDeleteGroup ->
                _uiState.update { it.copy(deleteGroupTarget = null) }

            is MultiRoleRuleIntent.ConfirmDeleteGroup -> execute {
                VoicePoolStore.dissolveGroup(intent.groupId)
                _uiState.update { it.copy(deleteGroupTarget = null) }
                refreshPools()
            }

            is MultiRoleRuleIntent.TogglePoolExpand -> {
                // 展开/收起每个池各存一份：整页只存一个 expandedPoolId 的话，
                // 点开第二个池就会把第一个顶掉。
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

            is MultiRoleRuleIntent.UpdateMemberQuery -> _uiState.update { state ->
                // 只记筛选词，成员列表原样留着，筛选在界面里做（见 CastPoolCard）
                state.copy(
                    expandedPools = state.expandedPools
                        .withExpandedPool(intent.poolId) { it.copy(memberQuery = intent.query) }
                        .toImmutableList(),
                )
            }

            is MultiRoleRuleIntent.ToggleMemberEnabled -> {
                _uiState.update { state ->
                    state.copy(
                        expandedPools = state.expandedPools
                            .withExpandedPool(intent.poolId) { pool ->
                                pool.copy(
                                    members = pool.members.map { m ->
                                        if (m.voiceId == intent.voiceId) {
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
                    VoicePoolStore.setMemberEnabled(intent.poolId, intent.voiceId, intent.enabled)
                    refreshPools()
                }
            }

            is MultiRoleRuleIntent.RemoveMember -> execute {
                val poolId = intent.poolId
                val keep = VoicePoolStore.memberVoiceIds(poolId) - intent.voiceId
                VoicePoolStore.setMembers(poolId, keep)
                refreshMembers(poolId)
                refreshPools()
            }

            is MultiRoleRuleIntent.ShowMemberPicker -> execute {
                val poolId = intent.poolId
                allVoices = VoicePoolStore.allVoicePairs()
                val engineNames = VoicePoolStore.voiceEngineNames()
                val inPool = VoicePoolStore.memberVoiceIds(poolId)
                _uiState.update {
                    it.copy(
                        pickerPoolId = poolId,
                        pickerQuery = "",
                        pickerCandidates = allVoices
                            .filterNot { (id, _) -> inPool.contains(id) }
                            .map { (id, display) ->
                                CastMemberUi(
                                    voiceId = id,
                                    displayName = display,
                                    engineName = engineNames[id].orEmpty(),
                                    checked = false,
                                )
                            }
                            .toImmutableList(),
                    )
                }
            }

            MultiRoleRuleIntent.DismissMemberPicker -> _uiState.update {
                it.copy(pickerPoolId = null, pickerCandidates = persistentListOf())
            }

            is MultiRoleRuleIntent.UpdatePickerQuery -> _uiState.update { state ->
                val query = intent.query.trim()
                state.copy(
                    pickerQuery = intent.query,
                    pickerCandidates = filterMembers(state.pickerCandidates, allVoices, query),
                )
            }

            is MultiRoleRuleIntent.TogglePickerSelection -> _uiState.update { state ->
                state.copy(
                    pickerCandidates = state.pickerCandidates.map { c ->
                        if (c.voiceId == intent.voiceId) c.copy(checked = intent.checked) else c
                    }.toImmutableList(),
                )
            }

            MultiRoleRuleIntent.SaveMemberPicker -> {
                val poolId = _uiState.value.pickerPoolId ?: return
                val picked = _uiState.value.pickerCandidates
                    .filter { it.checked }
                    .map { it.voiceId }
                    .toSet()
                execute {
                    val merged = VoicePoolStore.memberVoiceIds(poolId) + picked
                    VoicePoolStore.setMembers(poolId, merged)
                    _uiState.update {
                        it.copy(pickerPoolId = null, pickerCandidates = persistentListOf())
                    }
                    refreshMembers(poolId)
                    refreshPools()
                }
            }

            is MultiRoleRuleIntent.DeleteCharacter -> execute {
                VoicePoolStore.deleteCharacter(intent.characterId)
                loadAssignments()
            }

            MultiRoleRuleIntent.AskImportTtsServer ->
                _effects.tryEmit(MultiRoleRuleEffect.OpenTtsServerPicker)

            MultiRoleRuleIntent.AskImportMultiTts ->
                _effects.tryEmit(MultiRoleRuleEffect.OpenMultiTtsPicker)

            MultiRoleRuleIntent.AskImportPools ->
                _effects.tryEmit(MultiRoleRuleEffect.OpenPoolsPicker)

            MultiRoleRuleIntent.AskExportPools ->
                _effects.tryEmit(MultiRoleRuleEffect.SavePoolsTo(EXPORT_FILE_NAME))

            is MultiRoleRuleIntent.TtsServerFileRead -> readExternalFile(
                intent.text,
                CastImportSource.TtsServer,
            )

            is MultiRoleRuleIntent.MultiTtsFileRead -> readExternalFile(
                intent.text,
                CastImportSource.MultiTts,
            )

            is MultiRoleRuleIntent.UpdateTtsServerEngine -> _uiState.update {
                it.copy(ttsServerEngine = intent.engine)
            }

            MultiRoleRuleIntent.DismissTtsServerImport -> {
                pendingImport = null
                _uiState.update { it.copy(ttsServerPreview = null) }
            }

            MultiRoleRuleIntent.ConfirmTtsServerImport -> execute {
                val preview = pendingImport
                val engine = _uiState.value.ttsServerEngine.trim()
                val rootGroup = when (pendingImportSource) {
                    CastImportSource.TtsServer -> VoicePoolTransfer.TTS_SERVER_ROOT_GROUP_NAME
                    CastImportSource.MultiTts -> VoicePoolTransfer.MULTITTS_ROOT_GROUP_NAME
                }
                pendingImport = null
                if (preview == null || engine.isEmpty()) {
                    _uiState.update { it.copy(ttsServerPreview = null) }
                    return@execute
                }
                val summary = VoicePoolTransfer.applyTtsServer(preview, engine, rootGroup)
                _uiState.update { it.copy(ttsServerPreview = null) }
                refreshPools()
                _effects.tryEmit(
                    MultiRoleRuleEffect.ShowToast(
                        context.getString(
                            R.string.cast_import_tts_server_result,
                            summary.pools,
                            summary.voices,
                        )
                    )
                )
            }

            is MultiRoleRuleIntent.ImportPoolsText -> execute {
                val summary = runCatching { VoicePoolTransfer.importJson(intent.text) }.getOrNull()
                refreshPools()
                _effects.tryEmit(
                    MultiRoleRuleEffect.ShowToast(
                        if (summary == null || summary.isEmpty) {
                            context.getString(R.string.cast_import_pools_invalid)
                        } else {
                            context.getString(
                                R.string.cast_import_tts_server_result,
                                summary.pools,
                                summary.voices,
                            )
                        }
                    )
                )
            }

            is MultiRoleRuleIntent.ExportPoolsTo -> execute {
                val written = runCatching {
                    context.contentResolver.openOutputStream(intent.uri)?.use { out ->
                        out.writer().use { it.write(VoicePoolTransfer.exportJson()) }
                    } != null
                }.getOrDefault(false)
                _effects.tryEmit(
                    MultiRoleRuleEffect.ShowToast(
                        context.getString(
                            if (written) R.string.cast_export_pools_done else R.string.cast_export_pools_failed
                        )
                    )
                )
            }
        }
    }

    /**
     * 解析外部格式的角色表（TTS Server / multitts）并把预览摆进确认对话框。
     *
     * 引擎包名要现问系统 TTS：两种导出文件里都不带来源包名，而音色能不能发声全看
     * speakerId 是否与那个引擎暴露的 `Voice.name` 对得上，所以宁可让用户确认一次。
     */
    private fun readExternalFile(text: String, source: CastImportSource) {
        execute {
            val preview = runCatching {
                when (source) {
                    CastImportSource.TtsServer -> VoicePoolTransfer.parseTtsServer(text)
                    CastImportSource.MultiTts -> VoicePoolTransfer.parseMultiTts(text)
                }
            }.getOrNull()
            if (preview == null || preview.pools.isEmpty()) {
                pendingImport = null
                _uiState.update { it.copy(ttsServerPreview = null) }
                _effects.tryEmit(
                    MultiRoleRuleEffect.ShowToast(
                        context.getString(R.string.cast_import_tts_server_empty)
                    )
                )
                return@execute
            }
            pendingImport = preview
            pendingImportSource = source
            val installed = runCatching { SystemTtsVoiceCatalog(context).getEngines() }
                .getOrDefault(emptyList())
                .map { it.sourceId }
            _uiState.update {
                it.copy(
                    ttsServerEngine = guessEnginePackage(source, installed),
                    ttsServerPreview = TtsServerPreviewUi(
                        poolNames = preview.pools.map { p -> p.name }.toImmutableList(),
                        voiceCount = preview.voiceCount,
                        skippedEffects = preview.skippedEffects,
                        skippedDisabled = preview.skippedDisabled,
                        source = source,
                    ),
                )
            }
        }
    }

    /** 导出文件不带来源包名：先在已装引擎里按惯例字样找，找不到退回该格式的默认包名。 */
    private fun guessEnginePackage(source: CastImportSource, installed: List<String>): String {
        val hints = when (source) {
            CastImportSource.TtsServer -> listOf("tts_server", "jing332")
            CastImportSource.MultiTts -> listOf("multitts", "nobody")
        }
        return hints.firstNotNullOfOrNull { hint ->
            installed.firstOrNull { it.contains(hint, ignoreCase = true) }
        } ?: when (source) {
            CastImportSource.TtsServer -> VoicePoolTransfer.DEFAULT_TTS_SERVER_PACKAGE
            CastImportSource.MultiTts -> VoicePoolTransfer.DEFAULT_MULTITTS_PACKAGE
        }
    }

    /** 添加对话框的候选筛选（成员列表的筛选在界面里做，见 CastPoolCard）。 */
    private fun filterMembers(
        list: kotlinx.collections.immutable.ImmutableList<CastMemberUi>,
        voices: List<Pair<String, String>>,
        query: String,
    ): kotlinx.collections.immutable.ImmutableList<CastMemberUi> {
        if (query.isEmpty()) return list
        val nameById = voices.toMap()
        return list.filter {
            (nameById[it.voiceId] ?: it.displayName).contains(query, ignoreCase = true)
        }.toImmutableList()
    }

    private fun refreshPools() {
        execute {
            val pools = VoicePoolStore.listPools()
            val groups = VoicePoolStore.listGroups()
            _uiState.update { state ->
                state.copy(
                    pools = pools.toImmutableList(),
                    groups = groups.toImmutableList(),
                    rows = CastPoolTree.buildRows(groups, pools, state.collapsedGroups, state.searchQuery)
                        .toImmutableList(),
                    // 列表回到树状顺序，拖动中的落点提示作废
                    dragTargetGroupId = null,
                    dragSourceGroupId = null,
                )
            }
        }
    }

    /**
     * 松手落库：顺序按当前可见列表整体回写，但父级只重算「被拖的那一行」——
     * 规则本身在 [CastPoolTree.savePlan]，与背景音乐池共用。
     */
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
        VoicePoolStore.saveSlots(plan.first, plan.second)
    }

    /**
     * 重新取某个展开池的成员：存筛选前的全量（筛选在界面里做），这样清空筛选词
     * 能把整池看回来。池已经收起就什么都不做，withExpandedPool 会原样返回。
     */
    private fun refreshMembers(poolId: String) {
        execute {
            allVoices = VoicePoolStore.allVoicePairs()
            val detail = VoicePoolStore.poolDetail(poolId)
            val rows = detail.members.map { m ->
                CastMemberUi(
                    voiceId = m.id,
                    displayName = m.displayName,
                    engineName = m.engineName,
                    checked = m.enabled,
                )
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

    private fun loadAssignments() {
        execute {
            val rows = VoicePoolStore.assignmentRows()
            _uiState.update {
                it.copy(
                    assignments = rows.map { r ->
                        CastAssignmentRowUi(
                            characterId = r.characterId,
                            name = r.name,
                            poolLabel = r.poolLabel,
                            voiceName = r.voiceName,
                            bookName = r.bookName,
                            lineCount = r.lineCount,
                        )
                    }.toImmutableList(),
                )
            }
        }
    }

    private companion object {
        /** 自有格式导出文件名，界面弹系统「保存」时用它。 */
        const val EXPORT_FILE_NAME = "voice_pools.json"
    }
}
