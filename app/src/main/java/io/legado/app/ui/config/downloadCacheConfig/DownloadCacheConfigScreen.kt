package io.legado.app.ui.config.downloadCacheConfig

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadCacheConfigScreen(
    onBackClick: () -> Unit,
    viewModel: DownloadCacheConfigViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    var showClearBookCacheDialog by remember { mutableStateOf(false) }
    var showShrinkDbDialog by remember { mutableStateOf(false) }
    var showClearCoverCacheDialog by remember { mutableStateOf(false) }
    var showClearMangaCacheDialog by remember { mutableStateOf(false) }

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
                            viewModel.coverCacheSize
                        ),
                        onClick = { showClearCoverCacheDialog = true }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.manga_cache),
                        description = stringResource(
                            R.string.cache_size_mb,
                            viewModel.mangaCacheSize
                        ),
                        onClick = { showClearMangaCacheDialog = true }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.download_setting)) {
                    SliderSettingItem(
                        title = stringResource(R.string.threads_num_title),
                        description = stringResource(R.string.threads_num_summary),
                        value = DownloadCacheConfig.threadCount.toFloat(),
                        defaultValue = 8f,
                        valueRange = 1f..256f,
                        onValueChange = { DownloadCacheConfig.threadCount = it.toInt() }
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.cache_book_threads_num_title),
                        description = stringResource(R.string.cache_book_threads_num_summary),
                        value = DownloadCacheConfig.cacheBookThreadCount
                            .coerceIn(1, CacheBook.maxDownloadConcurrency)
                            .toFloat(),
                        defaultValue = CacheBook.maxDownloadConcurrency.toFloat(),
                        valueRange = 1f..CacheBook.maxDownloadConcurrency.toFloat(),
                        onValueChange = {
                            DownloadCacheConfig.cacheBookThreadCount =
                                it.toInt().coerceIn(1, CacheBook.maxDownloadConcurrency)
                        }
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.pre_download),
                        description = stringResource(
                            R.string.pre_download_s,
                            DownloadCacheConfig.preDownloadNum
                        ),
                        value = DownloadCacheConfig.preDownloadNum.toFloat(),
                        defaultValue = 10f,
                        valueRange = 0f..100f,
                        onValueChange = { DownloadCacheConfig.preDownloadNum = it.toInt() }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.image_cache)) {
                    SliderSettingItem(
                        title = stringResource(R.string.bitmap_cache_size),
                        description = stringResource(
                            R.string.bitmap_cache_size_summary,
                            DownloadCacheConfig.bitmapCacheSize
                        ),
                        value = DownloadCacheConfig.bitmapCacheSize.toFloat(),
                        defaultValue = 32f,
                        valueRange = 1f..2047f,
                        onValueChange = {
                            viewModel.updateBitmapCacheSize(it.toInt())
                        }
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.image_retain_number),
                        description = stringResource(
                            R.string.image_retain_number_summary,
                            DownloadCacheConfig.imageRetainNum
                        ),
                        value = DownloadCacheConfig.imageRetainNum.toFloat(),
                        defaultValue = 10f,
                        valueRange = 0f..100f,
                        onValueChange = { DownloadCacheConfig.imageRetainNum = it.toInt() }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.network)) {
                    InputSettingItem(
                        title = stringResource(R.string.user_agent),
                        value = DownloadCacheConfig.userAgent,
                        onConfirm = { viewModel.saveUserAgent(it) }
                    )

                    SwitchSettingItem(
                        title = "Cronet",
                        description = stringResource(R.string.pref_cronet_summary),
                        checked = DownloadCacheConfig.cronetEnable,
                        onCheckedChange = { DownloadCacheConfig.cronetEnable = it }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.other_setting)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.clear_cache),
                        description = stringResource(R.string.clear_cache_summary),
                        onClick = { showClearBookCacheDialog = true }
                    )

                    ClickableSettingItem(
                        title = stringResource(R.string.shrink_database),
                        description = stringResource(R.string.shrink_database_summary),
                        onClick = { showShrinkDbDialog = true }
                    )
                }
            }
        }

        AppAlertDialog(
            show = showClearBookCacheDialog,
            onDismissRequest = { showClearBookCacheDialog = false },
            title = stringResource(R.string.clear_cache),
            text = stringResource(R.string.sure_del),
            onConfirm = {
                viewModel.clearBookCache(context)
                showClearBookCacheDialog = false
            },
            onDismiss = { showClearBookCacheDialog = false }
        )

        AppAlertDialog(
            show = showClearCoverCacheDialog,
            onDismissRequest = { showClearCoverCacheDialog = false },
            title = stringResource(R.string.cover_cache),
            text = stringResource(R.string.sure_del),
            onConfirm = {
                viewModel.clearCoverCache()
                showClearCoverCacheDialog = false
            },
            onDismiss = { showClearCoverCacheDialog = false }
        )

        AppAlertDialog(
            show = showClearMangaCacheDialog,
            onDismissRequest = { showClearMangaCacheDialog = false },
            title = stringResource(R.string.manga_cache),
            text = stringResource(R.string.sure_del),
            onConfirm = {
                viewModel.clearMangaCache()
                showClearMangaCacheDialog = false
            },
            onDismiss = { showClearMangaCacheDialog = false }
        )

        AppAlertDialog(
            show = showShrinkDbDialog,
            onDismissRequest = { showShrinkDbDialog = false },
            title = stringResource(R.string.shrink_database),
            text = stringResource(R.string.sure),
            onConfirm = {
                viewModel.shrinkDatabase()
                showShrinkDbDialog = false
            },
            onDismiss = { showShrinkDbDialog = false }
        )
    }
}
