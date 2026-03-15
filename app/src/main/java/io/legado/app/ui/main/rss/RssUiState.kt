package io.legado.app.ui.main.rss

import io.legado.app.data.entities.RssSource
import io.legado.app.ui.widget.components.list.ListUiState

data class RssUiState(
    override val items: List<RssSource> = emptyList(),
    override val selectedIds: Set<String> = emptySet(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val groups: List<String> = emptyList(),
    val group: String = ""
) : ListUiState<RssSource>
