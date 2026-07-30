package io.legado.app.ui.book.source.manage

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class BookSourceItemUi(
    override val id: String,
    val domain: String,
    val name: String,
    val group: String?,
    val enabled: Boolean,
    val customOrder: Int,
) : SelectableItem<String> {
}

@Stable
data class BookSourceUiState(
    override val items: ImmutableList<BookSourceItemUi> = persistentListOf(),
    override val selectedIds: ImmutableSet<String> = persistentSetOf(),
    override val searchKey: String = "",
    val groupFilterName: String? = null,
    val activeFilter: String? = null,
    val groups: ImmutableList<String> = persistentListOf(),
    val sort: BookSourceSort = BookSourceSort.Default,
    val sortAscending: Boolean = true,
    val groupByDomain: Boolean = false,
    val interaction: InteractionState = InteractionState(isLoading = true),
) : ListUiState<BookSourceItemUi> {
    override val isSearch get() = interaction.isSearchMode
    override val isLoading get() = interaction.isLoading
}

sealed interface BookSourceIntent {
    data class SetSearchMode(val enabled: Boolean) : BookSourceIntent
    data class SetSearchQuery(val query: String) : BookSourceIntent
    data class SetSelection(val ids: Set<String>) : BookSourceIntent
    data class ToggleSelection(val id: String) : BookSourceIntent
    data class SetFilter(val filter: String?) : BookSourceIntent
    data class SetSort(val sort: BookSourceSort) : BookSourceIntent
    data object ToggleSortDirection : BookSourceIntent
    data object ToggleGroupByDomain : BookSourceIntent
    data class SetEnabled(val id: String, val enabled: Boolean) : BookSourceIntent
    data class SetEnabledForSelection(val ids: Set<String>, val enabled: Boolean) : BookSourceIntent
    data class SetExploreEnabled(val ids: Set<String>, val enabled: Boolean) : BookSourceIntent
    data class Delete(val ids: Set<String>) : BookSourceIntent
    data class MoveToEdge(val ids: Set<String>, val toTop: Boolean) : BookSourceIntent
    data class MoveItem(val from: Int, val to: Int) : BookSourceIntent
    data object SaveSortOrder : BookSourceIntent
    data class CommitSortOrder(
        val ids: List<String>,
        val ascending: Boolean,
    ) : BookSourceIntent

    data class AddToGroup(val ids: Set<String>, val group: String) : BookSourceIntent
    data class RemoveFromGroup(val ids: Set<String>, val group: String) : BookSourceIntent
    data class UpdateGroup(val old: String, val new: String) : BookSourceIntent
    data class DeleteGroup(val group: String) : BookSourceIntent
    data class CheckSelectedInterval(val ids: Set<String>) : BookSourceIntent
}
