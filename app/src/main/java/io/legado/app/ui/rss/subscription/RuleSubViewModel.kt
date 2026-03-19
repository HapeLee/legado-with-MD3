package io.legado.app.ui.rss.subscription

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RuleSub
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuleSubViewModel(application: Application) : BaseViewModel(application) {

    private val _searchKey = MutableStateFlow("")
    private val _isSearch = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<RuleSubUiState> = combine(
        _searchKey,
        _isSearch,
        _selectedIds,
        appDb.ruleSubDao.flowAll()
    ) { searchKey, isSearch, selectedIds, items ->
        val filteredItems = if (isSearch && searchKey.isNotBlank()) {
            items.filter {
                it.name.contains(searchKey, ignoreCase = true) || it.url.contains(
                    searchKey,
                    ignoreCase = true
                )
            }
        } else {
            items
        }
        RuleSubUiState(
            items = filteredItems,
            selectedIds = selectedIds,
            searchKey = searchKey,
            isSearch = isSearch
        )
    }.flowOn(IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RuleSubUiState())

    fun onSearchToggle(isSearch: Boolean) {
        _isSearch.value = isSearch
        if (!isSearch) _searchKey.value = ""
    }

    fun onSearchQueryChange(query: String) {
        _searchKey.value = query
    }

    fun toggleSelection(ruleSub: RuleSub) {
        _selectedIds.update {
            if (it.contains(ruleSub.id)) it - ruleSub.id else it + ruleSub.id
        }
    }

    fun selectAll() {
        _selectedIds.value = state.value.items.map { it.id }.toSet()
    }

    fun selectInvert() {
        val allIds = state.value.items.map { it.id }.toSet()
        _selectedIds.update { current ->
            allIds - current
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val selected = _selectedIds.value
        viewModelScope.launch(IO) {
            state.value.items.filter { selected.contains(it.id) }.forEach {
                appDb.ruleSubDao.delete(it)
            }
            clearSelection()
        }
    }

    fun delete(ruleSub: RuleSub) {
        viewModelScope.launch(IO) {
            appDb.ruleSubDao.delete(ruleSub)
        }
    }

    fun save(ruleSub: RuleSub, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val rs = withContext(IO) {
                appDb.ruleSubDao.findByUrl(ruleSub.url)
            }
            if (rs != null && rs.id != ruleSub.id) {
                onError("${getApplication<Application>().getString(R.string.url_already)}(${rs.name})")
                return@launch
            }
            withContext(IO) {
                appDb.ruleSubDao.insert(ruleSub)
            }
            onSuccess()
        }
    }

    fun updateOrder(vararg ruleSubs: RuleSub) {
        viewModelScope.launch(IO) {
            appDb.ruleSubDao.update(*ruleSubs)
        }
    }

    fun resetOrder() {
        viewModelScope.launch(IO) {
            val sourceSubs = appDb.ruleSubDao.all
            for ((index: Int, ruleSub: RuleSub) in sourceSubs.withIndex()) {
                ruleSub.customOrder = index + 1
            }
            appDb.ruleSubDao.update(*sourceSubs.toTypedArray())
        }
    }

    data class RuleSubUiState(
        override val items: List<RuleSub> = emptyList(),
        override val selectedIds: Set<Long> = emptySet(),
        override val searchKey: String = "",
        override val isSearch: Boolean = false,
        override val isLoading: Boolean = false,
    ) : ListUiState<RuleSub>
}
