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
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GridModule(
    books: ImmutableList<HomepageBookItemUi>,
    onClick: (io.legado.app.data.entities.SearchBook, String?) -> Unit,
    onLongClick: ((io.legado.app.data.entities.SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    maxRows: Int? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKeySourceId: String? = null,
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
        for ((rowIndex, row) in rows.withIndex()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((columnIndex, item) in row.withIndex()) {
                    val itemIndex = rowIndex * columns + columnIndex
                    val sharedCoverKey = bookCoverSharedElementKey(
                        item.book.bookUrl,
                        sharedCoverKeySourceId?.let { "$it:$itemIndex" }
                    )
                    SearchBookGridItem(
                        book = item.book,
                        shelfState = item.shelfState,
                        onClick = { onClick(item.book, sharedCoverKey) },
                        onLongClick = onLongClick,
                        modifier = Modifier.weight(1f),
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedCoverKey = sharedCoverKey
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
