package io.legado.app.ui.tagGroupRule

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.data.repository.TagGroupRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.help.book.applyTagGroupRules
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

class TagGroupRuleViewModel(
    application: Application,
    uploadRepository: UploadRepository
) : BaseRuleViewModel<TagGroupRuleItemUi, TagGroupRule, Long, TagGroupRuleUiState>(
    application,
    TagGroupRuleUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    private val repository = TagGroupRuleRepository()

    override val rawDataFlow: Flow<List<TagGroupRule>> = repository.flowAll()

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
                val state = uiState.value
                uploadSelectedRules(state.selectedIds, state.items)
            }
            is TagGroupRuleIntent.ExportSelection -> {
                val state = uiState.value
                exportToUri(intent.uri, state.items, state.selectedIds)
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
            is TagGroupRuleIntent.ImportSource -> importSource(intent.text)
            TagGroupRuleIntent.CancelImport -> cancelImport()
            is TagGroupRuleIntent.ToggleImportSelection -> toggleImportSelection(intent.index)
            is TagGroupRuleIntent.ToggleImportAll -> toggleImportAll(intent.isSelected)
            is TagGroupRuleIntent.UpdateImportItem -> updateImportItem(intent.index, intent.rule)
            TagGroupRuleIntent.SaveImportedRules -> saveImportedRules()
            TagGroupRuleIntent.SyncGroups -> syncGroups()
        }
    }

    override fun filterData(
        data: List<TagGroupRule>,
        searchKey: String,
        groupFilter: String
    ): List<TagGroupRule> {
        val key = groupFilter.ifEmpty { searchKey }
        val filtered = if (key.isEmpty()) data else {
            data.filter {
                it.groupName.contains(key, ignoreCase = true) ||
                        it.pattern.contains(key, ignoreCase = true)
            }
        }
        return filtered.sortedBy { it.order }
    }

    override fun composeUiState(
        items: List<TagGroupRuleItemUi>,
        selectedIds: Set<Long>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<TagGroupRule>
    ): TagGroupRuleUiState {
        return TagGroupRuleUiState(
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

    override fun TagGroupRule.toUiItem() = TagGroupRuleItemUi(
        id = id,
        displayName = groupName.ifBlank { pattern },
        pattern = pattern,
        groupName = groupName,
        rule = this
    )

    override fun ruleItemToEntity(item: TagGroupRuleItemUi): TagGroupRule = item.rule

    override suspend fun generateJson(entities: List<TagGroupRule>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<TagGroupRule> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<TagGroupRule>(text).getOrThrow()
            text.isJsonObject() -> listOf(GSON.fromJsonObject<TagGroupRule>(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: TagGroupRule, oldRule: TagGroupRule): Boolean {
        return newRule.groupName != oldRule.groupName
                || newRule.pattern != oldRule.pattern
    }

    override suspend fun findOldRule(newRule: TagGroupRule): TagGroupRule? {
        return repository.findById(newRule.id)
    }

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<TagGroupRule> ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val rulesToSave = state.items
                .filter { it.isSelected }
                .map { it.data }
            repository.insert(*rulesToSave.toTypedArray())
            autoApplyRules()
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

    fun delSelectionByIds(ids: Set<Long>) {
        viewModelScope.launch {
            repository.deleteByIds(ids)
            autoApplyRules()
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
        autoApplyRules()
    }

    fun insert(vararg rule: TagGroupRule) = viewModelScope.launch {
        repository.insert(*rule)
        autoApplyRules()
    }

    fun delete(vararg rule: TagGroupRule) = viewModelScope.launch {
        repository.delete(*rule)
        autoApplyRules()
    }

    private suspend fun autoApplyRules() {
        withContext(Dispatchers.IO) {
            val books = appDb.bookDao.getAll()
            val rules = appDb.tagGroupRuleDao.getAll()
            applyTagGroupRules(books, rules)
        }
    }

    fun copyRule(rule: TagGroupRule) {
        context.sendToClip(GSON.toJson(rule))
    }

    fun pasteRule(): TagGroupRule? {
        val text = context.getClipText()
        if (text.isNullOrBlank()) {
            context.toastOnUi("剪贴板没有内容")
            return null
        }
        return try {
            GSON.fromJsonObject<TagGroupRule>(text).getOrThrow()
        } catch (e: Exception) {
            context.toastOnUi("格式不对")
            null
        }
    }

    private fun syncGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            val books = appDb.bookDao.getAll()
            val rules = appDb.tagGroupRuleDao.getAll()
            applyTagGroupRules(books, rules)
            withContext(Dispatchers.Main) {
                context.toastOnUi(R.string.tag_group_sync_complete)
            }
        }
    }
}
