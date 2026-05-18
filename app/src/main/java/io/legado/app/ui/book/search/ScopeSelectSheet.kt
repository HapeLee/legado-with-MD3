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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
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
) {
    var scopeSheetTab by rememberSaveable(show) { mutableIntStateOf(if (isSourceScope) 1 else 0) }
    var filterText by rememberSaveable(show) { mutableStateOf("") }

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
        endAction = onConfirm?.let {
            {
                MediumIconButton(
                    onClick = it,
                    imageVector = Icons.Default.Check
                )
            }
        }
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
                isSelected = isAll,
                containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                inSelectionMode = true,
                onToggleSelection = {
                    onSelectAll()
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
                        val selected = !isSourceScope && selectedGroups.contains(groupName)
                        SelectionItemCard(
                            title = groupName,
                            isSelected = selected,
                            containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                            inSelectionMode = true,
                            onToggleSelection = {
                                onToggleGroup(groupName)
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
                            val selected = selectedSources.contains(source.bookSourceUrl)
                            SelectionItemCard(
                                title = source.bookSourceName,
                                subtitle = source.bookSourceGroup?.takeIf { group -> group.isNotBlank() },
                                containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                                isSelected = selected,
                                inSelectionMode = true,
                                onToggleSelection = {
                                    onToggleSource(source)
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
