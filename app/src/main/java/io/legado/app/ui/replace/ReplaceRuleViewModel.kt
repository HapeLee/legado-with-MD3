package io.legado.app.ui.replace

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleEvent
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.help.ReplaceAnalyzer
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.putPrefString
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReplaceRuleViewModel(
    application: Application,
    uploadRepository: UploadRepository
) : BaseRuleViewModel<ReplaceRuleItemUi, ReplaceRule, Long, ReplaceRuleUiState>(
    application,
    ReplaceRuleUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    private val repository = ReplaceRuleRepository()
    private val _sortMode = MutableStateFlow(context.getPrefString(PreferKey.replaceSortMode, "desc") ?: "desc")
    val sortMode = _sortMode.asStateFlow()
    private val _group = MutableStateFlow<String?>(null)
    val group = _group.asStateFlow()

    val allGroups: StateFlow<List<String>> = repository.flowGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onIntent(intent: ReplaceRuleIntent) {
        when (intent) {
            is ReplaceRuleIntent.SetSearchMode -> setSearchMode(intent.active)
            is ReplaceRuleIntent.UpdateSearchQuery -> setSearchKey(intent.query)
            ReplaceRuleIntent.ClearSelection -> setSelection(emptySet())
            ReplaceRuleIntent.SelectAll -> selectAll()
            ReplaceRuleIntent.InvertSelection -> invertSelection()
            is ReplaceRuleIntent.SetSelection -> setSelection(intent.ids)
            is ReplaceRuleIntent.ToggleSelection -> toggleSelection(intent.id)
            ReplaceRuleIntent.EnableSelection -> {
                enableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            ReplaceRuleIntent.DisableSelection -> {
                disableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            ReplaceRuleIntent.DeleteSelection -> {
                delSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            ReplaceRuleIntent.UploadSelection -> {
                val state = uiState.value
                uploadSelectedRules(state.selectedIds, state.items)
            }
            is ReplaceRuleIntent.ExportSelection -> {
                val state = uiState.value
                exportToUri(intent.uri, state.items, state.selectedIds)
            }
            is ReplaceRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            ReplaceRuleIntent.SaveSortOrder -> saveSortOrder()
            is ReplaceRuleIntent.DeleteRule -> delete(intent.rule)
            is ReplaceRuleIntent.SetRuleEnabled -> setEnabled(intent.id, intent.enabled)
            is ReplaceRuleIntent.CopyRule -> { /* not implemented for ReplaceRule */ }
            is ReplaceRuleIntent.ImportSource -> importSource(intent.text)
            ReplaceRuleIntent.CancelImport -> cancelImport()
            is ReplaceRuleIntent.ToggleImportSelection -> toggleImportSelection(intent.index)
            is ReplaceRuleIntent.ToggleImportAll -> toggleImportAll(intent.isSelected)
            is ReplaceRuleIntent.UpdateImportItem -> updateImportItem(intent.index, intent.rule)
            ReplaceRuleIntent.SaveImportedRules -> saveImportedRules()
            // ReplaceRule-specific
            is ReplaceRuleIntent.SetGroup -> setGroup(intent.groupName)
            is ReplaceRuleIntent.SetSortMode -> setSortMode(intent.mode)
            is ReplaceRuleIntent.ToTop -> toTop(intent.rule)
            is ReplaceRuleIntent.ToBottom -> toBottom(intent.rule)
            is ReplaceRuleIntent.TopSelectByIds -> topSelectByIds(intent.ids)
            is ReplaceRuleIntent.BottomSelectByIds -> bottomSelectByIds(intent.ids)
            is ReplaceRuleIntent.AddGroup -> addGroup(intent.group)
            is ReplaceRuleIntent.DeleteGroup -> delGroup(intent.group)
            is ReplaceRuleIntent.UpGroup -> upGroup(intent.oldGroup, intent.newGroup)
        }
    }

    private fun setGroup(groupName: String?) {
        _group.value = if (groupName == "全部" || groupName.isNullOrBlank()) {
            null
        } else {
            groupName
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val rawDataFlow: Flow<List<ReplaceRule>> =
        combine(_group, _sortMode) { group, sortMode ->
            group to sortMode
        }.flatMapLatest { (group, sortMode) ->
            val baseFlow = when (group) {
                null -> repository.flowAll()
                "未分组" -> repository.flowNoGroup()
                else -> repository.flowGroupSearch(group)
            }

            baseFlow.map { rules ->
                sortRules(rules, sortMode)
            }
        }

    override fun filterData(
        data: List<ReplaceRule>,
        searchKey: String,
        groupFilter: String
    ): List<ReplaceRule> {
        return if (searchKey.isEmpty() && groupFilter.isEmpty()) data
        else data.filter {
            val key = searchKey.ifEmpty { groupFilter }
            it.name.contains(key, ignoreCase = true)
                    || it.pattern.contains(key, ignoreCase = true)
                    || it.replacement.contains(key, ignoreCase = true)
                    || it.scope?.contains(key, ignoreCase = true) == true
        }
    }


    override fun composeUiState(
        items: List<ReplaceRuleItemUi>,
        selectedIds: Set<Long>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<ReplaceRule>
    ): ReplaceRuleUiState {
        return ReplaceRuleUiState(
            items = items,
            selectedIds = selectedIds,
            searchKey = _searchKey.value,
            sortMode = _sortMode.value,
            selectedGroup = _group.value,
            interaction = InteractionState(
                isSearchMode = isSearch,
                isUploading = isUploading || (importState is BaseImportUiState.Loading),
                isLoading = false
            )
        )
    }

    override fun ReplaceRule.toUiItem() = ReplaceRuleItemUi(
        id = id,
        name = name,
        isEnabled = isEnabled,
        group = group,
        pattern = pattern,
        replacement = replacement,
        scope = scope,
        scopeTitle = scopeTitle,
        scopeContent = scopeContent,
        excludeScope = excludeScope,
        isRegex = isRegex,
        timeoutMillisecond = timeoutMillisecond,
        order = order
    )

    override fun ruleItemToEntity(item: ReplaceRuleItemUi): ReplaceRule = item.toEntity()

    override suspend fun generateJson(entities: List<ReplaceRule>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<ReplaceRule> {
        return when {
            text.isJsonArray() -> ReplaceAnalyzer.jsonToReplaceRules(text).getOrThrow()
            text.isJsonObject() -> listOf(ReplaceAnalyzer.jsonToReplaceRule(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: ReplaceRule, oldRule: ReplaceRule): Boolean {
        return newRule.pattern != oldRule.pattern
                || newRule.replacement != oldRule.replacement
                || newRule.isRegex != oldRule.isRegex
                || newRule.scope != oldRule.scope
    }

    override suspend fun findOldRule(newRule: ReplaceRule): ReplaceRule? {
        return appDb.replaceRuleDao.findById(newRule.id)
    }

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<ReplaceRule> ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val rulesToSave = state.items
                .filter { it.isSelected }
                .map { wrapper ->
                    val rule = wrapper.data
                    val oldRule = wrapper.oldData

                    if (state.keepOriginalName && oldRule != null) {
                        rule.name = oldRule.name
                    }
                    val targetGroup = state.customGroup?.trim()
                    if (!targetGroup.isNullOrEmpty()) {
                        if (state.isAddGroup) {
                            val groups = linkedSetOf<String>()
                            rule.group?.splitNotBlank(AppPattern.splitGroupRegex)?.let { groups.addAll(it) }
                            groups.add(targetGroup)
                            rule.group = groups.joinToString(",")
                        } else {
                            rule.group = targetGroup
                        }
                    }
                    rule
                }
            if (rulesToSave.isNotEmpty()) {
                rulesToSave.forEach { rule ->
                    repository.insert(rule)
                }
                withContext(Dispatchers.Main) {
                    _importState.value = BaseImportUiState.Idle
                    _eventChannel.send(BaseRuleEvent.ShowSnackbar("成功导入 ${rulesToSave.size} 条规则"))
                }
            }
        }
    }

    private fun sortRules(rules: List<ReplaceRule>, mode: String): List<ReplaceRule> {
        val comparator = when (mode) {
            "asc" -> compareBy<ReplaceRule> { it.order.toLong() }
            "desc" -> compareByDescending<ReplaceRule> { it.order.toLong() }
            "name_asc" -> compareBy<ReplaceRule> { it.name.lowercase() }
            "name_desc" -> compareByDescending<ReplaceRule> { it.name.lowercase() }
            else -> null
        }
        return if (comparator != null) rules.sortedWith(comparator) else rules
    }

    private fun setSortMode(mode: String) {
        _sortMode.value = mode
        context.putPrefString(PreferKey.replaceSortMode, mode)
    }

    private fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.moveOrder(currentLocal.map { it.toEntity() }, _sortMode.value == "desc")
            _localItems.value = null
        }
    }


    private fun setEnabled(id: Long, enabled: Boolean) =
        viewModelScope.launch { repository.setEnabled(id, enabled) }

    private fun delete(rule: ReplaceRule) = viewModelScope.launch { repository.delete(rule) }
    fun enableSelectionByIds(ids: Set<Long>) = viewModelScope.launch { repository.enableByIds(ids) }
    fun disableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.disableByIds(ids) }

    fun delSelectionByIds(ids: Set<Long>) = viewModelScope.launch {
        repository.deleteByIds(ids)
        _selectedIds.update { it - ids }
    }

    private fun selectAll() {
        setSelection(uiState.value.items.map { it.id }.toSet())
    }

    private fun invertSelection() {
        val state = uiState.value
        setSelection(state.items.map { it.id }.toSet() - state.selectedIds)
    }

    private fun addGroup(group: String) = viewModelScope.launch { repository.addGroup(group) }
    private fun delGroup(group: String) = viewModelScope.launch { repository.delGroup(group) }

    private fun toTop(rule: ReplaceRule) =
        viewModelScope.launch { repository.toTop(rule, _sortMode.value == "desc") }

    private fun toBottom(rule: ReplaceRule) =
        viewModelScope.launch { repository.toBottom(rule, _sortMode.value == "desc") }

    private fun topSelectByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.topByIds(ids, _sortMode.value == "desc") }

    private fun bottomSelectByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.bottomByIds(ids, _sortMode.value == "desc") }

    private fun upGroup(oldGroup: String, newGroup: String?) =
        viewModelScope.launch { repository.upGroup(oldGroup, newGroup) }
}
