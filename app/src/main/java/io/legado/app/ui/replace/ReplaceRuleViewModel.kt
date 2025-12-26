package io.legado.app.ui.replace

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ReplaceAnalyzer
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isUri
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readText
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

@Immutable
data class ReplaceRuleItemUi(
    val id: Long,
    val name: String,
    val isEnabled: Boolean,
    val group: String?,
    val rule: ReplaceRule
)

data class ReplaceRuleUiState(
    val sortMode: String = "desc",
    val searchKey: String? = null,
    val groups: List<String> = emptyList(),
    val rules: List<ReplaceRuleItemUi> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * 替换规则数据修改
 * 修改数据要copy,直接修改会导致界面不刷新
 */
class ReplaceRuleViewModel(application: Application) : BaseViewModel(application) {

    private val repository = ReplaceRuleRepository()
    private val _sortMode = MutableStateFlow(context.getPrefString(PreferKey.replaceSortMode, "desc") ?: "desc")
    private val _searchKey = MutableStateFlow<String?>(null)
    private val _uiRules = MutableStateFlow<List<ReplaceRuleItemUi>>(emptyList())
    private val _selectedRuleIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRuleIds: StateFlow<Set<Long>> = _selectedRuleIds
    private val _importState = MutableStateFlow<BaseImportUiState<ReplaceRule>>(BaseImportUiState.Idle)
    val importState: StateFlow<BaseImportUiState<ReplaceRule>> = _importState.asStateFlow()

    fun toggleSelection(id: Long) {
        _selectedRuleIds.update {
            if (it.contains(id)) it - id else it + id
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rulesFlow = combine(_searchKey, _sortMode) { search, sort ->
        Pair(search, sort)
    }.flatMapLatest { (searchKey, sortMode) ->
        // 先获取基础数据
        val baseFlow = when {
            searchKey.isNullOrEmpty() -> repository.flowAll()
            searchKey == appCtx.getString(R.string.no_group) -> repository.flowNoGroup()
            searchKey.startsWith("group:") -> {
                val key = searchKey.substringAfter("group:")
                repository.flowGroupSearch("%$key%")
            }
            else -> repository.flowSearch("%$searchKey%")
        }

        baseFlow
            .map { rules ->
                val comparator = when (sortMode) {
                    "asc" -> compareBy<ReplaceRule> { it.order.toLong() }
                    "desc" -> compareByDescending<ReplaceRule> { it.order.toLong() }
                    "name_asc" -> compareBy<ReplaceRule> { it.name.lowercase() }
                    "name_desc" -> compareByDescending<ReplaceRule> { it.name.lowercase() }
                    else -> null
                }

                if (comparator != null) rules.sortedWith(comparator) else rules
            }
    }.flowOn(Dispatchers.Default)

    private val ruleUiFlow: Flow<List<ReplaceRuleItemUi>> =
        rulesFlow.map { rules ->
            rules.map { rule ->
                ReplaceRuleItemUi(
                    id = rule.id,
                    name = rule.name,
                    isEnabled = rule.isEnabled,
                    group = rule.group,
                    rule = rule
                )
            }
        }

    val uiState: StateFlow<ReplaceRuleUiState> = combine(
        _sortMode,
        _searchKey,
        repository.flowGroups(),
        _uiRules
    ) { sortMode, searchKey, groups, rules ->
        ReplaceRuleUiState(
            sortMode = sortMode,
            searchKey = searchKey,
            groups = groups,
            rules = rules,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReplaceRuleUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            ruleUiFlow.collect { rules ->
                _uiRules.value = rules
            }
        }
    }

    fun setSortMode(mode: String) {
        _sortMode.value = mode
        context.putPrefString(PreferKey.replaceSortMode, mode)
    }

    fun setSearchKey(key: String?) {
        _searchKey.value = key
    }

    fun setSelection(ids: Set<Long>) {
        _selectedRuleIds.value = ids
    }

    fun update(vararg rule: ReplaceRule) {
        execute {
            repository.update(*rule)
        }
    }

    fun delete(rule: ReplaceRule) {
        execute {
            repository.delete(rule)
        }
    }

    fun toTop(rule: ReplaceRule) {
        execute {
            repository.toTop(rule, _sortMode.value == "desc")
        }
    }

    fun toBottom(rule: ReplaceRule) {
        execute {
            repository.toBottom(rule, _sortMode.value == "desc")
        }
    }

    fun upOrder() {
        execute {
            repository.upOrder()
        }
    }

    fun enableSelection(rules: List<ReplaceRule>) {
        execute {
            repository.enableSelection(rules)
        }
    }

    fun disableSelection(rules: List<ReplaceRule>) {
        execute {
            repository.disableSelection(rules)
        }
    }

    fun enableSelectionByIds(ids: Set<Long>) {
        execute {
            repository.enableByIds(ids)
        }
    }

    fun disableSelectionByIds(ids: Set<Long>) {
        execute {
            repository.disableByIds(ids)
        }
    }

    fun delSelectionByIds(ids: Set<Long>) {
        execute {
            repository.deleteByIds(ids)
        }
    }

    fun topSelectByIds(ids: Set<Long>) {
        execute {
            repository.topByIds(ids, _sortMode.value == "desc")
        }
    }

    fun bottomSelectByIds(ids: Set<Long>) {
        execute {
            repository.bottomByIds(ids, _sortMode.value == "desc")
        }
    }


    fun addGroup(group: String) {
        execute {
            repository.addGroup(group)
        }
    }

    fun upGroup(oldGroup: String, newGroup: String?) {
        execute {
            repository.upGroup(oldGroup, newGroup)
        }
    }

    fun delGroup(group: String) {
        execute {
            repository.delGroup(group)
        }
    }

    fun moveItemInList(fromIndex: Int, toIndex: Int) {
        _uiRules.update { currentList ->
            val list = currentList.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            list
        }
    }

    fun saveSortOrder() {
        val currentRules = _uiRules.value
        val isDesc = _sortMode.value == "desc"
        execute {
            repository.moveOrder(currentRules, isDesc)
        }
    }

    fun importSource(text: String) {
        _importState.value = BaseImportUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val jsonText = resolveSource(text.trim())
                val rules = parseRules(jsonText)
                val wrappers = rules.map { newRule ->
                    val oldRule = appDb.replaceRuleDao.findById(newRule.id)

                    val status = when {
                        oldRule == null -> ImportStatus.New
                        hasChanged(newRule, oldRule) -> ImportStatus.Update
                        else -> ImportStatus.Existing
                    }

                    ImportItemWrapper(
                        data = newRule,
                        oldData = oldRule,
                        status = status,
                        isSelected = status != ImportStatus.Existing
                    )
                }

                _importState.value = BaseImportUiState.Success(
                    source = text,
                    items = wrappers
                )
            }.onFailure {
                it.printStackTrace()
                _importState.value = BaseImportUiState.Error(it.localizedMessage ?: "Unknown Error")
            }
        }
    }

    private suspend fun resolveSource(text: String): String {
        return when {
            text.isAbsUrl() -> {
                okHttpClient.newCallResponseBody {
                    if (text.endsWith("#requestWithoutUA")) {
                        url(text.substringBeforeLast("#requestWithoutUA"))
                        header(AppConst.UA_NAME, "null")
                    } else {
                        url(text)
                    }
                }.decompressed().text("utf-8")
            }
            text.isUri() -> text.toUri().readText(appCtx)
            else -> text
        }
    }

    private fun parseRules(text: String): List<ReplaceRule> {
        return when {
            text.isJsonArray() -> ReplaceAnalyzer.jsonToReplaceRules(text).getOrThrow()
            text.isJsonObject() -> listOf(ReplaceAnalyzer.jsonToReplaceRule(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    private fun hasChanged(newRule: ReplaceRule, oldRule: ReplaceRule): Boolean {
        return newRule.pattern != oldRule.pattern
                || newRule.replacement != oldRule.replacement
                || newRule.isRegex != oldRule.isRegex
                || newRule.scope != oldRule.scope
    }

    fun cancelImport() {
        _importState.value = BaseImportUiState.Idle
    }

    fun toggleImportSelection(index: Int) {
        val currentState = _importState.value as? BaseImportUiState.Success ?: return
        val newItems = currentState.items.toMutableList()
        val item = newItems[index]
        newItems[index] = item.copy(isSelected = !item.isSelected)
        _importState.value = currentState.copy(items = newItems)
    }

    fun toggleImportAll(isSelected: Boolean) {
        val currentState = _importState.value as? BaseImportUiState.Success ?: return
        val newItems = currentState.items.map { it.copy(isSelected = isSelected) }
        _importState.value = currentState.copy(items = newItems)
    }

    // 更新分组配置
    fun updateImportConfig(keepName: Boolean? = null, group: String? = null, isAdd: Boolean? = null) {
        val currentState = _importState.value as? BaseImportUiState.Success ?: return
        _importState.value = currentState.copy(
            keepOriginalName = keepName ?: currentState.keepOriginalName,
            customGroup = group ?: currentState.customGroup,
            isAddGroup = isAdd ?: currentState.isAddGroup
        )
    }

    fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success ?: return

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

            appDb.replaceRuleDao.insert(*rulesToSave.toTypedArray())

            withContext(Dispatchers.Main) {
                _importState.value = BaseImportUiState.Idle
            }
        }
    }

}
