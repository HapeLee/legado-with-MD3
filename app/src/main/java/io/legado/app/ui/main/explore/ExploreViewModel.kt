package io.legado.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState = _uiState.asStateFlow()

    private var exploreJob: Job? = null
    private var kindsJob: Job? = null

    init {
        observeGroups()
        observeExplore()
    }

    private fun observeGroups() {
        viewModelScope.launch {
            appDb.bookSourceDao.flowExploreGroups().collectLatest { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
    }

    fun search(key: String) {
        _uiState.update { it.copy(searchKey = key, expandedId = null) }
        observeExplore()
    }

    fun setGroup(group: String) {
        _uiState.update { it.copy(selectedGroup = group, expandedId = null) }
        observeExplore()
    }

    fun toggleSearchVisible(visible: Boolean) {
        _uiState.update { it.copy(isSearch = visible) }
        if (!visible) {
            search("")
        }
    }

    private fun observeExplore() {
        exploreJob?.cancel()
        exploreJob = viewModelScope.launch {
            val state = _uiState.value
            val query = state.searchKey
            val selectedGroup = state.selectedGroup

            val flow = when {
                query.isNotBlank() -> {
                    if (query.startsWith("group:")) {
                        val key = query.substringAfter("group:")
                        appDb.bookSourceDao.flowGroupExplore(key)
                    } else {
                        appDb.bookSourceDao.flowExplore(query)
                    }
                }

                selectedGroup.isNotBlank() -> {
                    appDb.bookSourceDao.flowGroupExplore(selectedGroup)
                }

                else -> {
                    appDb.bookSourceDao.flowExplore()
                }
            }

            flow.flowOn(IO).collectLatest { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun toggleExpand(source: BookSourcePart) {
        val newExpandedId =
            if (_uiState.value.expandedId == source.bookSourceUrl) null else source.bookSourceUrl
        _uiState.update {
            it.copy(
                expandedId = newExpandedId,
                exploreKinds = emptyList(),
                loadingKinds = newExpandedId != null
            )
        }

        if (newExpandedId != null) {
            loadExploreKinds(source)
        }
    }

    private fun loadExploreKinds(source: BookSourcePart) {
        kindsJob?.cancel()
        kindsJob = viewModelScope.launch(IO) {
            try {
                val kinds = source.exploreKinds()
                _uiState.update {
                    if (it.expandedId == source.bookSourceUrl) {
                        it.copy(exploreKinds = kinds, loadingKinds = false)
                    } else it
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loadingKinds = false) }
            }
        }
    }

    fun refreshExploreKinds(source: BookSourcePart) {
        viewModelScope.launch(IO) {
            source.clearExploreKindsCache()
            if (_uiState.value.expandedId == source.bookSourceUrl) {
                loadExploreKinds(source)
            }
        }
    }

    fun topSource(bookSource: BookSourcePart) {
        execute {
            val minXh = appDb.bookSourceDao.minOrder
            bookSource.customOrder = minXh - 1
            appDb.bookSourceDao.upOrder(bookSource)
        }
    }

    fun deleteSource(source: BookSourcePart) {
        execute {
            SourceHelp.deleteBookSource(source.bookSourceUrl)
        }
    }

    data class ExploreUiState(
        override val items: List<BookSourcePart> = emptyList(),
        override val selectedIds: Set<String> = emptySet(),
        override val searchKey: String = "",
        override val isSearch: Boolean = false,
        override val isLoading: Boolean = false,
        val groups: List<String> = emptyList(),
        val selectedGroup: String = "",
        val expandedId: String? = null,
        val exploreKinds: List<ExploreKind> = emptyList(),
        val loadingKinds: Boolean = false
    ) : ListUiState<BookSourcePart>

}
