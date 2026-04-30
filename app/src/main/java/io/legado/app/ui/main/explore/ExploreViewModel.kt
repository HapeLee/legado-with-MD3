package io.legado.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.widget.components.explore.ExploreKindUiUseCase
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(
    application: Application,
    private val exploreRepository: ExploreRepository,
    private val exploreKindUseCase: ExploreKindUiUseCase
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState
        .map { state -> state.copy(listItems = buildExploreListItems(state)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreUiState())
    private val _effects = MutableSharedFlow<ExploreEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var exploreJob: Job? = null
    private var kindsJob: Job? = null

    init {
        observeGroups()
        observeExplore()
    }

    private fun observeGroups() {
        viewModelScope.launch {
            exploreRepository.getExploreGroups()
                .flowOn(IO)
                .collectLatest { groups ->
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

            exploreRepository.getExploreSources(query, selectedGroup)
                .flowOn(IO)
                .collectLatest { items ->
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
                kindDisplayNames = emptyMap(),
                kindValues = emptyMap(),
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
                exploreKindUseCase.warmUp(source.bookSourceUrl)
                val infoMap = getExploreInfoMap(source.bookSourceUrl)
                val displayNames = kinds.associate { kind ->
                    kind.title to exploreKindUseCase.resolveDisplayName(
                        kind = kind,
                        sourceUrl = source.bookSourceUrl,
                        infoMap = infoMap
                    )
                }
                val values = buildKindValues(kinds, source.bookSourceUrl)
                _uiState.update {
                    if (it.expandedId == source.bookSourceUrl) {
                        it.copy(
                            exploreKinds = kinds,
                            kindDisplayNames = displayNames,
                            kindValues = values,
                            loadingKinds = false
                        )
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
            exploreRepository.topSource(bookSource)
        }
    }

    fun refreshExploreKinds(sourceUrl: String) {
        val source = _uiState.value.items.firstOrNull { it.bookSourceUrl == sourceUrl } ?: return
        refreshExploreKinds(source)
    }

    fun updateKindValue(sourceUrl: String, kind: ExploreKind, value: String) {
        _uiState.update { state ->
            state.copy(kindValues = state.kindValues + (kind.title to value))
        }
        viewModelScope.launch(IO) {
            getExploreInfoMap(sourceUrl).apply {
                this[kind.title] = value
                saveNow()
            }
        }
    }

    fun requestKindAction(sourceUrl: String, kind: ExploreKind) {
        _effects.tryEmit(ExploreEffect.ExecuteKindAction(sourceUrl, kind))
    }

    fun deleteSource(source: BookSourcePart) {
        execute {
            exploreRepository.deleteSource(source.bookSourceUrl)
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
        val kindDisplayNames: Map<String, String> = emptyMap(),
        val kindValues: Map<String, String> = emptyMap(),
        val loadingKinds: Boolean = false,
        val listItems: List<ExploreListItem> = emptyList()
    ) : ListUiState<BookSourcePart>

    private fun buildExploreListItems(state: ExploreUiState): List<ExploreListItem> {
        if (state.items.isEmpty()) return emptyList()
        val expandedId = state.expandedId
        val kindRows = if (expandedId != null) {
            calculateExploreKindRows(state.exploreKinds, 6)
        } else {
            emptyList()
        }
        return buildList {
            state.items.forEach { source ->
                add(ExploreListItem.Header(source))
                if (source.bookSourceUrl == expandedId) {
                    kindRows.forEachIndexed { index, row ->
                        add(
                            ExploreListItem.KindRow(
                                sourceUrl = source.bookSourceUrl,
                                rowIndex = index,
                                rowItems = row
                            )
                        )
                    }
                }
            }
        }
    }

    private fun buildKindValues(
        kinds: List<ExploreKind>,
        sourceUrl: String
    ): Map<String, String> {
        val infoMap = getExploreInfoMap(sourceUrl)
        var shouldSave = false
        val values = HashMap<String, String>()
        kinds.forEach { kind ->
            when (kind.type) {
                ExploreKind.Type.text -> {
                    values[kind.title] = infoMap[kind.title].orEmpty()
                }

                ExploreKind.Type.toggle,
                ExploreKind.Type.select -> {
                    val chars = kind.chars
                        ?.filterNotNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf("chars", "is null")
                    val value = infoMap[kind.title]
                        ?.takeUnless { it.isEmpty() }
                        ?: (kind.default ?: chars.first()).also {
                            infoMap[kind.title] = it
                            shouldSave = true
                        }
                    values[kind.title] = value
                }
            }
        }
        if (shouldSave) {
            infoMap.saveNow()
        }
        return values
    }

}

sealed interface ExploreListItem {
    val key: String

    data class Header(val source: BookSourcePart) : ExploreListItem {
        override val key: String = source.bookSourceUrl
    }

    data class KindRow(
        val sourceUrl: String,
        val rowIndex: Int,
        val rowItems: List<Pair<ExploreKind, Int>>
    ) : ExploreListItem {
        override val key: String = "${sourceUrl}_$rowIndex"
    }
}

sealed interface ExploreEffect {
    data class ExecuteKindAction(
        val sourceUrl: String,
        val kind: ExploreKind
    ) : ExploreEffect
}
