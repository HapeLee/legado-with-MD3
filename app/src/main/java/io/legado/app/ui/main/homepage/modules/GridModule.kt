package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GridModule(
    books: ImmutableList<SearchBook>,
    onClick: (SearchBook) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    maxRows: Int? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    if (books.isEmpty()) return
    var rows = books.toList().chunked(columns)
    if (maxRows != null) {
        rows = rows.take(maxRows)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (book in row) {
                    SearchBookGridItem(
                        book = book,
                        shelfState = BookShelfState.NOT_IN_SHELF,
                        onClick = { onClick(book) },
                        modifier = Modifier.weight(1f),
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedCoverKey = bookCoverSharedElementKey(book.bookUrl)
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
