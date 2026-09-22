package io.legado.app.feature.settings.downloadcache

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.bitmap_cache_size
import io.legado.app.feature.settings.res.bitmap_cache_size_summary
import io.legado.app.feature.settings.res.cache_book_threads_num_summary
import io.legado.app.feature.settings.res.cache_book_threads_num_title
import io.legado.app.feature.settings.res.cache_size_mb
import io.legado.app.feature.settings.res.clear_cache
import io.legado.app.feature.settings.res.clear_cache_summary
import io.legado.app.feature.settings.res.cover_cache
import io.legado.app.feature.settings.res.download_cache_config
import io.legado.app.feature.settings.res.download_setting
import io.legado.app.feature.settings.res.http_cache
import io.legado.app.feature.settings.res.image_cache
import io.legado.app.feature.settings.res.image_retain_number
import io.legado.app.feature.settings.res.image_retain_number_summary
import io.legado.app.feature.settings.res.manga_cache
import io.legado.app.feature.settings.res.network
import io.legado.app.feature.settings.res.other_setting
import io.legado.app.feature.settings.res.pre_download
import io.legado.app.feature.settings.res.pre_download_s
import io.legado.app.feature.settings.res.pref_cronet_summary
import io.legado.app.feature.settings.res.shrink_database
import io.legado.app.feature.settings.res.shrink_database_summary
import io.legado.app.feature.settings.res.sure
import io.legado.app.feature.settings.res.sure_del
import io.legado.app.feature.settings.res.threads_num_summary
import io.legado.app.feature.settings.res.threads_num_title
import io.legado.app.feature.settings.res.user_agent
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
import org.jetbrains.compose.resources.stringResource

// M5-7：从 `:app` 的 `io.legado.app.ui.config.downloadCacheConfig` 迁来。
//
// 差异两类：
//  ① `R.string.*` → `Res.string.*`（27 条）；
//  ② **3 处 `CacheBook.maxDownloadConcurrency` → `state.maxDownloadConcurrency`** ——
//     迁移前这个 composable 自己读 `:app` 的 `CacheBook`（见 `DownloadCacheConfigContract`
//     文件头注释：值由 VM 从平台契约填进 state）。
//
// `DownloadCacheConfigRouteScreen` 不搬 —— 它只做 `koinViewModel()`（本页路由不带参数），
// 宿主那侧照原样接。
//
// 四个确认对话框全是 Compose 状态（`state.dialog`），无平台动作 ⇒ 全留在共享层。
// 「Cronet」是硬编码字面量（迁移前就是，不是资源）⇒ 原样保留。

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
                title = stringResource(Res.string.download_cache_config),
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
                SplicedColumnGroup(title = stringResource(Res.string.http_cache)) {
                    ClickableSettingItem(
                        title = stringResource(Res.string.cover_cache),
                        description = stringResource(
                            Res.string.cache_size_mb,
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
                        title = stringResource(Res.string.manga_cache),
                        description = stringResource(
                            Res.string.cache_size_mb,
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

                SplicedColumnGroup(title = stringResource(Res.string.download_setting)) {
                    SliderSettingItem(
                        title = stringResource(Res.string.threads_num_title),
                        description = stringResource(Res.string.threads_num_summary),
                        value = settings.threadCount.toFloat(),
                        defaultValue = 8f,
                        valueRange = 1f..256f,
                        onValueChange = {
                            onIntent(DownloadCacheConfigIntent.SetThreadCount(it.toInt()))
                        }
                    )

                    SliderSettingItem(
                        title = stringResource(Res.string.cache_book_threads_num_title),
                        description = stringResource(Res.string.cache_book_threads_num_summary),
                        value = settings.cacheBookThreadCount
                            .coerceIn(1, state.maxDownloadConcurrency)
                            .toFloat(),
                        defaultValue = state.maxDownloadConcurrency.toFloat(),
                        valueRange = 1f..state.maxDownloadConcurrency.toFloat(),
                        onValueChange = {
                            onIntent(
                                DownloadCacheConfigIntent.SetCacheBookThreadCount(it.toInt())
                            )
                        }
                    )

                    SliderSettingItem(
                        title = stringResource(Res.string.pre_download),
                        description = stringResource(
                            Res.string.pre_download_s,
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

                SplicedColumnGroup(title = stringResource(Res.string.image_cache)) {
                    SliderSettingItem(
                        title = stringResource(Res.string.bitmap_cache_size),
                        description = stringResource(
                            Res.string.bitmap_cache_size_summary,
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
                        title = stringResource(Res.string.image_retain_number),
                        description = stringResource(
                            Res.string.image_retain_number_summary,
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

                SplicedColumnGroup(title = stringResource(Res.string.network)) {
                    InputSettingItem(
                        title = stringResource(Res.string.user_agent),
                        value = settings.userAgent,
                        onConfirm = { onIntent(DownloadCacheConfigIntent.SetUserAgent(it)) }
                    )

                    SwitchSettingItem(
                        title = "Cronet",
                        description = stringResource(Res.string.pref_cronet_summary),
                        checked = settings.cronetEnabled,
                        onCheckedChange = {
                            onIntent(DownloadCacheConfigIntent.SetCronetEnabled(it))
                        }
                    )
                }

                SplicedColumnGroup(title = stringResource(Res.string.other_setting)) {
                    ClickableSettingItem(
                        title = stringResource(Res.string.clear_cache),
                        description = stringResource(Res.string.clear_cache_summary),
                        onClick = {
                            onIntent(
                                DownloadCacheConfigIntent.ShowDialog(
                                    DownloadCacheConfigDialog.ClearBookCache
                                )
                            )
                        }
                    )

                    ClickableSettingItem(
                        title = stringResource(Res.string.shrink_database),
                        description = stringResource(Res.string.shrink_database_summary),
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
            title = stringResource(Res.string.clear_cache),
            text = stringResource(Res.string.sure_del),
            onConfirm = {
                onIntent(DownloadCacheConfigIntent.ConfirmDialog)
            },
            onDismiss = { onIntent(DownloadCacheConfigIntent.DismissDialog) }
        )

        AppAlertDialog(
            show = state.dialog == DownloadCacheConfigDialog.ClearCoverCache,
            onDismissRequest = { onIntent(DownloadCacheConfigIntent.DismissDialog) },
            title = stringResource(Res.string.cover_cache),
            text = stringResource(Res.string.sure_del),
            onConfirm = {
                onIntent(DownloadCacheConfigIntent.ConfirmDialog)
            },
            onDismiss = { onIntent(DownloadCacheConfigIntent.DismissDialog) }
        )

        AppAlertDialog(
            show = state.dialog == DownloadCacheConfigDialog.ClearMangaCache,
            onDismissRequest = { onIntent(DownloadCacheConfigIntent.DismissDialog) },
            title = stringResource(Res.string.manga_cache),
            text = stringResource(Res.string.sure_del),
            onConfirm = {
                onIntent(DownloadCacheConfigIntent.ConfirmDialog)
            },
            onDismiss = { onIntent(DownloadCacheConfigIntent.DismissDialog) }
        )

        AppAlertDialog(
            show = state.dialog == DownloadCacheConfigDialog.ShrinkDatabase,
            onDismissRequest = { onIntent(DownloadCacheConfigIntent.DismissDialog) },
            title = stringResource(Res.string.shrink_database),
            text = stringResource(Res.string.sure),
            onConfirm = {
                onIntent(DownloadCacheConfigIntent.ConfirmDialog)
            },
            onDismiss = { onIntent(DownloadCacheConfigIntent.DismissDialog) }
        )
    }
}
