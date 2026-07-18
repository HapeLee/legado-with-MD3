package io.legado.app.ui.config.downloadCacheConfig

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.CacheBook
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DownloadCacheConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: DownloadCacheConfigViewModel = koinViewModel(),
) {
    DownloadCacheConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadCacheConfigScreen(
    state: DownloadCacheConfigUiState,
    onIntent: (DownloadCacheConfigIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val settings = state.settings
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.download_cache_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.http_cache)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.cover_cache),
                        description = stringResource(
                            R.string.cache_size_mb,
                            state.coverCacheSizeMb
                        ),
                        onClick = {
                            onIntent(
                                DownloadCacheConfigIntent.ShowDialog(
                                    DownloadCacheConfigDialog.ClearCoverCache
                                )
                            )
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.manga_cache),
                        description = stringResource(
                            R.string.cache_size_mb,
                            state.mangaCacheSizeMb
                        ),
                        onClick = {
                            onIntent(
                                DownloadCacheConfigIntent.ShowDialog(
                                    DownloadCacheConfigDialog.ClearMangaCache
                                )
                            )
                        }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.download_setting)) {
                    SliderSettingItem(
                        title = stringResource(R.string.threads_num_title),
                        description = stringResource(R.string.threads_num_summary),
                        value = settings.threadCount.toFloat(),
                        defaultValue = 8f,
                        valueRange = 1f..256f,
                        onValueChange = {
                            onIntent(DownloadCacheConfigIntent.SetThreadCount(it.toInt()))
                        }
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.cache_book_threads_num_title),
                        description = stringResource(R.string.cache_book_threads_num_summary),
                        value = settings.cacheBookThreadCount
                            .coerceIn(1, CacheBook.maxDownloadConcurrency)
                            .toFloat(),
                        defaultValue = CacheBook.maxDownloadConcurrency.toFloat(),
                        valueRange = 1f..CacheBook.maxDownloadConcurrency.toFloat(),
                        onValueChange = {
                            onIntent(
                                DownloadCacheConfigIntent.SetCacheBookThreadCount(it.toInt())
                            )
                        }
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.pre_download),
                        description = stringResource(
                            R.string.pre_download_s,
                            settings.preDownloadNum
                        ),
                        value = settings.preDownloadNum.toFloat(),
                        defaultValue = 10f,
                        valueRange = 0f..100f,
                        onValueChange = {
                            onIntent(DownloadCacheConfigIntent.SetPreDownloadNum(it.toInt()))
                        }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.image_cache)) {
                    SliderSettingItem(
                        title = stringResource(R.string.bitmap_cache_size),
                        description = stringResource(
                            R.string.bitmap_cache_size_summary,
                            settings.bitmapCacheSize
                        ),
                        value = settings.bitmapCacheSize.toFloat(),
                        defaultValue = 32f,
                        valueRange = 1f..2047f,
                        onValueChange = {
                            onIntent(DownloadCacheConfigIntent.SetBitmapCacheSize(it.toInt()))
                        }
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.image_retain_number),
                        description = stringResource(
                            R.string.image_retain_number_summary,
                            settings.imageRetainNum
                        ),
                        value = settings.imageRetainNum.toFloat(),
                        defaultValue = 10f,
                        valueRange = 0f..100f,
                        onValueChange = {
                            onIntent(DownloadCacheConfigIntent.SetImageRetainNum(it.toInt()))
                        }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.network)) {
                    InputSettingItem(
                        title = stringResource(R.string.user_agent),
                        value = settings.userAgent,
                        onConfirm = { onIntent(DownloadCacheConfigIntent.SetUserAgent(it)) }
                    )

                    SwitchSettingItem(
                        title = "Cronet",
                        description = stringResource(R.string.pref_cronet_summary),
                        checked = settings.cronetEnabled,
                        onCheckedChange = {
                            onIntent(DownloadCacheConfigIntent.SetCronetEnabled(it))
                        }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.other_setting)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.clear_cache),
                        description = stringResource(R.string.clear_cache_summary),
                        onClick = {
                            onIntent(
                                DownloadCacheConfigIntent.ShowDialog(
                                    DownloadCacheConfigDialog.ClearBookCache
                                )
                            )
                        }
                    )

                    ClickableSettingItem(
                        title = stringResource(R.string.shrink_database),
                        description = stringResource(R.string.shrink_database_summary),
                        onClick = {
                            onIntent(
                                DownloadCacheConfigIntent.ShowDialog(
                                    DownloadCacheConfigDialog.ShrinkDatabase
                                )
                            )
                        }
                    )
                }
            }
        }

        AppAlertDialog(
            show = state.dialog == DownloadCacheConfigDialog.ClearBookCache,
            onDismissRequest = { onIntent(DownloadCacheConfigIntent.DismissDialog) },
            title = stringResource(R.string.clear_cache),
            text = stringResource(R.string.sure_del),
            onConfirm = {
                onIntent(DownloadCacheConfigIntent.ConfirmDialog)
            },
            onDismiss = { onIntent(DownloadCacheConfigIntent.DismissDialog) }
        )

        AppAlertDialog(
            show = state.dialog == DownloadCacheConfigDialog.ClearCoverCache,
            onDismissRequest = { onIntent(DownloadCacheConfigIntent.DismissDialog) },
            title = stringResource(R.string.cover_cache),
            text = stringResource(R.string.sure_del),
            onConfirm = {
                onIntent(DownloadCacheConfigIntent.ConfirmDialog)
            },
            onDismiss = { onIntent(DownloadCacheConfigIntent.DismissDialog) }
        )

        AppAlertDialog(
            show = state.dialog == DownloadCacheConfigDialog.ClearMangaCache,
            onDismissRequest = { onIntent(DownloadCacheConfigIntent.DismissDialog) },
            title = stringResource(R.string.manga_cache),
            text = stringResource(R.string.sure_del),
            onConfirm = {
                onIntent(DownloadCacheConfigIntent.ConfirmDialog)
            },
            onDismiss = { onIntent(DownloadCacheConfigIntent.DismissDialog) }
        )

        AppAlertDialog(
            show = state.dialog == DownloadCacheConfigDialog.ShrinkDatabase,
            onDismissRequest = { onIntent(DownloadCacheConfigIntent.DismissDialog) },
            title = stringResource(R.string.shrink_database),
            text = stringResource(R.string.sure),
            onConfirm = {
                onIntent(DownloadCacheConfigIntent.ConfirmDialog)
            },
            onDismiss = { onIntent(DownloadCacheConfigIntent.DismissDialog) }
        )
    }
}
