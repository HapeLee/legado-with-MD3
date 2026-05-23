package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.move
import kotlinx.collections.immutable.ImmutableList
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceBrowseDetailPage(
    browseUrl: String,
    selectingSetUrl: String?,
    allJoinedModules: ImmutableList<HomepageModuleManageUi>,
    canSelectInfiniteGlobal: Boolean,
    onGetSourceModules: (String, String?) -> List<HomepageModuleManageUi>,
    onGetExploreKinds: (String) -> List<Pair<String, String>>,
    onToggleModule: (String, Boolean) -> Unit,
    onJoinModule: (String, String?, ModuleDef) -> Unit,
    onRequestDeleteModule: (String) -> Unit,
    onReorderModules: (List<String>) -> Unit,
    onEditModule: (HomepageModuleManageUi) -> Unit,
    onAddDialogPrefill: (AddDialogPrefill?) -> Unit,
    onShowAddButtonGroupDialog: () -> Unit,
    browseTab: Int,
    onBrowseTabChange: (Int) -> Unit,
    browseModuleType: String,
    onBrowseModuleTypeChange: (String) -> Unit,
    selectedKindTitles: Set<String>,
    onSelectedKindTitlesChange: (Set<String>) -> Unit,
) {
    var showKindSelect by remember { mutableStateOf(false) }

    val displaySetUrl =
        selectingSetUrl ?: HomepageViewModel.customSetUrl("src_$browseUrl")
    val currentSetId = HomepageViewModel.customSetIdFromUrl(displaySetUrl)

    val joinedModules = remember(displaySetUrl, allJoinedModules) {
        allJoinedModules.filter { it.customSetId == currentSetId }
    }

    val standardModules = remember(joinedModules) {
        joinedModules.filter { !HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }
    val infiniteModules = remember(joinedModules) {
        joinedModules.filter { HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }
    val hasInfiniteInSet = infiniteModules.isNotEmpty()

    val joinedKeys = joinedModules.map { it.moduleKey }.toSet()
    val sourceModules = onGetSourceModules(browseUrl, currentSetId)
    Column {
        AppTabRow(
            tabTitles = listOf(
                stringResource(R.string.homepage_tab_joined),
                stringResource(R.string.homepage_tab_source_modules),
                stringResource(R.string.homepage_tab_discover)
            ),
            selectedTabIndex = browseTab,
            onTabSelected = { onBrowseTabChange(it) }
        )
        when (browseTab) {
            0 -> {
                if (joinedModules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(stringResource(R.string.homepage_no_joined_modules))
                    }
                } else {
                    var listData by remember(displaySetUrl, standardModules) {
                        mutableStateOf(standardModules.distinctBy { it.id })
                    }
                    val listState = rememberLazyListState()
                    val reorderableState =
                        rememberReorderableLazyListState(listState) { from, to ->
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
                        if (!reorderableState.isAnyItemDragging) listData =
                            standardModules.distinctBy { it.id }
                    }
                    LaunchedEffect(reorderableState.isAnyItemDragging) {
                        if (!reorderableState.isAnyItemDragging) {
                            val orderedIds =
                                (listData.map { it.id } + infiniteModules.map { it.id }).distinct()
                            if (orderedIds != joinedModules.map { it.id }) {
                                onReorderModules(orderedIds)
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "header_standard") {
                            AppText(
                                text = stringResource(R.string.homepage_standard_modules_sortable),
                                style = LegadoTheme.typography.labelMedium,
                                color = LegadoTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 4.dp
                                )
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

                        if (infiniteModules.isNotEmpty()) {
                            item(key = "header_infinite") {
                                PillDivider(
                                    modifier = Modifier.padding(
                                        vertical = 8.dp,
                                        horizontal = 16.dp
                                    )
                                )
                                AppText(
                                    text = stringResource(R.string.homepage_infinite_module_bottom),
                                    style = LegadoTheme.typography.labelMedium,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 4.dp
                                    )
                                )
                            }

                            items(infiniteModules, key = { it.id }) { module ->
                                val isEffective =
                                    infiniteModules.firstOrNull() == module
                                SelectionItemCard(
                                    title = module.title,
                                    subtitle = HomepageModuleType.fromKey(module.type).title + if (isEffective) stringResource(
                                        R.string.homepage_status_in_effect
                                    ) else stringResource(R.string.homepage_status_blocked),
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
                    }
                }
            }

            1 -> {
                if (sourceModules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(stringResource(R.string.homepage_source_json_empty))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            sourceModules.distinctBy { it.id },
                            key = { it.id }) { module ->
                            val isJoined = joinedKeys.contains(module.moduleKey)
                            val isInfinite =
                                HomepageViewModel.isInfinite(module.type, module.layoutConfig)
                            val isBlocked = !isJoined && isInfinite && hasInfiniteInSet

                            SelectionItemCard(
                                title = module.title,
                                subtitle = module.moduleKey + if (isJoined) stringResource(
                                    R.string.homepage_status_joined
                                ) else if (isBlocked) " (${stringResource(R.string.homepage_module_duplicate_infinite)})" else "",
                                containerColor = LegadoTheme.colorScheme.onSheetContent,
                                isSelected = isJoined,
                                inSelectionMode = true,
                                isEnabled = !isBlocked,
                                onToggleSelection = {
                                    if (!isJoined && !isBlocked) {
                                        onJoinModule(
                                            browseUrl, currentSetId, ModuleDef(
                                                key = module.moduleKey,
                                                type = module.type,
                                                title = module.title,
                                                sourceUrl = browseUrl,
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            2 -> {
                val effectiveTargetSetId = currentSetId
                val isButtonGroup = browseModuleType == "buttonGroup"
                Column {
                    val typeList = remember(canSelectInfiniteGlobal) {
                        HomepageModuleType.entries.filter {
                            it != HomepageModuleType.Unknown && (canSelectInfiniteGlobal || !HomepageViewModel.isInfinite(
                                it.key,
                                null
                            ))
                        }
                    }

                    LaunchedEffect(canSelectInfiniteGlobal) {
                        if (!canSelectInfiniteGlobal && HomepageViewModel.isInfinite(
                                browseModuleType,
                                null
                            )
                        ) {
                            onBrowseModuleTypeChange("card")
                        }
                    }

                    GlassCard(
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        cornerRadius = 12.dp
                    ) {
                        CompactDropdownSettingItem(
                            title = stringResource(R.string.homepage_module_type),
                            selectedValue = browseModuleType,
                            displayEntries = typeList.map { it.title }.toTypedArray(),
                            entryValues = typeList.map { it.key }.toTypedArray(),
                            onValueChange = {
                                onBrowseModuleTypeChange(it)
                                onSelectedKindTitlesChange(emptySet())
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SelectionItemCard(
                        title = stringResource(R.string.homepage_select_from_kinds),
                        subtitle = if (isButtonGroup) {
                            if (selectedKindTitles.isEmpty()) stringResource(R.string.homepage_select_multiple_kinds)
                            else stringResource(
                                R.string.homepage_n_selected,
                                selectedKindTitles.size
                            )
                        } else {
                            stringResource(R.string.homepage_select_one_kind)
                        },
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        onToggleSelection = { showKindSelect = true },
                        trailingAction = {
                            if (isButtonGroup && selectedKindTitles.isNotEmpty()) {
                                SmallIconButton(
                                    onClick = { onShowAddButtonGroupDialog() },
                                    imageVector = Icons.Default.Check
                                )
                            }
                        }
                    )

                    ExploreKindSelectSheet(
                        show = showKindSelect,
                        onDismissRequest = { showKindSelect = false },
                        sourceUrl = browseUrl,
                        multiple = isButtonGroup,
                        initialSelectedTitles = selectedKindTitles.toList(),
                        onSelected = { kinds ->
                            if (isButtonGroup) {
                                onSelectedKindTitlesChange(kinds.map { it.title }.toSet())
                            } else {
                                kinds.firstOrNull()?.let { kind ->
                                    onAddDialogPrefill(
                                        AddDialogPrefill(
                                            title = kind.title,
                                            url = kind.url ?: "",
                                            type = browseModuleType
                                        )
                                    )
                                }
                            }
                        }
                    )

                    PillDivider(
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    SecondaryButton(
                        text = stringResource(R.string.homepage_manual_add),
                        onClick = {
                            onAddDialogPrefill(AddDialogPrefill(type = browseModuleType))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
