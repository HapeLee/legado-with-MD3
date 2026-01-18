package io.legado.app.ui.dict.rule

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
import io.legado.app.help.DefaultData
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
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

@Immutable
data class DictRuleItemUi(
    val name: String,
    val urlRule: String,
    val showRule: String,
    val isEnabled: Boolean,
    val rule: DictRule
)

data class DictRuleUiState(
    val searchKey: String? = null,
    val items: List<DictRuleItemUi> = emptyList(),
    val dictRule: List<DictRule> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

class DictRuleViewModel(application: Application) : BaseViewModel(application) {

    private val repository = DictRuleRepository()
    private val _searchKey = MutableStateFlow<String?>(null)
    private val _uiRules = MutableStateFlow<List<DictRuleItemUi>>(emptyList())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggleSelection(id: String) {
        _selectedIds.update {
            if (it.contains(id)) it - id else it + id
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rulesFlow = _searchKey.flatMapLatest { searchKey ->
        val baseFlow = if (searchKey.isNullOrEmpty()) {
            repository.flowAll()
        } else {
            repository.flowSearch("%$searchKey%")
        }

        baseFlow.map { rules ->
            rules.sortedBy { it.sortNumber }
        }
    }.flowOn(Dispatchers.Default)

    private val ruleUiFlow: Flow<List<DictRuleItemUi>> =
        rulesFlow.map { rules ->
            rules.map { rule ->
                DictRuleItemUi(
                    name = rule.name,
                    urlRule = rule.urlRule,
                    showRule = rule.showRule,
                    isEnabled = rule.enabled,
                    rule = rule
                )
            }
        }

    val uiState: StateFlow<DictRuleUiState> = combine(
        _searchKey,
        _uiRules,
        _selectedIds
    ) { searchKey, rules, selectedIds ->
        DictRuleUiState(
            searchKey = searchKey,
            items = rules,
            selectedIds = selectedIds,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DictRuleUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            ruleUiFlow.collect { rules ->
                _uiRules.value = rules
            }
        }
    }

    fun setSearchKey(key: String?) {
        _searchKey.value = key
    }

    fun setSelection(ids: Set<String>) {
        _selectedIds.value = ids
    }


    fun enableSelectionByIds(ids: Set<String>) {
        execute {
            repository.enableByIds(ids)
        }
    }

    fun disableSelectionByIds(ids: Set<String>) {
        execute {
            repository.disableByIds(ids)
        }
    }

    fun delSelectionByIds(ids: Set<String>) {
        execute {
            repository.deleteByIds(ids)
            _selectedIds.update { it - ids }
        }
    }

    fun update(vararg rule: DictRule) {
        execute {
            repository.update(*rule)
        }
    }

    fun insert(vararg rule: DictRule) {
        execute {
            repository.insert(*rule)
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
        execute {
            repository.moveOrder(currentRules.map { it.rule })
        }
    }

    fun delete(vararg dictRule: DictRule) {
        execute {
            repository.delete(*dictRule)
        }
    }

    fun upSortNumber() {
        execute {
            val rules = repository.getAll()
            for ((index, rule) in rules.withIndex()) {
                rule.sortNumber = index + 1
            }
            repository.insert(*rules.toTypedArray())
        }
    }

    fun importDefault() {
        execute {
            DefaultData.importDefaultDictRules()
        }
    }

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
