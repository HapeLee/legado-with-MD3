package io.legado.app.ui.book.toc.rule

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.repository.TxtTocRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.help.DefaultData
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TxtTocRuleViewModel(
    application: Application,
    uploadRepository: UploadRepository
) : BaseRuleViewModel<TxtTocRuleItemUi, TxtTocRule, Long, TxtTocRuleUiState>(
    application,
    TxtTocRuleUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    private val repository = TxtTocRuleRepository()

    override val rawDataFlow: Flow<List<TxtTocRule>> = repository.flowAll()

    fun onIntent(intent: TxtTocRuleIntent) {
        when (intent) {
            is TxtTocRuleIntent.SetSearchMode -> setSearchMode(intent.active)
            is TxtTocRuleIntent.UpdateSearchQuery -> setSearchKey(intent.query)
            TxtTocRuleIntent.ClearSelection -> setSelection(emptySet())
            TxtTocRuleIntent.SelectAll -> selectAll()
            TxtTocRuleIntent.InvertSelection -> invertSelection()
            is TxtTocRuleIntent.SetSelection -> setSelection(intent.ids)
            is TxtTocRuleIntent.ToggleSelection -> toggleSelection(intent.id)
            TxtTocRuleIntent.EnableSelection -> {
                enableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            TxtTocRuleIntent.DisableSelection -> {
                disableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            TxtTocRuleIntent.DeleteSelection -> {
                delSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            TxtTocRuleIntent.UploadSelection -> {
                val state = uiState.value
                uploadSelectedRules(state.selectedIds, state.items)
            }
            is TxtTocRuleIntent.ExportSelection -> {
                val state = uiState.value
                exportToUri(intent.uri, state.items, state.selectedIds)
            }
            is TxtTocRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            TxtTocRuleIntent.SaveSortOrder -> saveSortOrder()
            is TxtTocRuleIntent.SaveRule -> save(intent.rule, intent.isNew)
            is TxtTocRuleIntent.DeleteRule -> delete(intent.rule)
            is TxtTocRuleIntent.SetRuleEnabled -> update(intent.rule.copy(enable = intent.enabled))
            is TxtTocRuleIntent.CopyRule -> copyRule(intent.rule)
            is TxtTocRuleIntent.ImportSource -> importSource(intent.text)
            TxtTocRuleIntent.CancelImport -> cancelImport()
            is TxtTocRuleIntent.ToggleImportSelection -> toggleImportSelection(intent.index)
            is TxtTocRuleIntent.ToggleImportAll -> toggleImportAll(intent.isSelected)
            is TxtTocRuleIntent.UpdateImportItem -> updateImportItem(intent.index, intent.rule)
            TxtTocRuleIntent.SaveImportedRules -> saveImportedRules()
            TxtTocRuleIntent.ImportBuiltInRules -> importBuiltInRules()
        }
    }

    override fun TxtTocRule.toUiItem() =
        TxtTocRuleItemUi(id, name, enable, this, example = example ?: "")

    @Suppress("DEPRECATION")
    override fun filterData(data: List<TxtTocRule>, key: String): List<TxtTocRule> {
        val filtered = if (key.isEmpty()) data
        else data.filter { it.name.contains(key, ignoreCase = true) }
        return filtered.sortedBy { it.serialNumber }
    }

    override fun composeUiState(
        items: List<TxtTocRuleItemUi>,
        selectedIds: Set<Long>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<TxtTocRule>
    ): TxtTocRuleUiState {
        return TxtTocRuleUiState(
            items = items,
            selectedIds = selectedIds,
            searchKey = _searchKey.value,
            interaction = InteractionState(
                isSearchMode = isSearch,
                isUploading = isUploading || (importState is BaseImportUiState.Loading),
                isLoading = false
            )
        )
    }

    private fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.saveOrder(currentLocal.map { it.rule })
            _localItems.value = null
        }
    }

    private fun save(rule: TxtTocRule, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) {
                repository.insert(rule)
            } else {
                repository.update(rule)
            }
        }
    }

    private fun update(vararg rules: TxtTocRule) = viewModelScope.launch { repository.update(*rules) }
    private fun insert(vararg rules: TxtTocRule) = viewModelScope.launch { repository.insert(*rules) }
    private fun delete(vararg rules: TxtTocRule) = viewModelScope.launch { repository.delete(*rules) }

    fun enableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.enableByIds(ids, true) }

    fun disableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.enableByIds(ids, false) }

    fun delSelectionByIds(ids: Set<Long>) = viewModelScope.launch { repository.deleteByIds(ids) }

    private fun selectAll() {
        setSelection(uiState.value.items.map { it.id }.toSet())
    }

    private fun invertSelection() {
        val state = uiState.value
        setSelection(state.items.map { it.id }.toSet() - state.selectedIds)
    }

    override suspend fun generateJson(entities: List<TxtTocRule>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<TxtTocRule> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<TxtTocRule>(text).getOrThrow()
            text.isJsonObject() -> listOf(GSON.fromJsonObject<TxtTocRule>(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: TxtTocRule, oldRule: TxtTocRule): Boolean {
        return newRule.name != oldRule.name || newRule.rule != oldRule.rule || newRule.enable != oldRule.enable
    }

    override suspend fun findOldRule(newRule: TxtTocRule): TxtTocRule? {
        return null
    }

    override fun ruleItemToEntity(item: TxtTocRuleItemUi): TxtTocRule = item.rule

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<TxtTocRule> ?: return
        viewModelScope.launch {
            val rulesToSave = state.items.filter { it.isSelected }.map { it.data }
            repository.insert(*rulesToSave.toTypedArray())
            _importState.value = BaseImportUiState.Idle
        }
    }

    private fun copyRule(rule: TxtTocRule) {
        context.sendToClip(GSON.toJson(rule))
    }

    private fun importBuiltInRules() {
        viewModelScope.launch(Dispatchers.IO) {
            DefaultData.importDefaultTocRules()
            context.toastOnUi(R.string.import_built_in_rules)
        }
    }

    fun pasteRule(): TxtTocRule? {
        val text = context.getClipText()
        if (text.isNullOrBlank()) {
            context.toastOnUi(R.string.clipboard_empty)
            return null
        }
        return try {
            GSON.fromJsonObject<TxtTocRule>(text).getOrThrow()
        } catch (e: Exception) {
            context.toastOnUi(R.string.invalid_format)
            null
        }
    }
}
