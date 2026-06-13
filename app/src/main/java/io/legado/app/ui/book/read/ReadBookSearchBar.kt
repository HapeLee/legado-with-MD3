package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppFloatingActionButton

/**
 * Search result navigation overlay. The bottom search menu is hosted by [ReadBookMenuBar].
 */
@Composable
fun ReadBookSearchBar(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val searchVisible = state.isShowingSearchResult &&
            !(state.menuVisible && state.menuState.currentRoute != ReadBookMenuRoute.Main)
    val hasResults = state.searchResultList.isNotEmpty()
    val totalResults = state.searchResultList.size
    val currentIndex = state.searchResultIndex.coerceIn(0, (totalResults - 1).coerceAtLeast(0))

    Box(Modifier.fillMaxSize()) {
        // Left FAB - previous result
        AnimatedVisibility(
            visible = searchVisible && hasResults && currentIndex > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
        ) {
            AppFloatingActionButton(
                onClick = {
                    onIntent(ReadBookIntent.NavigateSearchResultByOffset(-1))
                },
                tooltipText = "上一个结果",
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "上一个结果",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Right FAB - next result
        AnimatedVisibility(
            visible = searchVisible && hasResults && currentIndex < totalResults - 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        ) {
            AppFloatingActionButton(
                onClick = {
                    onIntent(ReadBookIntent.NavigateSearchResultByOffset(1))
                },
                tooltipText = "下一个结果",
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "下一个结果",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
