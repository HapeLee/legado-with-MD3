package io.legado.app.ui.widget.components.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.cover.Cover
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.Flow

@Composable
fun SearchBookListItem(
    book: SearchBook,
    shelfState: Flow<BookShelfState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentShelfState by shelfState.collectAsState(initial = BookShelfState.NOT_IN_SHELF)
    SearchBookListItem(
        book = book,
        shelfState = currentShelfState,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun SearchBookListItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeContent: (@Composable RowScope.() -> Unit)? = when (shelfState) {
        BookShelfState.IN_SHELF -> {
            {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已在书架",
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        BookShelfState.SAME_NAME_AUTHOR -> {
            {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "同名书籍",
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        BookShelfState.NOT_IN_SHELF -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Cover(
            path = book.coverUrl,
            modifier = Modifier.width(72.dp),
            badgeContent = badgeContent,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            AppText(
                text = book.name,
                style = LegadoTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row {
                AppText(
                    text = book.author,
                    style = LegadoTheme.typography.bodySmall,
                    maxLines = 1,
                )

                val latestChapter = book.latestChapterTitle
                if (!latestChapter.isNullOrEmpty()) {
                    AppText(
                        text = " • ",
                        style = LegadoTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                    )

                    AppText(
                        text = "最新: $latestChapter",
                        style = LegadoTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val intro = book.intro?.replace("\\s+".toRegex(), "") ?: ""
            if (intro.isNotEmpty()) {
                AppText(
                    text = intro,
                    style = LegadoTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val kinds = book.getKindList()
            if (kinds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    kinds.forEach { kind ->
                        SearchBookTagChip(text = kind)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBookGridItem(
    book: SearchBook,
    shelfState: Flow<BookShelfState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentShelfState by shelfState.collectAsState(initial = BookShelfState.NOT_IN_SHELF)
    SearchBookGridItem(
        book = book,
        shelfState = currentShelfState,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun SearchBookGridItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeText: String? = when (shelfState) {
        BookShelfState.IN_SHELF -> "已在书架"
        BookShelfState.SAME_NAME_AUTHOR -> "同名书籍"
        BookShelfState.NOT_IN_SHELF -> null
    }

    val badgeContent: (@Composable RowScope.() -> Unit)? = if (!badgeText.isNullOrBlank()) {
        {
            AppText(
                text = badgeText,
                style = LegadoTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                )
            )
        }
    } else {
        null
    }

    Column(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Cover(
            path = book.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(12 / 17f),
            badgeContent = badgeContent,
        )

        Spacer(modifier = Modifier.height(4.dp))

        AppText(
            text = book.name,
            style = LegadoTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchBookTagChip(text: String) {
    Surface(
        color = LegadoTheme.colorScheme.cardContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
        AppText(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = LegadoTheme.typography.labelSmall,
            color = LegadoTheme.colorScheme.onCardContainer,
        )
    }
}
