package io.legado.app.feature.tagrules.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.core.rules.RuleEntitySpec
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.core.rules.RuleTransferUseCase
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.platform.Toaster
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.data.repository.TagGroupRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 标签分组规则的列表/导入/导出编排。
 *
 * M1-3a：剪贴板与轻提示改为**构造注入**的平台契约（`Clipboard` / `Toaster`）；
 * JSON 编解码换用 `:core:platform` 的 `JsonCodec`，不再直连 app 侧 `GSON` 门面。
 *
 * M1-3b：**不再继承 `BaseRuleViewModel`**（那是个吃 `android.app.Application` 的
 * `AndroidViewModel`，也是本 Feature 进不了 `commonMain` 的最后一道墙）。导入/导出/上传的
 * 无 UI 编排下沉到 [RuleTransferUseCase]，列表/搜索/选择这些纯 UI 状态留在本类。
 * 本类因此只剩 `androidx.lifecycle.ViewModel` 一个 Android 依赖，且不再持有 `Context`：
 * 「同步完成」的提示文案改由 effect 通知 UI，由界面自己取 `R.string.tag_group_sync_complete`
 * ——文案属于 UI 层，VM 持有 `R` 会让多语言与 CMP 资源迁移互相牵制。
 *
 * 行为与迁移前逐字一致：导出内容、失败文案、导入分类、落库范围、排序与过滤规则均未变。
 */
class TagGroupRuleViewModel(
    uploadRepository: UploadRepository,
    transferPlatform: RuleTransferPlatform,
    private val clipboard: Clipboard,
    private val toaster: Toaster,
    private val bookGroupMutationGateway: BookGroupMutationGateway,
    private val repository: TagGroupRuleRepository,
) : ViewModel() {

    private val _searchKey = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSearchMode = MutableStateFlow(false)
    private val _localItems = MutableStateFlow<List<TagGroupRuleItemUi>?>(null)

    private val _effects = MutableSharedFlow<TagGroupRuleEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val transfer = RuleTransferUseCase(
        scope = viewModelScope,
        spec = TagGroupRuleTransferSpec(repository, bookGroupMutationGateway),
        transferPlatform = transferPlatform,
        uploadRepository = uploadRepository,
    )

    val importState = transfer.importState
    val events = transfer.events

    private val itemsFlow: Flow<List<TagGroupRuleItemUi>> = combine(
        repository.flowAll(),
        _searchKey,
        _localItems
    ) { data, searchKey, local ->
        if (local != null && searchKey.isEmpty()) {
            local
        } else {
            filterRules(data, searchKey).map { it.toUiItem() }
        }
    }

    val uiState: StateFlow<TagGroupRuleUiState> by lazy {
        combine(
            itemsFlow,
            _selectedIds,
            _isSearchMode,
            transfer.isUploading,
            transfer.importState
        ) { items, selectedIds, isSearch, isUploading, importState ->
            TagGroupRuleUiState(
                items = items.toImmutableList(),
                selectedIds = selectedIds.toImmutableSet(),
                searchKey = _searchKey.value,
                interaction = InteractionState(
                    isSearchMode = isSearch,
                    isUploading = isUploading || (importState is BaseImportUiState.Loading),
                    isLoading = false
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TagGroupRuleUiState(interaction = InteractionState(isLoading = true))
        )
    }

    fun onIntent(intent: TagGroupRuleIntent) {
        when (intent) {
            is TagGroupRuleIntent.SetSearchMode -> setSearchMode(intent.active)
            is TagGroupRuleIntent.UpdateSearchQuery -> setSearchKey(intent.query)
            TagGroupRuleIntent.ClearSelection -> setSelection(emptySet())
            TagGroupRuleIntent.SelectAll -> selectAll()
            TagGroupRuleIntent.InvertSelection -> invertSelection()
            is TagGroupRuleIntent.SetSelection -> setSelection(intent.ids)
            is TagGroupRuleIntent.ToggleSelection -> toggleSelection(intent.id)
            TagGroupRuleIntent.DeleteSelection -> {
                delSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            TagGroupRuleIntent.UploadSelection -> {
                transfer.upload(selectedRules())
            }
            is TagGroupRuleIntent.ExportSelection -> {
                transfer.export(intent.uri, selectedRules())
            }
            is TagGroupRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            TagGroupRuleIntent.SaveSortOrder -> saveSortOrder()
            is TagGroupRuleIntent.SaveRule -> {
                if (intent.isNew) {
                    insert(intent.rule)
                } else {
                    update(intent.rule)
                }
            }
            is TagGroupRuleIntent.DeleteRule -> delete(intent.rule)
            is TagGroupRuleIntent.CopyRule -> copyRule(intent.rule)
            is TagGroupRuleIntent.ImportSource -> transfer.importSource(intent.text)
            TagGroupRuleIntent.CancelImport -> transfer.cancelImport()
            is TagGroupRuleIntent.ToggleImportSelection -> transfer.toggleImportSelection(intent.index)
            is TagGroupRuleIntent.ToggleImportAll -> transfer.toggleImportAll(intent.isSelected)
            is TagGroupRuleIntent.UpdateImportItem -> transfer.updateImportItem(intent.index, intent.rule)
            TagGroupRuleIntent.SaveImportedRules -> transfer.saveImportedRules()
            TagGroupRuleIntent.SyncGroups -> syncGroups()
        }
    }

    private fun filterRules(data: List<TagGroupRule>, searchKey: String): List<TagGroupRule> {
        val filtered = if (searchKey.isEmpty()) data else {
            data.filter {
                it.groupName.contains(searchKey, ignoreCase = true) ||
                        it.pattern.contains(searchKey, ignoreCase = true)
            }
        }
        return filtered.sortedBy { it.order }
    }

    private fun TagGroupRule.toUiItem() = TagGroupRuleItemUi(
        id = id,
        displayName = groupName.ifBlank { pattern },
        pattern = pattern,
        groupName = groupName,
        rule = this
    )

    /** 当前选中项对应的实体；`ExportSelection` / `UploadSelection` 共用。 */
    private fun selectedRules(): List<TagGroupRule> {
        val state = uiState.value
        return state.items
            .filter { state.selectedIds.contains(it.id) }
            .map { it.rule }
    }

    fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.moveOrder(currentLocal.map { it.rule })
            _localItems.value = null
        }
    }

    fun delSelectionByIds(ids: Set<Long>) {
        viewModelScope.launch {
            repository.deleteByIds(ids)
            applyRules()
            _selectedIds.update { it - ids }
        }
    }

    private fun selectAll() {
        setSelection(uiState.value.items.map { it.id }.toSet())
    }

    private fun invertSelection() {
        val state = uiState.value
        setSelection(state.items.map { it.id }.toSet() - state.selectedIds)
    }

    fun update(vararg rule: TagGroupRule) = viewModelScope.launch {
        repository.update(*rule)
        bookGroupMutationGateway.applyTagGroupRulesToAllBooks()
    }

    fun insert(vararg rule: TagGroupRule) = viewModelScope.launch {
        repository.insert(*rule)
        bookGroupMutationGateway.applyTagGroupRulesToAllBooks()
    }

    fun delete(vararg rule: TagGroupRule) = viewModelScope.launch {
        repository.delete(*rule)
        bookGroupMutationGateway.applyTagGroupRulesToAllBooks()
    }

    fun copyRule(rule: TagGroupRule) {
        clipboard.setText(JsonCodec.toJson(rule))
    }

    fun pasteRule(): TagGroupRule? {
        val text = clipboard.getText()
        if (text.isNullOrBlank()) {
            toaster.toast("剪贴板没有内容")
            return null
        }
        return try {
            JsonCodec.fromJsonObject(text, TagGroupRule::class) ?: throw Exception("格式不对")
        } catch (e: Exception) {
            toaster.toast("格式不对")
            null
        }
    }

    private fun syncGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            applyRules()
            withContext(Dispatchers.Main) {
                _effects.tryEmit(TagGroupRuleEffect.SyncGroupsCompleted)
            }
        }
    }

    // ---------- 列表状态 ----------

    private fun moveItemInList(from: Int, to: Int) {
        val currentList = uiState.value.items.toMutableList()
        if (from !in currentList.indices || to !in currentList.indices) return
        val item = currentList.removeAt(from)
        currentList.add(to, item)
        _localItems.value = currentList
    }

    private fun setSearchKey(key: String?) {
        _localItems.value = null
        _searchKey.value = key ?: ""
    }

    private fun setSearchMode(active: Boolean) {
        _isSearchMode.value = active
        if (!active) setSearchKey("")
    }

    private fun toggleSelection(id: Long) {
        _selectedIds.update { if (it.contains(id)) it - id else it + id }
    }

    private fun setSelection(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    /**
     * 规则变更后立即应用到所有书。
     *
     * `viewModelScope` 默认跑在 Main 上，而这是「批量写库 + 重算书架分组」，必须切到 IO——
     * 与迁移前 `BaseRuleViewModel` 的 `autoApplyRules()` 逐字一致（`RuleTransferUseCase`
     * 的 `persist` 走的是它自己的 `Dispatchers.IO`，不需要这层包裹）。
     */
    private suspend fun applyRules() {
        withContext(Dispatchers.IO) {
            bookGroupMutationGateway.applyTagGroupRulesToAllBooks()
        }
    }
}

/**
 * `TagGroupRule` 在导入/导出流程里的实体语义。
 *
 * `persist` 与原 `saveImportedRules` 一致：落库后立即把规则应用到所有书——顺序不能反，
 * 否则新导入的分组规则不会体现在书架分组上。
 */
private class TagGroupRuleTransferSpec(
    private val repository: TagGroupRuleRepository,
    private val bookGroupMutationGateway: BookGroupMutationGateway,
) : RuleEntitySpec<TagGroupRule> {

    override suspend fun generateJson(entities: List<TagGroupRule>): String =
        JsonCodec.toJson(entities)

    override fun parseImportRules(text: String): List<TagGroupRule> {
        return when {
            text.isJsonArray() -> JsonCodec.decodeList(text, TagGroupRule::class)
                ?: throw Exception("格式不正确")
            text.isJsonObject() -> listOf(
                JsonCodec.fromJsonObject(text, TagGroupRule::class)
                    ?: throw Exception("格式不正确")
            )
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: TagGroupRule, oldRule: TagGroupRule): Boolean {
        return newRule.groupName != oldRule.groupName
                || newRule.pattern != oldRule.pattern
    }

    override suspend fun findOldRule(newRule: TagGroupRule): TagGroupRule? =
        repository.findById(newRule.id)

    override suspend fun persist(entities: List<TagGroupRule>) {
        repository.insert(*entities.toTypedArray())
        bookGroupMutationGateway.applyTagGroupRulesToAllBooks()
    }
}
