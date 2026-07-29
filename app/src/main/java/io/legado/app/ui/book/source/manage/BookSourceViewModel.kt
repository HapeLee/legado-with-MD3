package io.legado.app.ui.book.source.manage

import android.app.Application
import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.splitNotBlank
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookSourceViewModel(application: Application) : ViewModel() {
    companion object {
        const val FILTER_ENABLED = "@enabled"
        const val FILTER_DISABLED = "@disabled"
        const val FILTER_LOGIN = "@login"
        const val FILTER_NO_GROUP = "@noGroup"
        const val FILTER_ENABLED_EXPLORE = "@enabledExplore"
        const val FILTER_DISABLED_EXPLORE = "@disabledExplore"
        const val PREFIX_GROUP = "group:"
    }

    private val dao = appDb.bookSourceDao
    private val searchKey = MutableStateFlow("")
    private val filter = MutableStateFlow<String?>(null)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val sort = MutableStateFlow(BookSourceSort.Default)
    private val sortAscending = MutableStateFlow(true)
    private val groupByDomain = MutableStateFlow(false)
    private val localItems = MutableStateFlow<List<BookSourcePart>?>(null)

    val uiState = combine(
        dao.flowAll(),
        dao.flowGroups(),
        searchKey,
        filter,
        selectedIds,
        sort,
        sortAscending,
        groupByDomain,
        localItems
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val sourceItems = (values[0] as List<BookSourcePart>)
        val groups = values[1] as List<String>
        val query = values[2] as String
        val activeFilter = values[3] as String?
        val selected = values[4] as Set<String>
        val activeSort = values[5] as BookSourceSort
        val ascending = values[6] as Boolean
        val byDomain = values[7] as Boolean
        val local = values[8] as List<BookSourcePart>?
        val visible = local ?: sourceItems.filterFor(activeFilter, query)
            .sortFor(activeSort, ascending, byDomain)
        BookSourceUiState(
            items = visible.map { source ->
                BookSourceItemUi(
                    source = source,
                    domain = NetworkUtils.getSubDomainOrNull(source.bookSourceUrl) ?: "#",
                )
            }.toImmutableList(),
            selectedIds = selected.intersect(visible.map { it.bookSourceUrl }.toSet())
                .toImmutableSet(),
            searchKey = query,
            groupFilterName = activeFilter?.displayName(application),
            activeFilter = activeFilter,
            groups = groups.toImmutableList(),
            sort = activeSort,
            sortAscending = ascending,
            groupByDomain = byDomain,
            interaction = io.legado.app.ui.widget.components.list.InteractionState(isSearchMode = query.isNotEmpty()),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookSourceUiState())

    fun onIntent(intent: BookSourceIntent) {
        when (intent) {
            is BookSourceIntent.SetSearchMode -> {
                if (!intent.enabled) searchKey.value = ""
            }

            is BookSourceIntent.SetSearchQuery -> {
                localItems.value = null; searchKey.value = intent.query
            }

            is BookSourceIntent.SetSelection -> selectedIds.value = intent.ids
            is BookSourceIntent.ToggleSelection -> selectedIds.update { if (intent.id in it) it - intent.id else it + intent.id }
            is BookSourceIntent.SetFilter -> {
                localItems.value = null; filter.value = intent.filter
            }

            is BookSourceIntent.SetSort -> {
                localItems.value = null; sort.value = intent.sort
            }

            BookSourceIntent.ToggleSortDirection -> {
                localItems.value = null; sortAscending.update { !it }
            }

            BookSourceIntent.ToggleGroupByDomain -> {
                localItems.value = null; groupByDomain.update { !it }
            }

            is BookSourceIntent.SetEnabled -> launch { dao.enable(intent.id, intent.enabled) }
            is BookSourceIntent.SetEnabledForSelection -> launch {
                dao.enable(
                    intent.enabled,
                    parts(intent.ids)
                )
            }

            is BookSourceIntent.SetExploreEnabled -> updateExplore(intent.ids, intent.enabled)
            is BookSourceIntent.Delete -> launch { SourceHelp.deleteBookSourceParts(parts(intent.ids)); selectedIds.update { it - intent.ids } }
            is BookSourceIntent.MoveToEdge -> moveToEdge(intent.ids, intent.toTop)
            is BookSourceIntent.MoveItem -> moveItem(intent.from, intent.to)
            BookSourceIntent.SaveSortOrder -> saveSortOrder()
            is BookSourceIntent.CommitSortOrder -> commitSortOrder(intent.ids, intent.ascending)
            is BookSourceIntent.AddToGroup -> updateGroups(intent.ids, intent.group, true)
            is BookSourceIntent.RemoveFromGroup -> updateGroups(intent.ids, intent.group, false)
            is BookSourceIntent.UpdateGroup -> updateGroup(intent.old, intent.new)
            is BookSourceIntent.DeleteGroup -> updateGroup(intent.group, "")
            is BookSourceIntent.CheckSelectedInterval -> checkInterval(intent.ids)
        }
    }

    private fun launch(block: suspend () -> Unit) =
        viewModelScope.launch(Dispatchers.IO) { block() }

    private fun parts(ids: Set<String>) = dao.allPart.filter { it.bookSourceUrl in ids }
    private fun updateExplore(ids: Set<String>, enabled: Boolean) =
        launch { dao.enableExplore(enabled, parts(ids)) }

    private fun updateGroups(ids: Set<String>, group: String, add: Boolean) = launch {
        val changed = parts(ids).map { part ->
            part.copy().apply { if (add) addGroup(group) else removeGroup(group) }
        }
        dao.upGroup(changed)
    }

    private fun updateGroup(old: String, new: String) = launch {
        val sources = dao.getByGroup(old)
        sources.forEach { source ->
            source.bookSourceGroup?.splitNotBlank(",")?.toHashSet()
                ?.apply { remove(old); if (new.isNotBlank()) add(new) }
                ?.let { source.bookSourceGroup = TextUtils.join(",", it) }
        }
        dao.update(*sources.toTypedArray())
    }

    private fun moveToEdge(ids: Set<String>, toTop: Boolean) = launch {
        val selected = parts(ids).sortedBy { it.customOrder }
        val start = if (toTop) dao.minOrder - selected.size else dao.maxOrder + 1
        dao.upOrder(selected.mapIndexed { index, part -> part.copy(customOrder = start + index) })
    }

    private fun moveItem(from: Int, to: Int) {
        if (sort.value != BookSourceSort.Default || groupByDomain.value) return
        val moved = (localItems.value ?: uiState.value.items.map { it.source }).toMutableList()
        if (from !in moved.indices || to !in moved.indices) return
        moved.add(to, moved.removeAt(from)); localItems.value = moved
    }

    private fun saveSortOrder() {
        val items = localItems.value ?: return
        launch {
            dao.upOrder(items.mapIndexed { index, item -> item.copy(customOrder = index + 1) }); localItems.value =
            null
        }
    }

    private fun commitSortOrder(ids: List<String>, ascending: Boolean) = launch {
        val sourcesById = dao.allPart.associateBy { it.bookSourceUrl }
        val orderSlots = ids.mapNotNull { sourcesById[it]?.customOrder }
            .let { if (ascending) it.sorted() else it.sortedDescending() }
        val ordered = ids.mapIndexedNotNull { index, id ->
            sourcesById[id]?.copy(
                customOrder = orderSlots.getOrNull(index) ?: return@mapIndexedNotNull null
            )
        }
        dao.upOrder(ordered)
    }

    private fun checkInterval(ids: Set<String>) {
        val items = uiState.value.items
        val positions = items.mapIndexedNotNull { index, item -> index.takeIf { item.id in ids } }
        if (positions.isNotEmpty()) selectedIds.value =
            items.subList(positions.min(), positions.max() + 1).map { it.id }.toSet()
    }
}

private fun List<BookSourcePart>.filterFor(filter: String?, query: String): List<BookSourcePart> =
    filter { source ->
        val filterMatch = when (filter) {
            null -> true; BookSourceViewModel.FILTER_ENABLED -> source.enabled; BookSourceViewModel.FILTER_DISABLED -> !source.enabled
            BookSourceViewModel.FILTER_LOGIN -> source.hasLoginUrl; BookSourceViewModel.FILTER_NO_GROUP -> source.bookSourceGroup.isNullOrBlank()
            BookSourceViewModel.FILTER_ENABLED_EXPLORE -> source.enabledExplore; BookSourceViewModel.FILTER_DISABLED_EXPLORE -> !source.enabledExplore
            else -> filter.startsWith(BookSourceViewModel.PREFIX_GROUP) && source.bookSourceGroup?.split(
                ","
            )?.contains(filter.removePrefix(BookSourceViewModel.PREFIX_GROUP)) == true
        }
        filterMatch && (query.isBlank() || listOf(
            source.bookSourceName,
            source.bookSourceUrl,
            source.bookSourceGroup
        ).any { it?.contains(query, true) == true })
    }

private fun List<BookSourcePart>.sortFor(
    sort: BookSourceSort,
    ascending: Boolean,
    byDomain: Boolean
): List<BookSourcePart> {
    if (byDomain) {
        val domains = associateWith { NetworkUtils.getSubDomainOrNull(it.bookSourceUrl) ?: "#" }
        return sortedWith(compareBy<BookSourcePart> { domains.getValue(it) == "#" }
            .thenBy { domains.getValue(it) }
            .thenByDescending { it.lastUpdateTime })
    }
    val comparator = when (sort) {
        BookSourceSort.Name -> compareBy<BookSourcePart> { it.bookSourceName }
        BookSourceSort.Url -> compareBy { it.bookSourceUrl }; BookSourceSort.Weight -> compareBy { it.weight }
        BookSourceSort.Update -> compareByDescending<BookSourcePart> { it.lastUpdateTime }; BookSourceSort.Respond -> compareBy { it.respondTime }
        BookSourceSort.Enable -> compareByDescending<BookSourcePart> { it.enabled }.thenBy { it.bookSourceName }
        BookSourceSort.Default -> compareBy { it.customOrder }
    }
    return if (ascending) sortedWith(comparator) else sortedWith(comparator.reversed())
}

private fun String.displayName(application: Application) = when (this) {
    BookSourceViewModel.FILTER_ENABLED -> application.getString(io.legado.app.R.string.enabled)
    BookSourceViewModel.FILTER_DISABLED -> application.getString(io.legado.app.R.string.disabled)
    BookSourceViewModel.FILTER_LOGIN -> application.getString(io.legado.app.R.string.need_login)
    BookSourceViewModel.FILTER_NO_GROUP -> application.getString(io.legado.app.R.string.no_group)
    BookSourceViewModel.FILTER_ENABLED_EXPLORE -> application.getString(io.legado.app.R.string.enabled_explore)
    BookSourceViewModel.FILTER_DISABLED_EXPLORE -> application.getString(io.legado.app.R.string.disabled_explore)
    else -> removePrefix(BookSourceViewModel.PREFIX_GROUP)
}
