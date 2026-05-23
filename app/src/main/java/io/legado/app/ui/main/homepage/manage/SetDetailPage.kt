package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.move
import kotlinx.collections.immutable.ImmutableList
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SetDetailPage(
    setUrl: String,
    allJoinedModules: ImmutableList<HomepageModuleManageUi>,
    onToggleModule: (String, Boolean) -> Unit,
    onReorderModules: (List<String>) -> Unit,
    onEditModule: (HomepageModuleManageUi) -> Unit,
    onRequestDeleteModule: (String) -> Unit,
    onBrowseSourceModules: () -> Unit,
    onAddModules: () -> Unit,
) {
    val setId = HomepageViewModel.customSetIdFromUrl(setUrl)
    val modules = remember(setId, allJoinedModules) {
        allJoinedModules.filter { it.customSetId == setId }.distinctBy { it.id }
    }

    val standardModules = remember(modules) {
        modules.filter { !HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }
    val infiniteModules = remember(modules) {
        modules.filter { HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }

    if (modules.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText(stringResource(R.string.homepage_no_modules))
            SecondaryButton(
                text = stringResource(R.string.homepage_browse_to_add),
                onClick = {
                    if (setId.startsWith("src_")) onBrowseSourceModules()
                    else onAddModules()
                }
            )
        }
    } else {
        var listData by remember(setUrl, standardModules) {
            mutableStateOf(standardModules)
        }
        val listState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
            listData = listData.toMutableList().apply {
                if (isEmpty()) return@apply
                val fromIndex = (from.index - 1).coerceIn(0, lastIndex)
                val toIndex = (to.index - 1).coerceIn(0, lastIndex)
                if (fromIndex in indices && toIndex in indices) {
                    move(fromIndex, toIndex)
                }
            }
        }

        LaunchedEffect(standardModules) {
            if (!reorderableState.isAnyItemDragging) listData = standardModules
        }
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                val orderedIds =
                    (listData.map { it.id } + infiniteModules.map { it.id }).distinct()
                if (orderedIds != modules.map { it.id }) onReorderModules(orderedIds)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (listData.isNotEmpty()) {
                item(key = "header_std_detail") {
                    AppText(
                        text = stringResource(R.string.homepage_standard_module),
                        style = LegadoTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(listData, key = { it.id }) { module ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = module.id,
                        title = module.title,
                        subtitle = HomepageModuleType.fromKey(module.type).title,
                        isEnabled = module.isVisible,
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        onEnabledChange = { enabled ->
                            onToggleModule(module.id, enabled)
                            listData = listData.map {
                                if (it.id == module.id) it.copy(isVisible = enabled) else it
                            }
                        },
                        trailingAction = {
                            SmallIconButton(
                                onClick = { onEditModule(module) },
                                imageVector = Icons.Default.Edit
                            )
                            SmallIconButton(
                                onClick = { onRequestDeleteModule(module.id) },
                                imageVector = Icons.Default.Delete
                            )
                        }
                    )
                }
            }

            if (infiniteModules.isNotEmpty()) {
                item(key = "header_inf_detail") {
                    PillDivider()
                    AppText(
                        text = stringResource(R.string.homepage_infinite_module_slot),
                        style = LegadoTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(infiniteModules, key = { it.id }) { module ->
                    val isEffective = infiniteModules.firstOrNull() == module
                    SelectionItemCard(
                        title = module.title,
                        subtitle = HomepageModuleType.fromKey(module.type).title,
                        isEnabled = module.isVisible,
                        containerColor = if (isEffective) LegadoTheme.colorScheme.surfaceContainerHigh else LegadoTheme.colorScheme.onSheetContent,
                        onEnabledChange = { onToggleModule(module.id, it) },
                        trailingAction = {
                            SmallIconButton(
                                onClick = { onEditModule(module) },
                                imageVector = Icons.Default.Edit
                            )
                            SmallIconButton(
                                onClick = { onRequestDeleteModule(module.id) },
                                imageVector = Icons.Default.Delete
                            )
                        }
                    )
                }
            }

            item(key = "browse_from_set") {
                SecondaryButton(
                    text = stringResource(R.string.homepage_browse_source_modules),
                    onClick = {
                        if (setId.startsWith("src_")) onBrowseSourceModules()
                        else onAddModules()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
