package io.legado.app.feature.tagrules.highlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.core.rules.RuleEntitySpec
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.core.rules.RuleTransferUseCase
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.JsonCodec
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.data.repository.HighlightTagRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
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

/**
 * 高亮标签规则的列表/导入/导出编排。
 *
 * M1-3a：剪贴板改为**构造注入**的平台契约（`Clipboard`）；JSON 编解码换用
 * `:core:platform` 的 `JsonCodec`，不再直连 app 侧 `GSON` 门面。输出字节与 `GSON` 一致。
 * 用户提示本来就经由 `_effects`（已是对齐目标的形态），本次不动。
 *
 * M1-3b：**不再继承 `BaseRuleViewModel`**——导入/导出/上传的无 UI 编排下沉到
 * [RuleTransferUseCase]，列表/搜索/选择留在本类。本类因此只剩 `androidx.lifecycle.ViewModel`
 * 一个 Android 依赖，也不再持有 `Application`。本 Feature 本就没有 `R.string` 用法。
 */
class HighlightTagRuleViewModel(
    uploadRepository: UploadRepository,
    transferPlatform: RuleTransferPlatform,
    private val clipboard: Clipboard,
    private val repository: HighlightTagRuleRepository,
) : ViewModel() {

    private val _effects = MutableSharedFlow<HighlightTagRuleEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val _searchKey = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSearchMode = MutableStateFlow(false)
    private val _localItems = MutableStateFlow<List<HighlightTagRuleItemUi>?>(null)

    private val transfer = RuleTransferUseCase(
        scope = viewModelScope,
        spec = HighlightTagRuleTransferSpec(repository),
        transferPlatform = transferPlatform,
        uploadRepository = uploadRepository,
    )

    val importState = transfer.importState
    val events = transfer.events

    private val itemsFlow: Flow<List<HighlightTagRuleItemUi>> = combine(
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

    val uiState: StateFlow<HighlightTagRuleUiState> by lazy {
        combine(
            itemsFlow,
            _selectedIds,
            _isSearchMode,
            transfer.isUploading,
            transfer.importState
        ) { items, selectedIds, isSearch, isUploading, importState ->
            HighlightTagRuleUiState(
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
            initialValue = HighlightTagRuleUiState(interaction = InteractionState(isLoading = true))
        )
    }

    fun onIntent(intent: HighlightTagRuleIntent) {
        when (intent) {
            is HighlightTagRuleIntent.SetSearchMode -> setSearchMode(intent.active)
            is HighlightTagRuleIntent.UpdateSearchQuery -> setSearchKey(intent.query)
            HighlightTagRuleIntent.ClearSelection -> setSelection(emptySet())
            HighlightTagRuleIntent.SelectAll -> selectAll()
            HighlightTagRuleIntent.InvertSelection -> invertSelection()
            is HighlightTagRuleIntent.SetSelection -> setSelection(intent.ids)
            is HighlightTagRuleIntent.ToggleSelection -> toggleSelection(intent.id)
            HighlightTagRuleIntent.EnableSelection -> {
                enableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            HighlightTagRuleIntent.DisableSelection -> {
                disableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            HighlightTagRuleIntent.DeleteSelection -> {
                delSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            HighlightTagRuleIntent.UploadSelection -> {
                transfer.upload(selectedRules())
            }
            is HighlightTagRuleIntent.ExportSelection -> {
                transfer.export(intent.uri, selectedRules())
            }
            is HighlightTagRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            HighlightTagRuleIntent.SaveSortOrder -> saveSortOrder()
            is HighlightTagRuleIntent.SaveRule -> {
                if (intent.isNew) {
                    insert(intent.rule)
                } else {
                    update(intent.rule)
                }
            }
            is HighlightTagRuleIntent.DeleteRule -> delete(intent.rule)
            is HighlightTagRuleIntent.SetRuleEnabled -> update(intent.rule.copy(enabled = intent.enabled))
            is HighlightTagRuleIntent.CopyRule -> copyRule(intent.rule)
            is HighlightTagRuleIntent.ImportSource -> transfer.importSource(intent.text)
            HighlightTagRuleIntent.CancelImport -> transfer.cancelImport()
            is HighlightTagRuleIntent.ToggleImportSelection -> transfer.toggleImportSelection(intent.index)
            is HighlightTagRuleIntent.ToggleImportAll -> transfer.toggleImportAll(intent.isSelected)
            is HighlightTagRuleIntent.UpdateImportItem -> transfer.updateImportItem(intent.index, intent.rule)
            HighlightTagRuleIntent.SaveImportedRules -> transfer.saveImportedRules()
        }
    }

    private fun filterRules(data: List<HighlightTagRule>, searchKey: String): List<HighlightTagRule> {
        val filtered = if (searchKey.isEmpty()) data else {
            data.filter {
                it.title.contains(searchKey, ignoreCase = true) ||
                        it.pattern.contains(searchKey, ignoreCase = true)
            }
        }
        return filtered.sortedBy { it.order }
    }

    private fun HighlightTagRule.toUiItem() = HighlightTagRuleItemUi(
        id = id,
        displayName = title.ifBlank { pattern },
        pattern = pattern,
        isEnabled = enabled,
        rule = this
    )

    /** 当前选中项对应的实体；`ExportSelection` / `UploadSelection` 共用。 */
    private fun selectedRules(): List<HighlightTagRule> {
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

    fun enableSelectionByIds(ids: Set<Long>) {
        viewModelScope.launch { repository.enableByIds(ids) }
    }

    fun disableSelectionByIds(ids: Set<Long>) {
        viewModelScope.launch { repository.disableByIds(ids) }
    }

    fun delSelectionByIds(ids: Set<Long>) {
        viewModelScope.launch {
            repository.deleteByIds(ids)
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

    fun update(vararg rule: HighlightTagRule) = viewModelScope.launch { repository.update(*rule) }
    fun insert(vararg rule: HighlightTagRule) = viewModelScope.launch { repository.insert(*rule) }
    fun delete(vararg rule: HighlightTagRule) = viewModelScope.launch { repository.delete(*rule) }

    fun copyRule(rule: HighlightTagRule) {
        clipboard.setText(JsonCodec.toJson(rule))
    }

    fun pasteRule(): HighlightTagRule? {
        val text = clipboard.getText()
        if (text.isNullOrBlank()) {
            _effects.tryEmit(HighlightTagRuleEffect.ShowMessage("剪贴板没有内容"))
            return null
        }
        return try {
            JsonCodec.fromJsonObject(text, HighlightTagRule::class) ?: throw Exception("格式不对")
        } catch (e: Exception) {
            _effects.tryEmit(HighlightTagRuleEffect.ShowMessage("格式不对"))
            null
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
}

/**
 * `HighlightTagRule` 在导入/导出流程里的实体语义。
 *
 * 与标签分组规则的差异只有两点：变化判定包含 `enabled`；落库后不需要重新应用到书架。
 */
private class HighlightTagRuleTransferSpec(
    private val repository: HighlightTagRuleRepository,
) : RuleEntitySpec<HighlightTagRule> {

    override suspend fun generateJson(entities: List<HighlightTagRule>): String =
        JsonCodec.toJson(entities)

    override fun parseImportRules(text: String): List<HighlightTagRule> {
        return when {
            text.isJsonArray() -> JsonCodec.decodeList(text, HighlightTagRule::class)
                ?: throw Exception("格式不正确")
            text.isJsonObject() -> listOf(
                JsonCodec.fromJsonObject(text, HighlightTagRule::class)
                    ?: throw Exception("格式不正确")
            )
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: HighlightTagRule, oldRule: HighlightTagRule): Boolean {
        return newRule.title != oldRule.title
                || newRule.pattern != oldRule.pattern
                || newRule.enabled != oldRule.enabled
    }

    override suspend fun findOldRule(newRule: HighlightTagRule): HighlightTagRule? =
        repository.findById(newRule.id)

    override suspend fun persist(entities: List<HighlightTagRule>) {
        repository.insert(*entities.toTypedArray())
    }
}
