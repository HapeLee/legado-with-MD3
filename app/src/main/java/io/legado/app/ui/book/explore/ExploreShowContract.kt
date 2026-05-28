package io.legado.app.ui.book.explore

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.BookShelfState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class ExploreShowUiState(
    val sourceUrl: String? = null,
    val books: ImmutableList<ExploreBookItemUi> = persistentListOf(),
    val kinds: ImmutableList<ExploreKind> = persistentListOf(),
    val selectedKindTitle: String? = null,
    val layoutState: Int = 0,
    val gridCount: Int = 3,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEnd: Boolean = false,
    val errorMsg: String? = null,
    val sheet: ExploreShowSheet = ExploreShowSheet.None,
)

@Stable
data class ExploreBookItemUi(
    val book: SearchBook,
    val shelfState: BookShelfState = BookShelfState.NOT_IN_SHELF,
)

sealed interface ExploreShowSheet {
    data object None : ExploreShowSheet
    data object KindSelect : ExploreShowSheet
    data object GridCount : ExploreShowSheet
}

sealed interface ExploreShowIntent {
    data class InitData(
        val sourceUrl: String,
        val exploreUrl: String?,
    ) : ExploreShowIntent

    data object LoadMore : ExploreShowIntent
    data object ForceLoadNext : ExploreShowIntent
    data object Refresh : ExploreShowIntent
    data class SwitchKind(val kind: ExploreKind) : ExploreShowIntent
    data object ToggleLayout : ExploreShowIntent
    data class SaveGridCount(val count: Int) : ExploreShowIntent
    data class ShowSheet(val sheet: ExploreShowSheet) : ExploreShowIntent
    data object DismissSheet : ExploreShowIntent
    data class OpenBook(val book: SearchBook, val sharedCoverKey: String?) : ExploreShowIntent
    data class AddToShelf(val book: SearchBook) : ExploreShowIntent
}

sealed interface ExploreShowEffect {
    data class OpenBookInfo(
        val name: String,
        val author: String,
        val bookUrl: String,
        val origin: String?,
        val coverPath: String?,
        val sharedCoverKey: String?,
    ) : ExploreShowEffect

    data class ShowMessage(val message: String) : ExploreShowEffect
}
