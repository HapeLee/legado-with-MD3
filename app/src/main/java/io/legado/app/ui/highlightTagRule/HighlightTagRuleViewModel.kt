package io.legado.app.ui.highlightTagRule

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.data.repository.HighlightTagRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HighlightTagRuleViewModel(
    application: Application,
    uploadRepository: UploadRepository
) : BaseRuleViewModel<HighlightTagRuleItemUi, HighlightTagRule, Long, HighlightTagRuleUiState>(
    application,
    HighlightTagRuleUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    private val repository = HighlightTagRuleRepository()

    override val rawDataFlow: Flow<List<HighlightTagRule>> = repository.flowAll()

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
                val state = uiState.value
                uploadSelectedRules(state.selectedIds, state.items)
            }
            is HighlightTagRuleIntent.ExportSelection -> {
                val state = uiState.value
                exportToUri(intent.uri, state.items, state.selectedIds)
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
            is HighlightTagRuleIntent.ImportSource -> importSource(intent.text)
            HighlightTagRuleIntent.CancelImport -> cancelImport()
            is HighlightTagRuleIntent.ToggleImportSelection -> toggleImportSelection(intent.index)
            is HighlightTagRuleIntent.ToggleImportAll -> toggleImportAll(intent.isSelected)
            is HighlightTagRuleIntent.UpdateImportItem -> updateImportItem(intent.index, intent.rule)
            HighlightTagRuleIntent.SaveImportedRules -> saveImportedRules()
        }
    }

    override fun filterData(
        data: List<HighlightTagRule>,
        searchKey: String,
        groupFilter: String
    ): List<HighlightTagRule> {
        val key = groupFilter.ifEmpty { searchKey }
        val filtered = if (key.isEmpty()) data else {
            data.filter {
                it.title.contains(key, ignoreCase = true) ||
                        it.pattern.contains(key, ignoreCase = true)
            }
        }
        return filtered.sortedBy { it.order }
    }

    override fun composeUiState(
        items: List<HighlightTagRuleItemUi>,
        selectedIds: Set<Long>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<HighlightTagRule>
    ): HighlightTagRuleUiState {
        return HighlightTagRuleUiState(
            items = items.toImmutableList(),
            selectedIds = selectedIds.toImmutableSet(),
            searchKey = _searchKey.value,
            interaction = InteractionState(
                isSearchMode = isSearch,
                isUploading = isUploading || (importState is BaseImportUiState.Loading),
                isLoading = false
            )
        )
    }

    override fun HighlightTagRule.toUiItem() = HighlightTagRuleItemUi(
        id = id,
        displayName = title.ifBlank { pattern },
        pattern = pattern,
        isEnabled = enabled,
        rule = this
    )

    override fun ruleItemToEntity(item: HighlightTagRuleItemUi): HighlightTagRule = item.rule

    override suspend fun generateJson(entities: List<HighlightTagRule>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<HighlightTagRule> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<HighlightTagRule>(text).getOrThrow()
            text.isJsonObject() -> listOf(GSON.fromJsonObject<HighlightTagRule>(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: HighlightTagRule, oldRule: HighlightTagRule): Boolean {
        return newRule.title != oldRule.title
                || newRule.pattern != oldRule.pattern
                || newRule.enabled != oldRule.enabled
    }

    override suspend fun findOldRule(newRule: HighlightTagRule): HighlightTagRule? {
        return repository.findById(newRule.id)
    }

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<HighlightTagRule> ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val rulesToSave = state.items
                .filter { it.isSelected }
                .map { it.data }
            repository.insert(*rulesToSave.toTypedArray())
            withContext(Dispatchers.Main) {
                _importState.value = BaseImportUiState.Idle
            }
        }
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
        context.sendToClip(GSON.toJson(rule))
    }

    fun pasteRule(): HighlightTagRule? {
        val text = context.getClipText()
        if (text.isNullOrBlank()) {
            context.toastOnUi("剪贴板没有内容")
            return null
        }
        return try {
            GSON.fromJsonObject<HighlightTagRule>(text).getOrThrow()
        } catch (e: Exception) {
            context.toastOnUi("格式不对")
            null
        }
    }
}
