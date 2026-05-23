package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.main.homepage.HomepageSourceManageUi
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.utils.move
import kotlinx.collections.immutable.ImmutableList
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SetListPage(
    sets: ImmutableList<HomepageSourceManageUi>,
    onToggleSet: (String, Boolean) -> Unit,
    onReorderSets: (List<String>) -> Unit,
    onSelectSet: (String) -> Unit,
    onRenameSet: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onCreateSet: () -> Unit,
    onBrowseSources: () -> Unit,
) {
    var localSets by remember { mutableStateOf(sets.distinctBy { it.sourceUrl }) }
    val setsListState = rememberLazyListState()
    val setsReorderableState =
        rememberReorderableLazyListState(setsListState) { from, to ->
            localSets = localSets.toMutableList().apply {
                if (isEmpty()) return@apply
                val fromIndex = from.index.coerceIn(0, lastIndex)
                val toIndex = to.index.coerceIn(0, lastIndex)
                if (fromIndex in indices && toIndex in indices) {
                    move(fromIndex, toIndex)
                }
            }
        }

    LaunchedEffect(sets) {
        if (!setsReorderableState.isAnyItemDragging) localSets = sets.distinctBy { it.sourceUrl }
    }
    LaunchedEffect(setsReorderableState.isAnyItemDragging) {
        if (!setsReorderableState.isAnyItemDragging) {
            val orderedUrls = localSets.map { it.sourceUrl }
            if (orderedUrls != sets.map { it.sourceUrl }) onReorderSets(orderedUrls)
        }
    }

    LazyColumn(
        state = setsListState,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(localSets, key = { it.sourceUrl }) { set ->
            ReorderableSelectionItem(
                state = setsReorderableState,
                key = set.sourceUrl,
                title = set.sourceName,
                subtitle = stringResource(R.string.homepage_n_modules, set.moduleCount),
                containerColor = LegadoTheme.colorScheme.onSheetContent,
                isEnabled = set.isSelected,
                onToggleSelection = { onSelectSet(set.sourceUrl) },
                onEnabledChange = { enabled ->
                    onToggleSet(set.sourceUrl, enabled)
                    localSets = localSets.map {
                        if (it.sourceUrl == set.sourceUrl) it.copy(isSelected = enabled) else it
                    }
                },
                trailingAction = {
                    SmallIconButton(
                        onClick = { onRenameSet(set.sourceUrl) },
                        imageVector = Icons.Default.DriveFileRenameOutline
                    )
                    SmallIconButton(
                        onClick = { onDeleteSet(set.sourceUrl) },
                        imageVector = Icons.Default.Delete
                    )
                }
            )
        }
        item {
            PillDivider(
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        item(key = "create_set") {
            SecondaryButton(
                text = stringResource(R.string.homepage_new_custom_set),
                onClick = onCreateSet,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item(key = "browse_sources") {
            SecondaryButton(
                text = stringResource(R.string.homepage_browse_source_modules),
                onClick = onBrowseSources,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
