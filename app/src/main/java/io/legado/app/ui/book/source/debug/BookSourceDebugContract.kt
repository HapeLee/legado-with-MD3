package io.legado.app.ui.book.source.debug

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.model.Debug
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class BookSourceDebugTarget { Search, Explore, Info, Toc, Content }
enum class BookSourceDebugStatus { Loading, Idle, Running, Success, Failed, Cancelled }
enum class BookSourceDebugFilter { All, Messages, Sources, Errors }

@Immutable
data class BookSourceDebugEntryUi(
    val id: Long,
    val kind: Debug.EventKind,
    val message: String,
    val timestamp: Long,
    val elapsedMillis: Long,
)

@Immutable
data class BookSourceDebugExampleUi(
    val title: String,
    val target: BookSourceDebugTarget,
    val value: String,
)

@Stable
data class BookSourceDebugUiState(
    val sourceName: String = "",
    val query: String = "",
    val target: BookSourceDebugTarget = BookSourceDebugTarget.Search,
    val status: BookSourceDebugStatus = BookSourceDebugStatus.Loading,
    val filter: BookSourceDebugFilter = BookSourceDebugFilter.All,
    val entries: ImmutableList<BookSourceDebugEntryUi> = persistentListOf(),
    val examples: ImmutableList<BookSourceDebugExampleUi> = persistentListOf(),
    val selectedEntryId: Long? = null,
)

sealed interface BookSourceDebugIntent {
    data class Load(val sourceUrl: String?) : BookSourceDebugIntent
    data class SetQuery(val value: String) : BookSourceDebugIntent
    data class SelectTarget(val target: BookSourceDebugTarget) : BookSourceDebugIntent
    data class SelectFilter(val filter: BookSourceDebugFilter) : BookSourceDebugIntent
    data class UseExample(val example: BookSourceDebugExampleUi) : BookSourceDebugIntent
    data class ShowEntry(val id: Long) : BookSourceDebugIntent
    data object DismissEntry : BookSourceDebugIntent
    data object Start : BookSourceDebugIntent
    data object Stop : BookSourceDebugIntent
    data object Clear : BookSourceDebugIntent
}

sealed interface BookSourceDebugEffect {
    data class ShowMessage(val message: String) : BookSourceDebugEffect
}
