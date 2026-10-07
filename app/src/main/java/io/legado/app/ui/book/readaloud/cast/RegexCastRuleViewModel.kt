package io.legado.app.ui.book.readaloud.cast

import android.app.Application
import android.net.Uri
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.help.readaloud.cast.BgmPoolStore
import io.legado.app.help.readaloud.cast.RegexCastRuleStore
import io.legado.app.help.readaloud.cast.RegexCastTransfer
import io.legado.app.help.readaloud.cast.VoicePoolStore
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.ui.widget.components.CastOption
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 正则角色管理 ViewModel。
 *
 * DAO 访问收口在 [RegexCastRuleStore]（架构护栏：VM 不直连 DAO）。树的操作（拉平、落点归属、
 * 松手回写）全部走 [CastPoolTree]，与角色声音池 / 背景音乐池共用同一份规则。
 *
 * 改完即生效：朗读侧每次准备新章会重取规则快照，正在播的那一条不受影响，下一条起用新规则。
 */
class RegexCastRuleViewModel(
    application: Application,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(RegexCastRuleUiState())
    val uiState = _uiState.asStateFlow()

    /** 导入/导出的一次性回报（计数、失败原因），不属于页面状态，不进 UiState。 */
    private val _effects = MutableSharedFlow<RegexCastRuleEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    /** 规则原文：卡片给回来的只有 id 字符串，编辑/删除/开关都要按 id 找回那条。 */
    private var rules: List<RegexCastRule> = emptyList()

    /** 池名与条目名：列表摘要与弹窗候选都从这里取，省掉逐行查库。 */
    private var poolNames: Map<String, String> = emptyMap()
    private var itemNames: Map<String, String> = emptyMap()

    /** 拖动中被拖行的 key；松手回写后要清掉。 */
    private var draggedKey: String? = null

    init {
        refresh()
    }

    fun onIntent(intent: RegexCastRuleIntent) {
        when (intent) {
            RegexCastRuleIntent.Refresh -> refresh()

            RegexCastRuleIntent.ShowCreate -> {
                _uiState.update {
                    it.copy(editTarget = RegexCastRuleHolder(RegexCastRule(), isNew = true))
                }
                launchIo { loadCandidates(RegexCastRule.POOL_ROLE, "", "") }
            }

            is RegexCastRuleIntent.ShowEdit -> {
                val rule = rules.firstOrNull { it.id == intent.ruleId }
                if (rule != null) {
                    _uiState.update {
                        it.copy(editTarget = RegexCastRuleHolder(rule, isNew = false))
                    }
                    launchIo { loadCandidates(rule.poolKind, rule.poolId, rule.itemId) }
                }
            }

            RegexCastRuleIntent.DismissEdit -> _uiState.update { it.copy(editTarget = null) }

            is RegexCastRuleIntent.Save -> launchIo {
                RegexCastRuleStore.save(intent.rule)
                refresh()
                _uiState.update { it.copy(editTarget = null) }
            }

            is RegexCastRuleIntent.PickPool -> launchIo {
                loadCandidates(intent.kind, intent.poolId, intent.keepItemId)
            }

            is RegexCastRuleIntent.ShowDelete -> {
                rules.firstOrNull { it.id == intent.ruleId }?.let { rule ->
                    _uiState.update { it.copy(deleteTarget = RegexCastRuleHolder(rule, false)) }
                }
            }

            RegexCastRuleIntent.DismissDelete -> _uiState.update { it.copy(deleteTarget = null) }

            is RegexCastRuleIntent.Delete -> launchIo {
                rules.firstOrNull { it.id == intent.ruleId }?.let { RegexCastRuleStore.delete(it) }
                refresh()
                _uiState.update { it.copy(deleteTarget = null) }
            }

            is RegexCastRuleIntent.RuleEnabled -> launchIo {
                val id = intent.ruleId.toLongOrNull() ?: return@launchIo
                rules.firstOrNull { it.id == id }?.let {
                    RegexCastRuleStore.setEnabled(it, intent.enabled)
                }
                refresh()
            }

            is RegexCastRuleIntent.Query -> {
                _uiState.update { it.copy(searchQuery = intent.text) }
                rebuildRows()
            }

            RegexCastRuleIntent.ToggleSearch -> {
                val open = !_uiState.value.searchActive
                _uiState.update { it.copy(searchActive = open, searchQuery = if (open) it.searchQuery else "") }
                rebuildRows()
            }

            is RegexCastRuleIntent.MoveItem -> moveItem(intent.from, intent.to)

            RegexCastRuleIntent.SaveSortOrder -> launchIo {
                saveSortOrder()
                refresh()
            }

            is RegexCastRuleIntent.ToggleGroup -> {
                _uiState.update { state ->
                    val collapsed = state.collapsedGroups.toMutableSet()
                    if (!collapsed.add(intent.groupId)) collapsed.remove(intent.groupId)
                    state.copy(collapsedGroups = collapsed.toPersistentSet())
                }
                rebuildRows()
            }

            is RegexCastRuleIntent.GroupEnabled -> launchIo {
                RegexCastRuleStore.setGroupEnabled(intent.groupId, intent.enabled)
                refresh()
            }

            is RegexCastRuleIntent.RenameGroup -> {
                val group = _uiState.value.groups.firstOrNull { it.id == intent.groupId }
                _uiState.update {
                    it.copy(groupDialog = GroupEditDialogState(
                        editingId = intent.groupId,
                        parentId = group?.parentId.orEmpty(),
                        name = group?.name.orEmpty(),
                    ))
                }
            }

            is RegexCastRuleIntent.CreateGroup -> _uiState.update {
                it.copy(groupDialog = GroupEditDialogState(parentId = intent.parentId))
            }

            is RegexCastRuleIntent.MoveGroup -> _uiState.update {
                it.copy(moveGroupTarget = intent.groupId)
            }

            is RegexCastRuleIntent.DeleteGroup -> _uiState.update {
                it.copy(deleteGroupTarget = intent.groupId)
            }

            RegexCastRuleIntent.DismissGroupDialog -> _uiState.update { it.copy(groupDialog = null) }

            is RegexCastRuleIntent.ConfirmGroup -> launchIo {
                val dialog = _uiState.value.groupDialog
                val editing = intent.editingId
                if (editing == null) {
                    val created = RegexCastRuleStore.createGroup(intent.name, dialog?.parentId.orEmpty())
                    _uiState.update {
                        it.copy(
                            groupDialog = if (created == null) {
                                dialog?.copy(errorRes = R.string.regex_cast_group_bad_name)
                            } else {
                                null
                            }
                        )
                    }
                } else {
                    val ok = RegexCastRuleStore.renameGroup(editing, intent.name)
                    _uiState.update {
                        it.copy(groupDialog = if (ok) null else dialog?.copy(errorRes = R.string.regex_cast_group_duplicate))
                    }
                }
                refresh()
            }

            is RegexCastRuleIntent.ConfirmMoveGroup -> launchIo {
                RegexCastRuleStore.moveGroupToParent(intent.id, intent.parentId)
                refresh()
                _uiState.update { it.copy(moveGroupTarget = null) }
            }

            RegexCastRuleIntent.DismissMoveGroup -> _uiState.update { it.copy(moveGroupTarget = null) }
            RegexCastRuleIntent.DismissDeleteGroup -> _uiState.update { it.copy(deleteGroupTarget = null) }

            is RegexCastRuleIntent.ConfirmDeleteGroup -> launchIo {
                RegexCastRuleStore.deleteGroup(intent.id)
                refresh()
                _uiState.update { it.copy(deleteGroupTarget = null) }
            }

            is RegexCastRuleIntent.ExportTo -> launchIo {
                val written = runCatching {
                    context.contentResolver.openOutputStream(intent.uri)?.use { out ->
                        out.writer().use { it.write(RegexCastTransfer.exportJson()) }
                    } != null
                }.getOrDefault(false)
                _effects.tryEmit(
                    RegexCastRuleEffect.ShowToast(
                        context.getString(
                            if (written) R.string.regex_cast_export_done
                            else R.string.regex_cast_export_failed
                        )
                    )
                )
            }

            /**
             * 导入只增不删：解一半失败时已经落库的部分保留，与角色声音池同一口径。
             * 不是本软件导出的文件（读不出 / kind 对不上）统一报一句，不抛给用户看堆栈。
             */
            is RegexCastRuleIntent.ImportFrom -> launchIo {
                val summary = runCatching {
                    context.contentResolver.openInputStream(intent.uri)
                        ?.use { RegexCastTransfer.importJson(it.reader().readText()) }
                }.getOrNull()
                if (summary == null) {
                    _effects.tryEmit(
                        RegexCastRuleEffect.ShowToast(
                            context.getString(R.string.regex_cast_import_invalid)
                        )
                    )
                } else {
                    _effects.tryEmit(
                        RegexCastRuleEffect.ShowToast(
                            context.getString(
                                R.string.regex_cast_import_result,
                                summary.rules,
                                summary.groups,
                                summary.unresolvedTargets,
                                summary.existingRules,
                            )
                        )
                    )
                    refresh()
                }
            }
        }
    }

    private fun refresh() = launchIo {
        val (pools, items) = RegexCastRuleStore.names()
        poolNames = pools
        itemNames = items
        rules = RegexCastRuleStore.all()
        val groups = RegexCastRuleStore.listGroups()
        val rows = RegexCastRuleStore.poolRows { rule -> summaryOf(rule) }
        _uiState.update { state ->
            state.copy(
                pools = rows.toImmutableList(),
                groups = groups.toImmutableList(),
                rows = CastPoolTree.buildRows(
                    groups = groups,
                    pools = rows,
                    collapsed = state.collapsedGroups,
                    query = state.searchQuery,
                ).toImmutableList(),
                groupOptions = groups.map { CastOption(it.id, it.path) }.toImmutableList(),
                dragTargetGroupId = null,
                dragSourceGroupId = null,
            )
        }
    }

    /** 折叠/搜索只改可见行，不用重新查库。 */
    private fun rebuildRows() = _uiState.update { state ->
        state.copy(
            rows = CastPoolTree.buildRows(
                groups = state.groups,
                pools = state.pools,
                collapsed = state.collapsedGroups,
                query = state.searchQuery,
            ).toImmutableList()
        )
    }

    /**
     * 拖动过程中的一次挪动：严格照搬库给的绝对下标，**不夹紧**。
     * 夹紧等于吞掉一次移动，模型与库会错位、下一帧又被要求移回去（来回抽搐）。
     * 「未分组」表头允许被暂时顶下去，松手回写重算列表时它会自己回到首位。
     */
    private fun moveItem(from: Int, to: Int) {
        val state = _uiState.value
        val dragged = state.rows.getOrNull(from)
        if (dragged == null || dragged.isUngroupedHeader || from == to || to !in state.rows.indices) {
            return
        }
        draggedKey = dragged.key
        _uiState.update { current ->
            val moved = current.rows.toMutableList()
            moved.add(to, moved.removeAt(from))
            current.copy(
                rows = moved.toImmutableList(),
                // 实时告诉界面「松手会归到哪个组」，界面据此点亮目标分组头
                dragTargetGroupId = CastPoolTree.dropParentOf(
                    moved,
                    to,
                    current.groups.associate { it.id to it.parentId },
                ),
                dragSourceGroupId = dragged.pool?.groupId
                    ?: dragged.group?.parentId
                    ?: CastPoolTree.UNGROUPED_ID,
            )
        }
    }

    /** 松手落库：顺序按当前可见列表整体回写，父级只重算被拖的那一行（规则在 CastPoolTree 里）。 */
    private suspend fun saveSortOrder() {
        val state = _uiState.value
        val key = draggedKey
        draggedKey = null
        if (key == null || state.searchQuery.isNotBlank()) return
        val plan = CastPoolTree.savePlan(
            rows = state.rows,
            pools = state.pools,
            groups = state.groups,
            draggedKey = key,
        ) ?: return
        RegexCastRuleStore.saveSlots(plan.first, plan.second)
    }

    /**
     * 弹窗的「声音池」「音色/配乐」两栏候选。
     *
     * 声音池**不按启用状态过滤**（停用的也列出来，后缀标一下）——用户就是要能挑到它；
     * 音色/配乐同理：没选池时列全部，选了池才按池内成员收窄。**池只是筛选**，不是前置条件。
     * 已经选中的那条即使不在当前筛选里也留在列表头上，否则一换池子显示就空了。
     *
     * 「变声器」那一栏的候选与池无关（预设是全书共用的），开头补一条「不变声」让空串这一档可选：
     * 它写回 `regex_cast_rules.voiceEffect`，朗读侧由 RegexCastRuleStore.effectsFor 取用。
     */
    private suspend fun loadCandidates(kind: String, poolId: String, keepItemId: String) {
        val role = kind != RegexCastRule.POOL_BGM
        val pools = if (role) VoicePoolStore.listPools() else BgmPoolStore.listPools()
        val allItems = if (role) VoicePoolStore.allVoicePairs() else BgmPoolStore.allTrackPairs()
        val members = when {
            poolId.isBlank() -> emptySet()
            role -> VoicePoolStore.memberVoiceIds(poolId)
            else -> BgmPoolStore.memberTrackIds(poolId)
        }
        val disabled = context.getString(R.string.regex_cast_pool_disabled)
        val items = allItems
            .filter { it.first in members || it.first == keepItemId }
            .ifEmpty { if (poolId.isBlank()) allItems else emptyList() }
            .map { CastOption(it.first, it.second) }
        val effects = if (role) {
            VoiceEffectStore.enabledNames().map { CastOption(it, it) }
        } else {
            emptyList<CastOption>()
        }
        _uiState.update {
            it.copy(
                poolOptions = (listOf(CastOption("", context.getString(R.string.regex_cast_pick_pool))) +
                    pools.map { p -> CastOption(p.id, if (p.enabled) p.name else p.name + disabled) })
                    .toImmutableList(),
                itemOptions = (if (poolId.isBlank()) {
                    items
                } else {
                    listOf(CastOption("", context.getString(R.string.regex_cast_random))) + items
                }).toImmutableList(),
                effectOptions = (listOf(
                    CastOption("", context.getString(R.string.regex_cast_effect_none)),
                ) + effects).toImmutableList(),
            )
        }
    }

    /** 一行中文摘要：命中什么 → 变成什么。id 反查不到名字就原样显示，别留空白让人以为没存。 */
    private fun summaryOf(rule: RegexCastRule): String {
        val pool = poolNames[rule.poolId] ?: rule.poolId
        val item = rule.itemId.takeIf { it.isNotBlank() }?.let { itemNames[it] ?: it }
            ?: context.getString(R.string.regex_cast_random)
        val action = if (rule.poolKind == RegexCastRule.POOL_BGM) {
            context.getString(R.string.regex_cast_summary_sound, item, pool)
        } else {
            context.getString(R.string.regex_cast_summary_voice, item, pool)
        }
        // 变声器是那条规则自己的一列（regex_cast_rules.voiceEffect），列表里也得看得见，
        // 否则用户设完只听到声音变了、认不出是谁在变。配乐那一种不念文字，压根不带这一截。
        val effect = rule.voiceEffect
            .takeIf { it.isNotBlank() && rule.poolKind != RegexCastRule.POOL_BGM }
            ?.let { context.getString(R.string.regex_cast_effect_summary, it) }
            .orEmpty()
        return context.getString(R.string.regex_cast_summary, rule.pattern, action) + effect
    }

    private fun launchIo(block: suspend () -> Unit) {
        execute { runCatching { block() } }
    }
}
