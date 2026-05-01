package io.legado.app.ui.widget.components.cover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.card.TextCard

@Composable
fun BookshelfCover(
    name: String?,
    author: String?,
    path: String?,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier.fillMaxWidth(),
    isUpdating: Boolean = false,
    badgeText: String? = null,
    showBadgeDot: Boolean = false,
    leftBottomText: String? = null,
    sourceOrigin: String? = null,
    onLoadFinish: (() -> Unit)? = null,
    showLoadingPlaceholder: Boolean = true,
) {
    Box(modifier = modifier) {
        CoilBookCover(
            name = name,
            author = author,
            path = path,
            modifier = coverModifier,
            sourceOrigin = sourceOrigin,
            onLoadFinish = onLoadFinish,
            showLoadingPlaceholder = showLoadingPlaceholder,
        )

        if (!badgeText.isNullOrEmpty()) {
            TextCard(
                text = badgeText,
                icon = if (showBadgeDot) Icons.Default.Update else null,
                iconSize = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
                cornerRadius = 4.dp,
                horizontalPadding = 4.dp,
                verticalPadding = 2.dp
            )
        }

        if (!leftBottomText.isNullOrEmpty()) {
            TextCard(
                text = leftBottomText,
                backgroundColor = LegadoTheme.colorScheme.cardContainer,
                contentColor = LegadoTheme.colorScheme.onCardContainer,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp),
                cornerRadius = 4.dp,
                horizontalPadding = 4.dp,
                verticalPadding = 2.dp
            )
        }

        if (isUpdating) {
            AppLinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .height(3.dp)
            )
        }
    }
}
