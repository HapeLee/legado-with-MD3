package io.legado.app.ui.rss.favorites

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssStar
import io.legado.app.ui.widget.components.rules.ListUiState
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RssFavoritesViewModel(application: Application) : BaseViewModel(application) {

    private val _searchKey = MutableStateFlow("")
    private val _isSearch = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _currentGroup = MutableStateFlow("")

    val groups = appDb.rssStarDao.flowGroups()
        .flowOn(IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RssFavoritesUiState> = combine(
        _currentGroup,
        _searchKey,
        _isSearch,
        _selectedIds,
        _currentGroup.flatMapLatest { group ->
            if (group.isEmpty()) {
                // If group is empty, we might want to wait for the first group from 'groups' flow
                // Or just show nothing/default. The Activity/Screen should set the first group.
                appDb.rssStarDao.liveAll()
            } else {
                appDb.rssStarDao.flowByGroup(group)
            }
        }
    ) { group, searchKey, isSearch, selectedIds, items ->
        val filteredItems = if (isSearch && searchKey.isNotBlank()) {
            items.filter { it.title.contains(searchKey, ignoreCase = true) }
        } else {
            items
        }
        RssFavoritesUiState(
            items = filteredItems,
            selectedIds = selectedIds,
            searchKey = searchKey,
            isSearch = isSearch,
            currentGroup = group
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RssFavoritesUiState())

    fun onSearchToggle(isSearch: Boolean) {
        _isSearch.value = isSearch
        if (!isSearch) _searchKey.value = ""
    }

    fun onSearchQueryChange(query: String) {
        _searchKey.value = query
    }

    fun onGroupChange(group: String) {
        _currentGroup.value = group
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(rssStar: RssStar) {
        val id = "${rssStar.origin}|${rssStar.link}"
        _selectedIds.update {
            if (it.contains(id)) it - id else it + id
        }
    }

    fun selectAll() {
        _selectedIds.value = state.value.items.map { "${it.origin}|${it.link}" }.toSet()
    }

    fun selectInvert() {
        val allIds = state.value.items.map { "${it.origin}|${it.link}" }.toSet()
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
            state.value.items.forEach {
                val id = "${it.origin}|${it.link}"
                if (selected.contains(id)) {
                    appDb.rssStarDao.delete(it.origin, it.link)
                }
            }
            clearSelection()
        }
    }

    fun deleteGroup(group: String) {
        viewModelScope.launch(IO) {
            appDb.rssStarDao.deleteByGroup(group)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(IO) {
            appDb.rssStarDao.deleteAll()
        }
    }

    fun deleteStar(rssStar: RssStar) {
        viewModelScope.launch(IO) {
            appDb.rssStarDao.delete(rssStar.origin, rssStar.link)
        }
    }

    data class RssFavoritesUiState(
        override val items: List<RssStar> = emptyList(),
        override val selectedIds: Set<String> = emptySet(),
        override val searchKey: String = "",
        override val isSearch: Boolean = false,
        override val isLoading: Boolean = false,
        val currentGroup: String = ""
    ) : ListUiState<RssStar>
}
