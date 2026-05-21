package io.legado.app.ui.main.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun HomepageSourceSelectSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    sources: List<HomepageSourceManageUi>,
    onToggleSource: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    var filterText by remember(show) { mutableStateOf("") }

    val filteredSources = remember(sources, filterText) {
        if (filterText.isBlank()) sources else sources.filter {
            it.sourceName.contains(filterText, ignoreCase = true) ||
                    it.sourceGroup?.contains(filterText, ignoreCase = true) == true
        }
    }

    val isAllSelected = remember(sources) {
        sources.all { it.isSelected } || sources.none { it.isSelected }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.homepage_filter_sources),
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
                isSelected = isAllSelected,
                containerColor = LegadoTheme.colorScheme.onSheetContent,
                inSelectionMode = true,
                onToggleSelection = {
                    onSelectAll()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredSources, key = { it.sourceUrl }) { source ->
                    SelectionItemCard(
                        title = source.sourceName,
                        subtitle = source.sourceGroup?.takeIf { it.isNotBlank() },
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        isSelected = source.isSelected,
                        inSelectionMode = true,
                        onToggleSelection = {
                            onToggleSource(source.sourceUrl)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
