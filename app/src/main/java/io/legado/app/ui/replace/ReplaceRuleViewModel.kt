package io.legado.app.ui.replace

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val _selectedRuleIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRuleIds: StateFlow<Set<Long>> = _selectedRuleIds

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
                    "asc" -> compareBy<ReplaceRule> {
                        when (it.order) {
                            -1 -> Long.MIN_VALUE
                            -2 -> Long.MAX_VALUE
                            else -> it.order.toLong()
                        }
                    }
                    "desc" -> compareByDescending<ReplaceRule> {
                        when (it.order) {
                            -1 -> Long.MAX_VALUE
                            -2 -> Long.MIN_VALUE
                            else -> it.order.toLong()
                        }
                    }
                    "name_asc" -> compareBy<ReplaceRule> {
                        when (it.order) {
                            -1 -> Long.MIN_VALUE
                            -2 -> Long.MAX_VALUE
                            else -> 0
                        }
                    }.thenBy { it.name.lowercase() }
                    "name_desc" -> compareBy<ReplaceRule> {
                        when (it.order) {
                            -1 -> Long.MIN_VALUE
                            -2 -> Long.MAX_VALUE
                            else -> 0
                        }
                    }.thenByDescending { it.name.lowercase() }
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
        ruleUiFlow
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
            repository.toTop(rule)
        }
    }

    fun toBottom(rule: ReplaceRule) {
        execute {
            repository.toBottom(rule)
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
            repository.topByIds(ids)
        }
    }

    fun bottomSelectByIds(ids: Set<Long>) {
        execute {
            repository.bottomByIds(ids)
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
}