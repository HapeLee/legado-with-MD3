package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.changesource.ChangeChapterSourceIntent
import io.legado.app.ui.book.changesource.ChangeChapterSourceUiState
import io.legado.app.ui.book.search.ScopeSelectSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun ChangeChapterSourceSheet(
    state: ChangeChapterSourceUiState,
    onIntent: (ChangeChapterSourceIntent) -> Unit,
    show: Boolean = true,
    onDismissRequest: () -> Unit,
    onAnimationFinish: () -> Unit = onDismissRequest,
    bookScoreFlow: (SearchBook) -> kotlinx.coroutines.flow.StateFlow<Int>,
    onBookScoreClick: (SearchBook) -> Unit,
    onEditSource: (String) -> Unit,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onAnimationFinish,
        title = if (state.showToc) state.selectedSourceName else stringResource(R.string.chapter_change_source),
        startAction = {
            if (!state.showToc) {
                Box {
                    MediumPlainButton(
                        onClick = { showOptionsMenu = true },
                        icon = Icons.Default.MoreVert
                    )
                    RoundDropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) { dismiss ->
                        RoundDropdownMenuItem(
                            text = "校验作者",
                            isSelected = state.checkAuthor,
                            onClick = {
                                onIntent(ChangeChapterSourceIntent.SetCheckAuthor(!state.checkAuthor))
                                dismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "加载详情",
                            isSelected = state.loadInfo,
                            onClick = {
                                onIntent(ChangeChapterSourceIntent.SetLoadInfo(!state.loadInfo))
                                dismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "加载目录",
                            isSelected = state.loadToc,
                            onClick = {
                                onIntent(ChangeChapterSourceIntent.SetLoadToc(!state.loadToc))
                                dismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "显示更多信息",
                            isSelected = state.loadWordCount,
                            onClick = {
                                onIntent(ChangeChapterSourceIntent.SetLoadWordCount(!state.loadWordCount))
                                dismiss()
                            }
                        )
                    }
                }
            }
        },
        endAction = {
            if (!state.showToc) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MediumPlainButton(
                        onClick = { onIntent(ChangeChapterSourceIntent.StartStopSearch) },
                        icon = if (state.isSearching) Icons.Default.PauseCircleOutline else Icons.Default.Refresh,
                    )
                    MediumPlainButton(
                        onClick = { showFilterSheet = true },
                        icon = Icons.Default.FilterList
                    )
                }
            }
        }
    ) {
        if (state.showToc) {
            TocContent(
                state = state,
                onIntent = onIntent,
            )
        } else {
            SearchContent(
                state = state,
                onIntent = onIntent,
                bookScoreFlow = bookScoreFlow,
                onBookScoreClick = onBookScoreClick,
                onEditSource = onEditSource,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    ScopeSelectSheet(
        show = showFilterSheet,
        onDismissRequest = { showFilterSheet = false },
        isAll = state.scopeState.isAll,
        onSelectAll = { onIntent(ChangeChapterSourceIntent.SelectAllScope) },
        groups = state.enabledGroups,
        selectedGroups = state.scopeState.displayNames,
        onToggleGroup = { onIntent(ChangeChapterSourceIntent.ToggleScopeGroup(it)) },
        sources = state.enabledSources,
        selectedSources = state.scopeState.sourceUrls,
        onToggleSource = { onIntent(ChangeChapterSourceIntent.ToggleScopeSource(it)) },
        isSourceScope = state.scopeState.isSource,
        onApplyScope = { selection ->
            onIntent(ChangeChapterSourceIntent.ApplyScope)
            showFilterSheet = false
        }
    )
}

@Composable
private fun TocContent(
    state: ChangeChapterSourceUiState,
    onIntent: (ChangeChapterSourceIntent) -> Unit,
) {
    if (state.isLoadingToc) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            AppCircularProgressIndicator()
        }
    } else {
        val listState = rememberLazyListState()
        LaunchedEffect(state.currentTocIndex, state.tocItems) {
            if (state.currentTocIndex >= 0) {
                listState.scrollToItem(state.currentTocIndex)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(state.tocItems) { index, chapter ->
                SelectionItemCard(
                    title = chapter.title,
                    containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                    selectedContainerColor = LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                    isSelected = index == state.currentTocIndex,
                    onToggleSelection = {
                        onIntent(ChangeChapterSourceIntent.SelectChapter(chapter))
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchContent(
    state: ChangeChapterSourceUiState,
    onIntent: (ChangeChapterSourceIntent) -> Unit,
    bookScoreFlow: (SearchBook) -> kotlinx.coroutines.flow.StateFlow<Int>,
    onBookScoreClick: (SearchBook) -> Unit,
    onEditSource: (String) -> Unit,
) {
    val context = LocalContext.current

    AppTextField(
        value = state.searchQuery,
        backgroundColor = LegadoTheme.colorScheme.surface,
        onValueChange = { onIntent(ChangeChapterSourceIntent.UpdateQuery(it)) },
        label = stringResource(R.string.screen),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    if (state.isSearching) {
        AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = "${state.searchProgress.first} / ${state.totalSourceCount} · ${state.searchResults.size}",
            style = LegadoTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (state.searchResults.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyMessage(
                message = stringResource(R.string.search_empty)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.searchResults, key = { it.bookUrl + it.origin }) { item ->
                val bookScore by remember(item.origin, item.name, item.author) {
                    bookScoreFlow(item)
                }.collectAsStateWithLifecycle()
                SelectionItemCard(
                    title = item.originName,
                    containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                    selectedContainerColor = LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                    leadingContent = {
                        MediumPlainButton(
                            onClick = { onBookScoreClick(item) },
                            icon = Icons.Default.PushPin,
                            tint = if (bookScore > 0) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.outline,
                            contentDescription = null
                        )
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppText(
                                text = item.author,
                                style = LegadoTheme.typography.labelLargeEmphasized
                            )
                            AppText(
                                text = item.getDisplayLastChapterTitle(),
                                style = LegadoTheme.typography.labelMediumEmphasized
                            )
                            item.chapterWordCountText?.takeIf { state.loadWordCount }?.let {
                                AppText(
                                    text = it,
                                    style = LegadoTheme.typography.labelSmallEmphasized,
                                    color = LegadoTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    isSelected = false,
                    onToggleSelection = {
                        onIntent(ChangeChapterSourceIntent.SelectSource(item))
                    },
                    dropdownContent = { onDismiss: () -> Unit ->
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.to_top),
                            onClick = {
                                onIntent(ChangeChapterSourceIntent.TopSource(item))
                                onDismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.to_bottom),
                            onClick = {
                                onIntent(ChangeChapterSourceIntent.BottomSource(item))
                                onDismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.edit),
                            onClick = {
                                onDismiss()
                                onEditSource(item.origin)
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.disable_source),
                            onClick = {
                                onIntent(ChangeChapterSourceIntent.DisableSource(item))
                                onDismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.delete),
                            color = LegadoTheme.colorScheme.error,
                            onClick = {
                                onIntent(ChangeChapterSourceIntent.DeleteSource(item))
                                onDismiss()
                            }
                        )
                    }
                )
            }
        }
    }
}
