package io.legado.app.ui.book.readaloud.cache

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.log.LogDetailSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TtsCacheRouteScreen(
    onBackClick: () -> Unit,
    viewModel: TtsCacheViewModel = koinViewModel(),
) {
    TtsCacheScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsCacheScreen(
    state: TtsCacheUiState,
    onIntent: (TtsCacheIntent) -> Unit,
    effects: kotlinx.coroutines.flow.Flow<TtsCacheEffect>,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is TtsCacheEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.tts_cache_manage),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                bottomContent = {
                    AppTabRow(
                        tabTitles = listOf(
                            stringResource(R.string.tts_cache_tab_files),
                            stringResource(R.string.tts_cache_tab_logs),
                        ),
                        selectedTabIndex = state.selectedTab.ordinal,
                        onTabSelected = { onIntent(TtsCacheIntent.SelectTab(TtsCacheTab.entries[it])) },
                        isScrollable = false,
                    )
                }
            )
        },
        floatingActionButton = {
            if (state.selectedTab == TtsCacheTab.Files && state.files.isNotEmpty()) {
                AppFloatingActionButton(
                    onClick = { onIntent(TtsCacheIntent.ShowClearAllDialog) },
                    icon = Icons.Default.DeleteSweep,
                    tooltipText = stringResource(R.string.clear_all),
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 96.dp,
            ),
        ) {
            if (state.selectedTab == TtsCacheTab.Files) {
                item {
                    val sizeText = TtsCacheViewModel.formatSize(state.totalSizeBytes)
                    val countText =
                        context.getString(R.string.tts_cache_file_count, state.files.size)
                    TinyClickableSettingItem(
                        title = stringResource(R.string.tts_cache_total),
                        description = "$sizeText · $countText",
                        onClick = {},
                    )
                }
                if (state.files.isEmpty() && !state.loading) {
                    item {
                        TinyClickableSettingItem(
                            title = stringResource(R.string.tts_cache_empty),
                            description = stringResource(R.string.tts_cache_empty_summary),
                            onClick = {},
                        )
                    }
                }
                items(state.files, key = { it.name }) { file ->
                    val sizeText = TtsCacheViewModel.formatSize(file.sizeBytes)
                    val dateText = dateFormat.format(Date(file.lastModified))
                    val displayText = file.text.ifEmpty {
                        context.getString(R.string.tts_cache_unknown_text)
                    }
                    TinyClickableSettingItem(
                        title = displayText,
                        description = "$sizeText · $dateText",
                        onClick = {
                            onIntent(
                                TtsCacheIntent.ShowFileDetail(
                                    name = file.name,
                                    text = file.text,
                                    sizeBytes = file.sizeBytes,
                                    lastModified = file.lastModified,
                                )
                            )
                        },
                    )
                }
            } else {
                if (state.logs.isEmpty()) {
                    item {
                        TinyClickableSettingItem(
                            title = stringResource(R.string.tts_cache_logs_empty),
                            description = stringResource(R.string.tts_cache_logs_empty_summary),
                            onClick = {},
                        )
                    }
                }
                items(state.logs, key = { "${it.timestamp}:${it.message.hashCode()}" }) { entry ->
                    val timeText = dateFormat.format(Date(entry.timestamp))
                    TinyClickableSettingItem(
                        title = timeText,
                        description = entry.message,
                        onClick = {
                            onIntent(
                                TtsCacheIntent.ShowLogDetail(
                                    timestamp = entry.timestamp,
                                    fullContent = entry.fullContent,
                                )
                            )
                        },
                    )
                }
            }
        }
    }

    AppAlertDialog(
        show = state.activeDialog == TtsCacheDialog.ClearAll,
        onDismissRequest = { onIntent(TtsCacheIntent.DismissDialog) },
        title = stringResource(R.string.clear_all_tts_cache),
        text = stringResource(R.string.sure_del),
        onConfirm = { onIntent(TtsCacheIntent.ClearAll) },
        onDismiss = { onIntent(TtsCacheIntent.DismissDialog) },
    )

    LogDetailSheet(
        show = state.showDetail,
        title = state.detailTitle,
        content = state.detailContent,
        onDismissRequest = { onIntent(TtsCacheIntent.DismissDetail) },
    )
}
