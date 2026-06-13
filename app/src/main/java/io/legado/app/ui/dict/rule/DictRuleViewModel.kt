package io.legado.app.ui.dict.rule

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
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

class DictRuleViewModel(
    application: Application,
    uploadRepository: UploadRepository
) : BaseRuleViewModel<DictRuleItemUi, DictRule, String, DictRuleUiState>(
    application,
    DictRuleUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    private val repository = DictRuleRepository()

    override val rawDataFlow: Flow<List<DictRule>> = repository.flowAll()

    fun onIntent(intent: DictRuleIntent) {
        when (intent) {
            is DictRuleIntent.SetSearchMode -> setSearchMode(intent.active)
            is DictRuleIntent.UpdateSearchQuery -> setSearchKey(intent.query)
            DictRuleIntent.ClearSelection -> setSelection(emptySet())
            DictRuleIntent.SelectAll -> selectAll()
            DictRuleIntent.InvertSelection -> invertSelection()
            is DictRuleIntent.SetSelection -> setSelection(intent.ids)
            is DictRuleIntent.ToggleSelection -> toggleSelection(intent.id)
            DictRuleIntent.EnableSelection -> {
                enableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            DictRuleIntent.DisableSelection -> {
                disableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            DictRuleIntent.DeleteSelection -> {
                delSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            DictRuleIntent.UploadSelection -> {
                val state = uiState.value
                uploadSelectedRules(state.selectedIds, state.items)
            }
            is DictRuleIntent.ExportSelection -> {
                val state = uiState.value
                exportToUri(intent.uri, state.items, state.selectedIds)
            }
            is DictRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            DictRuleIntent.SaveSortOrder -> saveSortOrder()
            is DictRuleIntent.SaveRule -> {
                if (intent.isNew) {
                    insert(intent.rule)
                } else {
                    update(intent.rule)
                }
            }
            is DictRuleIntent.DeleteRule -> delete(intent.rule)
            is DictRuleIntent.SetRuleEnabled -> update(intent.rule.copy(enabled = intent.enabled))
            is DictRuleIntent.CopyRule -> copyRule(intent.rule)
            is DictRuleIntent.ImportSource -> importSource(intent.text)
            DictRuleIntent.CancelImport -> cancelImport()
            is DictRuleIntent.ToggleImportSelection -> toggleImportSelection(intent.index)
            is DictRuleIntent.ToggleImportAll -> toggleImportAll(intent.isSelected)
            is DictRuleIntent.UpdateImportItem -> updateImportItem(intent.index, intent.rule)
            DictRuleIntent.SaveImportedRules -> saveImportedRules()
        }
    }

    override fun filterData(
        data: List<DictRule>,
        searchKey: String,
        groupFilter: String
    ): List<DictRule> {
        val key = groupFilter.ifEmpty { searchKey }
        val filtered = if (key.isEmpty()) data else {
            data.filter { it.name.contains(key, ignoreCase = true) }
        }
        return filtered.sortedBy { it.sortNumber }
    }

    override fun composeUiState(
        items: List<DictRuleItemUi>,
        selectedIds: Set<String>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<DictRule>
    ): DictRuleUiState {
        return DictRuleUiState(
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

    override fun DictRule.toUiItem() = DictRuleItemUi(name, urlRule, showRule, enabled, this)
    override fun ruleItemToEntity(item: DictRuleItemUi): DictRule = item.rule

    override suspend fun generateJson(entities: List<DictRule>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<DictRule> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<DictRule>(text).getOrThrow()
            text.isJsonObject() -> listOf(GSON.fromJsonObject<DictRule>(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: DictRule, oldRule: DictRule): Boolean {
        return newRule.name != oldRule.name
                || newRule.urlRule != oldRule.urlRule
                || newRule.showRule != oldRule.showRule
                || newRule.enabled != oldRule.enabled
    }

    override suspend fun findOldRule(newRule: DictRule): DictRule? {
        return repository.findById(newRule.name)
    }

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<DictRule> ?: return
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

    fun enableSelectionByIds(ids: Set<String>) {
        viewModelScope.launch { repository.enableByIds(ids) }
    }

    fun disableSelectionByIds(ids: Set<String>) {
        viewModelScope.launch { repository.disableByIds(ids) }
    }

    fun delSelectionByIds(ids: Set<String>) {
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

    fun update(vararg rule: DictRule) = viewModelScope.launch { repository.update(*rule) }
    fun insert(vararg rule: DictRule) = viewModelScope.launch { repository.insert(*rule) }
    fun delete(vararg dictRule: DictRule) = viewModelScope.launch { repository.delete(*dictRule) }

    fun copyRule(dictRule: DictRule) {
        context.sendToClip(GSON.toJson(dictRule))
    }

    fun pasteRule(): DictRule? {
        val text = context.getClipText()
        if (text.isNullOrBlank()) {
            context.toastOnUi("剪贴板没有内容")
            return null
        }
        return try {
            GSON.fromJsonObject<DictRule>(text).getOrThrow()
        } catch (e: Exception) {
            context.toastOnUi("格式不对")
            null
        }
    }
}
