package io.legado.app.ui.replace

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.help.ReplaceAnalyzer
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.rules.InteractionState
import io.legado.app.ui.widget.components.rules.RuleActionState
import io.legado.app.ui.widget.components.rules.SelectableItem
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ReplaceRuleItemUi(
    override val id: Long,
    val name: String,
    val isEnabled: Boolean,
    val group: String?,
    val rule: ReplaceRule
) : SelectableItem<Long>

data class ReplaceRuleUiState(
    override val items: List<ReplaceRuleItemUi> = emptyList(),
    override val selectedIds: Set<Long> = emptySet(),
    override val searchKey: String = "",
    val sortMode: String = "desc",
    val groups: List<String> = emptyList(),
    val interaction: InteractionState = InteractionState()
) : RuleActionState<ReplaceRuleItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isUploading: Boolean get() = interaction.isUploading
}

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
    private val _group = MutableStateFlow<String?>(null)
    val group = _group.asStateFlow()

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

    override fun filterData(data: List<ReplaceRule>, key: String): List<ReplaceRule> =
        if (key.isEmpty()) data
        else data.filter { it.name.contains(key, ignoreCase = true) }


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
            interaction = InteractionState(
                isSearchMode = isSearch,
                isUploading = isUploading || (importState is BaseImportUiState.Loading),
                isLoading = false
            )
        )
    }

    override fun ReplaceRule.toUiItem() = ReplaceRuleItemUi(id, name, isEnabled, group, this)
    override fun ruleItemToEntity(item: ReplaceRuleItemUi): ReplaceRule = item.rule

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
            repository.update(*rulesToSave.toTypedArray())
            withContext(Dispatchers.Main) {
                _importState.value = BaseImportUiState.Idle
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

    fun setSortMode(mode: String) {
        _sortMode.value = mode
        context.putPrefString(PreferKey.replaceSortMode, mode)
    }

    fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.moveOrder(currentLocal.map { it.rule }, _sortMode.value == "desc")
            _localItems.value = null
        }
    }


    fun update(vararg rule: ReplaceRule) = viewModelScope.launch { repository.update(*rule) }
    fun delete(rule: ReplaceRule) = viewModelScope.launch { repository.delete(rule) }
    fun enableSelectionByIds(ids: Set<Long>) = viewModelScope.launch { repository.enableByIds(ids) }
    fun disableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.disableByIds(ids) }

    fun delSelectionByIds(ids: Set<Long>) = viewModelScope.launch {
        repository.deleteByIds(ids)
        _selectedIds.update { it - ids }
    }

    fun addGroup(group: String) = viewModelScope.launch { repository.addGroup(group) }
    fun delGroup(group: String) = viewModelScope.launch { repository.delGroup(group) }

    fun toTop(rule: ReplaceRule) =
        viewModelScope.launch { repository.toTop(rule, _sortMode.value == "desc") }

    fun toBottom(rule: ReplaceRule) =
        viewModelScope.launch { repository.toBottom(rule, _sortMode.value == "desc") }

    fun upOrder() = viewModelScope.launch { repository.upOrder() }
    fun topSelectByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.topByIds(ids, _sortMode.value == "desc") }

    fun bottomSelectByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.bottomByIds(ids, _sortMode.value == "desc") }

    fun upGroup(oldGroup: String, newGroup: String?) =
        viewModelScope.launch { repository.upGroup(oldGroup, newGroup) }
}