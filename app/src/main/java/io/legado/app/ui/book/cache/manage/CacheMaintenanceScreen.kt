package io.legado.app.ui.book.cache.manage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun CacheMaintenanceRouteScreen(
    onBackClick: () -> Unit,
    viewModel: CacheMaintenanceViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CacheMaintenanceEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }
    CacheMaintenanceScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheMaintenanceScreen(
    state: CacheMaintenanceUiState,
    onIntent: (CacheMaintenanceIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.cache_maintenance_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                bottomContent = {
                    AppTabRow(
                        tabTitles = listOf(
                            stringResource(R.string.cache_maintenance_tab_orphan),
                            stringResource(R.string.cache_maintenance_tab_wordcount),
                        ),
                        selectedTabIndex = state.tab.ordinal,
                        onTabSelected = {
                            onIntent(CacheMaintenanceIntent.SelectTab(CacheMaintenanceTab.entries[it]))
                        },
                        isScrollable = false,
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item {
                ActionRow(
                    state = state,
                    onIntent = onIntent,
                )
            }
            if (state.hasScanned && !state.isBusy) {
                item {
                    ResultRow(state = state)
                }
            }
            when (state.tab) {
                CacheMaintenanceTab.OrphanChapters -> {
                    if (state.orphanGroups.isEmpty() && state.hasScanned) {
                        item {
                            SettingItem(
                                title = stringResource(R.string.cache_maintenance_orphan_empty),
                                onClick = null,
                            )
                        }
                    }
                    items(state.orphanGroups, key = { it.bookUrl }) { group ->
                        val selected = group.bookUrl in state.selectedOrphanBookUrls
                        SettingItem(
                            title = group.bookName,
                            description = stringResource(
                                R.string.cache_maintenance_orphan_desc,
                                group.entryNames.size,
                                formatBytes(group.bytes),
                            ),
                            trailingContent = {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = { onIntent(CacheMaintenanceIntent.ToggleOrphanBook(group.bookUrl)) },
                            semanticRole = androidx.compose.ui.semantics.Role.Checkbox,
                            // 执行期间结果会整体替换，此时改选中项没有意义。
                            enabled = !state.isBusy,
                        )
                    }
                }

                CacheMaintenanceTab.WordCount -> {
                    if (state.recountTargets.isEmpty() && state.hasScanned) {
                        item {
                            SettingItem(
                                title = stringResource(R.string.cache_maintenance_wordcount_empty),
                                onClick = null,
                            )
                        }
                    }
                    items(state.recountTargets, key = { it.bookUrl }) { target ->
                        val selected = target.bookUrl in state.selectedRecountBookUrls
                        val description = if (target.unsolvedChapters == 0) {
                            stringResource(
                                R.string.cache_maintenance_recount_desc,
                                target.recountableChapters,
                            )
                        } else {
                            stringResource(
                                R.string.cache_maintenance_recount_blocked,
                                target.unsolvedChapters,
                            )
                        }
                        SettingItem(
                            title = target.bookName,
                            description = description,
                            trailingContent = {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = { onIntent(CacheMaintenanceIntent.ToggleRecountBook(target.bookUrl)) },
                            semanticRole = androidx.compose.ui.semantics.Role.Checkbox,
                            enabled = !state.isBusy,
                        )
                    }
                }
            }
        }
    }

    AppAlertDialog(
        show = state.showDeleteConfirm,
        onDismissRequest = { onIntent(CacheMaintenanceIntent.DismissDeleteConfirm) },
        title = stringResource(R.string.cache_maintenance_confirm_delete_title),
        text = stringResource(R.string.cache_maintenance_confirm_delete_text),
        onConfirm = { onIntent(CacheMaintenanceIntent.ConfirmDelete) },
        onDismiss = { onIntent(CacheMaintenanceIntent.DismissDeleteConfirm) },
    )
}

@Composable
private fun ActionRow(
    state: CacheMaintenanceUiState,
    onIntent: (CacheMaintenanceIntent) -> Unit,
) {
    val busyKind = state.busyKind
    when {
        busyKind != null -> SettingItem(
            title = when (busyKind) {
                CacheMaintenanceBusyKind.Scan -> stringResource(
                    R.string.cache_maintenance_action_scanning,
                    state.scannedBooks,
                    state.totalBooks,
                )

                CacheMaintenanceBusyKind.Delete -> stringResource(
                    R.string.cache_maintenance_action_deleting,
                )

                CacheMaintenanceBusyKind.Recount -> stringResource(
                    R.string.cache_maintenance_action_recounting,
                    state.scannedBooks,
                    state.totalBooks,
                )
            },
            onClick = { onIntent(CacheMaintenanceIntent.Cancel) },
        )

        state.tab == CacheMaintenanceTab.OrphanChapters -> {
            SettingItem(
                title = stringResource(R.string.cache_maintenance_action_scan),
                onClick = { onIntent(CacheMaintenanceIntent.Scan) },
            )
            if (state.orphanGroups.isNotEmpty()) {
                SettingItem(
                    title = stringResource(R.string.cache_maintenance_action_select_all),
                    onClick = { onIntent(CacheMaintenanceIntent.ToggleAllOrphanBooks) },
                )
                if (state.selectedOrphanBookUrls.isNotEmpty()) {
                    SettingItem(
                        title = stringResource(R.string.cache_maintenance_action_delete),
                        onClick = { onIntent(CacheMaintenanceIntent.RequestDelete) },
                    )
                }
            }
        }

        else -> {
            SettingItem(
                title = stringResource(R.string.cache_maintenance_action_scan),
                onClick = { onIntent(CacheMaintenanceIntent.Scan) },
            )
            if (state.recountTargets.isNotEmpty()) {
                SettingItem(
                    title = stringResource(R.string.cache_maintenance_action_select_all),
                    onClick = { onIntent(CacheMaintenanceIntent.ToggleAllRecountBooks) },
                )
                if (state.selectedRecountBookUrls.isNotEmpty()) {
                    SettingItem(
                        title = stringResource(R.string.cache_maintenance_action_recount),
                        onClick = { onIntent(CacheMaintenanceIntent.RecountSelected) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(state: CacheMaintenanceUiState) {
    state.lastDeletion?.let { deleted ->
        SettingItem(
            title = stringResource(
                R.string.cache_maintenance_result_delete,
                deleted.deletedEntries,
                formatBytes(deleted.freedBytes),
            ),
            onClick = null,
        )
        if (deleted.skippedBooks > 0) {
            SettingItem(
                title = stringResource(
                    R.string.cache_maintenance_result_delete_skipped,
                    deleted.skippedBooks,
                ),
                onClick = null,
            )
        }
    }
    state.lastRecount?.let { recount ->
        SettingItem(
            title = stringResource(
                R.string.cache_maintenance_result_recount,
                recount.updatedChapters,
                recount.updatedBooks,
            ),
            onClick = null,
        )
    }
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}
