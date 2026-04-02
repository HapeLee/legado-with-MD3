package io.legado.app.ui.widget.components.bookmark

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookmarkItem(
    modifier: Modifier = Modifier,
    bookmark: Bookmark,
    isDur: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isDur) LegadoTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        else Color.Transparent,
        label = "BgColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AppText(
                text = bookmark.chapterName,
                style = LegadoTheme.typography.titleSmallEmphasized,
                color = if (isDur) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.secondary
            )

            if (bookmark.bookText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                AppText(
                    text = bookmark.bookText,
                    style = LegadoTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bookmark.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                AppText(
                    text = bookmark.content,
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.primary
                )
            }
        }
    }
}