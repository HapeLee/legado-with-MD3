package io.legado.app.ui.book.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.tabRow.AppTabRow

@Composable
fun ScopeSelectSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    isAll: Boolean,
    onSelectAll: () -> Unit,
    groups: List<String>,
    selectedGroups: Collection<String>,
    onToggleGroup: (String) -> Unit,
    sources: List<BookSourcePart>,
    selectedSources: Collection<String>,
    onToggleSource: (BookSourcePart) -> Unit,
    isSourceScope: Boolean = false,
    title: String = stringResource(R.string.search_select_group),
    onConfirm: (() -> Unit)? = null,
    onApplyScope: ((ScopeSelection) -> Unit)? = null,
) {
    var scopeSheetTab by rememberSaveable(show) { mutableIntStateOf(if (isSourceScope) 1 else 0) }
    var filterText by rememberSaveable(show) { mutableStateOf("") }
    var draftIsAll by remember(show, isAll) { mutableStateOf(isAll) }
    var draftIsSourceScope by remember(show, isSourceScope) { mutableStateOf(isSourceScope) }
    var draftGroups by remember(show, selectedGroups) { mutableStateOf(selectedGroups.toSet()) }
    var draftSourceUrls by remember(show, selectedSources) { mutableStateOf(selectedSources.toSet()) }

    val useDraftSelection = onApplyScope != null
    val currentIsAll = if (useDraftSelection) draftIsAll else isAll
    val currentIsSourceScope = if (useDraftSelection) draftIsSourceScope else isSourceScope
    val currentGroups = if (useDraftSelection) draftGroups else selectedGroups
    val currentSourceUrls = if (useDraftSelection) draftSourceUrls else selectedSources
    val applyDraftSelection = {
        onApplyScope?.invoke(
            ScopeSelection(
                groupNames = if (!draftIsSourceScope) draftGroups.toList() else emptyList(),
                sources = if (draftIsSourceScope) {
                    sources.filter { draftSourceUrls.contains(it.bookSourceUrl) }
                } else {
                    emptyList()
                },
                isSourceScope = draftIsSourceScope,
            )
        )
    }

    val filteredGroups = remember(groups, filterText) {
        if (filterText.isBlank()) groups else groups.filter { it.contains(filterText, ignoreCase = true) }
    }
    val filteredSources = remember(sources, filterText) {
        if (filterText.isBlank()) sources else sources.filter {
            it.bookSourceName.contains(filterText, ignoreCase = true) || 
            it.bookSourceGroup?.contains(filterText, ignoreCase = true) == true ||
            it.bookSourceUrl.contains(filterText, ignoreCase = true)
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        startAction = onConfirm?.let {
            {
                MediumPlainButton(
                    onClick = it,
                    icon = AppIcons.Settings
                )
            }
        },
        endAction = onApplyScope?.let {
            {
                MediumPlainButton(
                    onClick = {
                        applyDraftSelection()
                        onDismissRequest()
                    },
                    icon = AppIcons.Check,
                )
            }
        },
    ) {
        Column {

            SearchBar(
                query = filterText,
                onQueryChange = { filterText = it },
                placeholder = stringResource(R.string.screen),
                autoFocus = false
            )

            Spacer(modifier = Modifier.height(8.dp))

            SelectionItemCard(
                title = stringResource(R.string.all_source),
                isSelected = currentIsAll,
                containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                inSelectionMode = true,
                onToggleSelection = {
                    if (useDraftSelection) {
                        draftIsAll = true
                        draftIsSourceScope = false
                        draftGroups = emptySet()
                        draftSourceUrls = emptySet()
                    } else {
                        onSelectAll()
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AppTabRow(
                tabTitles = listOf(
                    stringResource(R.string.group),
                    stringResource(R.string.book_source),
                ),
                selectedTabIndex = scopeSheetTab,
                onTabSelected = { scopeSheetTab = it },
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (scopeSheetTab == 0) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredGroups, key = { it }) { groupName ->
                        val selected = !currentIsSourceScope && currentGroups.contains(groupName)
                        SelectionItemCard(
                            title = groupName,
                            isSelected = selected,
                            containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                            inSelectionMode = true,
                            onToggleSelection = {
                                if (useDraftSelection) {
                                    val next = currentGroups.toMutableSet()
                                    if (!currentIsSourceScope && next.contains(groupName)) {
                                        next.remove(groupName)
                                    } else {
                                        next.add(groupName)
                                    }
                                    draftGroups = next
                                    draftSourceUrls = emptySet()
                                    draftIsSourceScope = false
                                    draftIsAll = next.isEmpty()
                                } else {
                                    onToggleGroup(groupName)
                                }
                            }
                        )
                    }
                }
            } else {
                if (filteredSources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_empty),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredSources, key = { it.bookSourceUrl }) { source ->
                            val selected = currentSourceUrls.contains(source.bookSourceUrl)
                            SelectionItemCard(
                                title = source.bookSourceName,
                                subtitle = source.bookSourceGroup?.takeIf { group -> group.isNotBlank() },
                                containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                                isSelected = selected,
                                inSelectionMode = true,
                                onToggleSelection = {
                                    if (useDraftSelection) {
                                        val next = currentSourceUrls.toMutableSet()
                                        if (next.contains(source.bookSourceUrl)) {
                                            next.remove(source.bookSourceUrl)
                                        } else {
                                            next.add(source.bookSourceUrl)
                                        }
                                        draftSourceUrls = next
                                        draftGroups = emptySet()
                                        draftIsSourceScope = next.isNotEmpty()
                                        draftIsAll = next.isEmpty()
                                    } else {
                                        onToggleSource(source)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

data class ScopeSelection(
    val groupNames: List<String>,
    val sources: List<BookSourcePart>,
    val isSourceScope: Boolean,
)
