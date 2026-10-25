package io.legado.app.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreSourceSelectSheet(
    show: Boolean,
    sources: List<ExploreMainSourceItem>,
    selectedSourceUrl: String?,
    layoutMode: Int?,
    gridCount: Int,
    onDismissRequest: () -> Unit,
    onSelectSource: (ExploreMainSourceItem) -> Unit,
    onToggleLayout: () -> Unit,
    onSaveGridCount: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredSources = remember(query, sources) {
        if (query.isBlank()) sources
        else sources.filter { source ->
            source.name.contains(query, ignoreCase = true) ||
                    source.url.contains(query, ignoreCase = true)
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            AnimatedVisibility (layoutMode != null) {
                val isGridMode = layoutMode == 1
                Row(
                    Modifier.animateContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TinyClickableSettingItem(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.layout_mode),
                        description = stringResource(
                            if (isGridMode) R.string.layout_mode_grid else R.string.layout_mode_list
                        ),
                        imageVector = Icons.Default.GridView,
                        color = LegadoTheme.colorScheme.onSheetContent,
                        onClick = onToggleLayout,
                    )
                    AnimatedVisibility(
                        isGridMode
                    ) {
                        NormalCard(
                            modifier = Modifier.padding(bottom = 4.dp),
                            cornerRadius = 32.dp,
                            containerColor = LegadoTheme.colorScheme.onSheetContent,
                        ) {
                            ValueStepper(
                                modifier = Modifier.padding(10.dp),
                                value = gridCount.toFloat(),
                                displayValue = gridCount.toFloat(),
                                valueRange = 1f..10f,
                                onValueChange = {
                                    onSaveGridCount(it.toInt().coerceIn(1, 10))
                                },
                            )
                        }
                    }

                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SearchBar(
                query = query,
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.explore_select_source),
                autoFocus = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (filteredSources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyMessage(message = stringResource(R.string.search_empty))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(filteredSources, key = { it.url }) { source ->
                        SelectionItemCard(
                            title = source.name,
                            isSelected = source.url == selectedSourceUrl,
                            containerColor = LegadoTheme.colorScheme.onSheetContent,
                            selectedContainerColor = LegadoTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.32f),
                            onToggleSelection = {
                                onSelectSource(source)
                                onDismissRequest()
                            },
                        )
                    }
                }
            }
        }
    }
}
