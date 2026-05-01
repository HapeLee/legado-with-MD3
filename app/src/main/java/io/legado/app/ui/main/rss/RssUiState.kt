package io.legado.app.ui.main.rss

import io.legado.app.data.entities.RssSource
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

data class RssUiState(
    override val items: ImmutableList<RssSource> = persistentListOf(),
    override val selectedIds: ImmutableSet<String> = persistentSetOf(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val groups: ImmutableList<String> = persistentListOf(),
    val group: String = ""
) : ListUiState<RssSource>
