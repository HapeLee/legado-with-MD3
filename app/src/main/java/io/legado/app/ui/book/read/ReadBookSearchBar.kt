package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Compose replacement for SearchMenu — search result navigation overlay.
 */
@Composable
fun ReadBookSearchBar(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val searchVisible = state.isShowingSearchResult &&
            !(state.menuVisible && state.menuState.currentRoute != ReadBookMenuRoute.Main)
    val hasResults = state.searchResultList.isNotEmpty()
    val currentIndex = state.searchResultIndex
    val totalResults = state.searchResultList.size
    val currentResult = if (hasResults && currentIndex in state.searchResultList.indices) {
        state.searchResultList[currentIndex]
    } else null

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
            SmallFloatingActionButton(
                onClick = {
                    val prevIndex = currentIndex - 1
                    onIntent(
                        ReadBookIntent.NavigateToSearchResult(
                            state.searchResultList[prevIndex], prevIndex
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous",
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
            SmallFloatingActionButton(
                onClick = {
                    val nextIndex = currentIndex + 1
                    onIntent(
                        ReadBookIntent.NavigateToSearchResult(
                            state.searchResultList[nextIndex], nextIndex
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Tap background to dismiss search menu
        AnimatedVisibility(
            visible = searchVisible && state.searchMenuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onIntent(ReadBookIntent.HideSearchMenu) }
            )
        }

        // Bottom menu
        AnimatedVisibility(
            visible = searchVisible && state.searchMenuVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SearchBottomMenu(
                state = state,
                currentResult = currentResult,
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun SearchBottomMenu(
    state: ReadBookUiState,
    currentResult: io.legado.app.ui.book.searchContent.SearchResult?,
    onIntent: (ReadBookIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(bottom = 16.dp),
    ) {
        // Search progress info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Fraction: "3 / 10"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.searchResultIndex + 1} / ${state.searchResultList.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    val percent = if (state.searchResultList.isNotEmpty()) {
                        ((state.searchResultIndex + 1) * 100 / state.searchResultList.size)
                    } else 0
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Current chapter
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(
                    text = state.chapterName,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(
                onClick = { onIntent(ReadBookIntent.OpenSearch(null)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("搜索内容")
            }
            TextButton(
                onClick = { onIntent(ReadBookIntent.ShowMenu) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("主菜单")
            }
            IconButton(
                onClick = { onIntent(ReadBookIntent.ExitSearch) },
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit search")
            }
        }
    }
}
