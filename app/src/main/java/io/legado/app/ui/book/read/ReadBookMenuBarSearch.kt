package io.legado.app.ui.book.read

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.text.AnimatedText

@Composable
internal fun SearchBottomMenuContent(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val totalResults = state.searchResultList.size
    val currentIndex = state.searchResultIndex.coerceIn(0, (totalResults - 1).coerceAtLeast(0))
    val percent = if (totalResults > 0) {
        ((currentIndex + 1) * 100 / totalResults)
    } else {
        0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            )
            .padding(top = 12.dp, bottom = bottomPadding)
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchInfoPill(
                modifier = Modifier.fillMaxWidth(),
            ) {
                AnimatedText(
                    text = if (totalResults > 0) "${currentIndex + 1} / $totalResults" else "0 / 0",
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                TextCard(
                    text = "$percent%"
                )
                Spacer(Modifier.width(16.dp))
                VerticalDivider(
                    color = LegadoTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .height(8.dp)
                        .width(1.dp)
                )
                Spacer(Modifier.width(16.dp))
                AnimatedText(
                    text = state.chapterName.ifBlank { "-" },
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchMenuActionButton(
                modifier = Modifier.weight(2f),
                icon = Icons.Default.Search,
                text = stringResource(R.string.all_results),
                onClick = {
                    onIntent(ReadBookIntent.OpenSearch(word = null, autoFocus = false))
                },
            )
            SearchMenuActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Menu,
                text = stringResource(R.string.main_menu),
                onClick = {
                    onIntent(ReadBookIntent.HideSearchMenu)
                    onIntent(ReadBookIntent.ShowMenu)
                },
            )
            SearchMenuActionButton(
                modifier = Modifier.weight(0.55f),
                icon = Icons.Default.Close,
                text = null,
                iconContentDescription = stringResource(R.string.exit),
                onClick = { onIntent(ReadBookIntent.ExitSearch) },
            )
        }
    }
}

@Composable
private fun SearchPillSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val baseModifier = Modifier
        .height(40.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(LegadoTheme.colorScheme.surfaceContainerLow)
    Row(
        modifier = modifier
            .then(
                if (onClick != null) baseModifier.clickable(
                    role = Role.Button,
                    onClick = onClick
                ) else baseModifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun SearchInfoPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    SearchPillSurface(modifier = modifier, content = content)
}

@Composable
private fun SearchMenuActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String?,
    iconContentDescription: String? = null,
    onClick: () -> Unit,
) {
    SearchPillSurface(modifier = modifier, onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            tint = LegadoTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        if (text != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = LegadoTheme.typography.labelMediumEmphasized,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
