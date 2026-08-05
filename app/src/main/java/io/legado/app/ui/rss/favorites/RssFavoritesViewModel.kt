package io.legado.app.ui.rss.favorites

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.RssStar
import io.legado.app.data.repository.RssFavoriteRepository
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RssFavoritesViewModel(
    private val repository: RssFavoriteRepository,
) : ViewModel() {
    private val searchKey = MutableStateFlow("")
    private val isSearch = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val currentGroup = MutableStateFlow("")
    private val _effects = MutableSharedFlow<RssFavoritesEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState = combine(
        currentGroup,
        searchKey,
        isSearch,
        selectedIds,
        currentGroup.flatMapLatest { group ->
            if (group.isEmpty()) repository.observeAll() else repository.observeGroup(group)
        },
        repository.observeGroups(),
    ) { values ->
        val group = values[0] as String
        val query = values[1] as String
        val searching = values[2] as Boolean
        @Suppress("UNCHECKED_CAST") val selected = values[3] as Set<String>
        @Suppress("UNCHECKED_CAST") val items = values[4] as List<RssStar>
        @Suppress("UNCHECKED_CAST") val groups = values[5] as List<String>
        val filtered = if (searching && query.isNotBlank()) {
            items.filter { it.title.contains(query, ignoreCase = true) }
        } else items
        RssFavoritesUiState(
            items = filtered.toImmutableList(),
            selectedIds = selected.toImmutableSet(),
            searchKey = query,
            isSearch = searching,
            currentGroup = group,
            groups = groups.toImmutableList(),
        )
    }.flowOn(IO).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RssFavoritesUiState(),
    )

    fun onIntent(intent: RssFavoritesIntent) {
        when (intent) {
            is RssFavoritesIntent.ToggleSearch -> {
                isSearch.value = intent.enabled
                if (!intent.enabled) searchKey.value = ""
            }
            is RssFavoritesIntent.Search -> searchKey.value = intent.query
            is RssFavoritesIntent.ChangeGroup -> {
                currentGroup.value = intent.group
                selectedIds.value = emptySet()
            }
            is RssFavoritesIntent.ToggleSelection -> toggleSelection(intent.star)
            RssFavoritesIntent.SelectAll -> selectedIds.value =
                uiState.value.items.map(::itemId).toSet()
            RssFavoritesIntent.InvertSelection -> {
                val all = uiState.value.items.map(::itemId).toSet()
                selectedIds.update { all - it }
            }
            RssFavoritesIntent.ClearSelection -> selectedIds.value = emptySet()
            RssFavoritesIntent.DeleteSelected -> mutateSelected { repository.delete(it) }
            is RssFavoritesIntent.AddSelectedToGroup -> mutateSelected { star ->
                val groups = (star.group.split(',').filter(String::isNotBlank) + intent.group)
                    .distinct().joinToString(",")
                repository.update(star.copy(group = groups))
            }
            is RssFavoritesIntent.RemoveSelectedFromGroup -> mutateSelected { star ->
                repository.update(
                    star.copy(
                        group = star.group.split(',')
                            .filter { it.isNotBlank() && it != intent.group }
                            .joinToString(",")
                    )
                )
            }
            is RssFavoritesIntent.UpdateGroup -> viewModelScope.launch(IO) {
                repository.update(intent.star.copy(group = intent.group))
            }
            is RssFavoritesIntent.DeleteGroup -> viewModelScope.launch(IO) {
                repository.deleteGroup(intent.group)
            }
            RssFavoritesIntent.DeleteAll -> viewModelScope.launch(IO) { repository.deleteAll() }
            is RssFavoritesIntent.DeleteStar -> viewModelScope.launch(IO) {
                repository.delete(intent.star)
            }
        }
    }

    private fun toggleSelection(star: RssStar) {
        val id = itemId(star)
        selectedIds.update { if (id in it) it - id else it + id }
    }

    private fun mutateSelected(block: suspend (RssStar) -> Unit) {
        val selected = selectedIds.value
        viewModelScope.launch(IO) {
            uiState.value.items.filter { itemId(it) in selected }.forEach { block(it) }
            selectedIds.value = emptySet()
        }
    }

    private fun itemId(star: RssStar) = "${star.origin}|${star.link}"
}

@Stable
data class RssFavoritesUiState(
    override val items: ImmutableList<RssStar> = persistentListOf(),
    override val selectedIds: ImmutableSet<String> = persistentSetOf(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val currentGroup: String = "",
    val groups: ImmutableList<String> = persistentListOf(),
) : ListUiState<RssStar>

sealed interface RssFavoritesIntent {
    data class ToggleSearch(val enabled: Boolean) : RssFavoritesIntent
    data class Search(val query: String) : RssFavoritesIntent
    data class ChangeGroup(val group: String) : RssFavoritesIntent
    data class ToggleSelection(val star: RssStar) : RssFavoritesIntent
    data object SelectAll : RssFavoritesIntent
    data object InvertSelection : RssFavoritesIntent
    data object ClearSelection : RssFavoritesIntent
    data object DeleteSelected : RssFavoritesIntent
    data class AddSelectedToGroup(val group: String) : RssFavoritesIntent
    data class RemoveSelectedFromGroup(val group: String) : RssFavoritesIntent
    data class UpdateGroup(val star: RssStar, val group: String) : RssFavoritesIntent
    data class DeleteGroup(val group: String) : RssFavoritesIntent
    data object DeleteAll : RssFavoritesIntent
    data class DeleteStar(val star: RssStar) : RssFavoritesIntent
}

sealed interface RssFavoritesEffect
