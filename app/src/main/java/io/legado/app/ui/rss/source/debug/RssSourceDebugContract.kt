package io.legado.app.ui.rss.source.debug

import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import io.legado.app.ui.book.source.debug.BookSourceDebugEntryUi
import io.legado.app.ui.book.source.debug.BookSourceDebugFilter
import io.legado.app.ui.book.source.debug.BookSourceDebugStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class RssSourceDebugTarget { Search, Sort, Content }

@Stable data class RssSourceDebugUiState(
    val sourceName: String = "",
    val query: String = "",
    val target: RssSourceDebugTarget = RssSourceDebugTarget.Search,
    val status: BookSourceDebugStatus = BookSourceDebugStatus.Loading,
    val filter: BookSourceDebugFilter = BookSourceDebugFilter.All,
    val entries: ImmutableList<BookSourceDebugEntryUi> = persistentListOf(),
    val examples: ImmutableList<RssSourceDebugExampleUi> = persistentListOf(),
    val selectedEntryId: Long? = null,
)

@Immutable
data class RssSourceDebugExampleUi(val title: String, val target: RssSourceDebugTarget, val value: String)

sealed interface RssSourceDebugIntent {
    data class Load(val sourceUrl: String?) : RssSourceDebugIntent
    data class SetQuery(val value: String) : RssSourceDebugIntent
    data class SelectTarget(val value: RssSourceDebugTarget) : RssSourceDebugIntent
    data class SelectFilter(val value: BookSourceDebugFilter) : RssSourceDebugIntent
    data class UseExample(val value: RssSourceDebugExampleUi) : RssSourceDebugIntent
    data class ShowEntry(val id: Long) : RssSourceDebugIntent
    data object DismissEntry : RssSourceDebugIntent
    data object Start : RssSourceDebugIntent
    data object Stop : RssSourceDebugIntent
    data object Clear : RssSourceDebugIntent
}

sealed interface RssSourceDebugEffect { data class ShowMessage(val value: String) : RssSourceDebugEffect }
