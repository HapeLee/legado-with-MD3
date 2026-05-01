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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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

    private val searchKeyFlow = MutableStateFlow("")
    private val groupFlow = MutableStateFlow("")
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
                    _uiState.update { it.copy(groups = groups.toImmutableList()) }
                }
        }
    }

    fun search(key: String) {
        searchKeyFlow.value = key
        _uiState.update { it.copy(searchKey = key, expandedId = null) }
    }

    fun setGroup(group: String) {
        groupFlow.value = group
        _uiState.update { it.copy(selectedGroup = group, expandedId = null) }
    }

    fun toggleSearchVisible(visible: Boolean) {
        _uiState.update { it.copy(isSearch = visible) }
        if (!visible) {
            search("")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeExplore() {
        viewModelScope.launch {
            combine(
                searchKeyFlow
                    .debounce(250)
                    .distinctUntilChanged(),
                groupFlow
            ) { query, selectedGroup ->
                query to selectedGroup
            }
                .flatMapLatest { (query, selectedGroup) ->
                    exploreRepository.getExploreSources(query, selectedGroup)
                }
                .flowOn(IO)
                .collectLatest { items ->
                    _uiState.update { it.copy(items = items.toImmutableList()) }
                }
        }
    }

    fun toggleExpand(source: BookSourcePart) {
        val newExpandedId =
            if (_uiState.value.expandedId == source.bookSourceUrl) null else source.bookSourceUrl
        _uiState.update {
            it.copy(
                expandedId = newExpandedId,
                exploreKinds = persistentListOf(),
                kindDisplayNames = persistentMapOf(),
                kindValues = persistentMapOf(),
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
                            exploreKinds = kinds.toImmutableList(),
                            kindDisplayNames = displayNames.toImmutableMap(),
                            kindValues = values.toImmutableMap(),
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
            state.copy(kindValues = (state.kindValues + (kind.title to value)).toImmutableMap())
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
        override val items: ImmutableList<BookSourcePart> = persistentListOf(),
        override val selectedIds: ImmutableSet<String> = persistentSetOf(),
        override val searchKey: String = "",
        override val isSearch: Boolean = false,
        override val isLoading: Boolean = false,
        val groups: ImmutableList<String> = persistentListOf(),
        val selectedGroup: String = "",
        val expandedId: String? = null,
        val exploreKinds: ImmutableList<ExploreKind> = persistentListOf(),
        val kindDisplayNames: ImmutableMap<String, String> = persistentMapOf(),
        val kindValues: ImmutableMap<String, String> = persistentMapOf(),
        val loadingKinds: Boolean = false,
        val listItems: ImmutableList<ExploreListItem> = persistentListOf()
    ) : ListUiState<BookSourcePart>

    private fun buildExploreListItems(state: ExploreUiState): ImmutableList<ExploreListItem> {
        if (state.items.isEmpty()) return persistentListOf()
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
                                rowItems = row.toImmutableList()
                            )
                        )
                    }
                }
            }
        }.toImmutableList()
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
        val rowItems: ImmutableList<Pair<ExploreKind, Int>>
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
