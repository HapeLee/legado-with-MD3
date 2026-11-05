package io.legado.app.feature.settings.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.book.manga.config.MangaDoublePageMode
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AccentColorButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

@Composable
fun MangaConfigRouteScreen(
    onBackClick: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenCacheSettings: () -> Unit,
    translationContent: @Composable () -> Unit = {},
    startAtTranslation: Boolean = false,
    translationReady: Boolean = true,
    viewModel: MangaConfigViewModel = koinViewModel(),
) {
    MangaConfigScreen(viewModel.uiState.collectAsStateWithLifecycle().value, viewModel::onIntent,
        onBackClick, onOpenAiSettings, onOpenCacheSettings, translationContent, startAtTranslation, translationReady)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaConfigScreen(
    state: MangaConfigUiState,
    onIntent: (MangaConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenCacheSettings: () -> Unit,
    translationContent: @Composable () -> Unit = {},
    startAtTranslation: Boolean = false,
    translationReady: Boolean = true,
) {
    val settings = state.settings
    val webtoon = settings.scrollMode in setOf(MangaScrollMode.WEBTOON, MangaScrollMode.WEBTOON_WITH_GAP)
    val horizontal = settings.scrollMode in setOf(MangaScrollMode.PAGE_LEFT_TO_RIGHT, MangaScrollMode.PAGE_RIGHT_TO_LEFT)
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var pickBackground by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var translationFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startAtTranslation, translationReady) {
        if (startAtTranslation && translationReady && !translationFocused) {
            listState.scrollToItem(TranslationGroupIndex)
            translationFocused = true
        }
    }
    AppScaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        GlassMediumFlexibleTopAppBar(title = stringResource(R.string.manga_config), scrollBehavior = scrollBehavior,
            navigationIcon = { TopBarNavigationButton(onClick = onBackClick) })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = adaptiveContentPadding(
            top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp)) {
            item("general") {
                SplicedColumnGroup {
                    SwitchSettingItem(stringResource(R.string.show_manga_ui), checked = settings.showMangaUi) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(showMangaUi = enabled) })
                    }
                }
            }
            item("reading") {
                SplicedColumnGroup(title = stringResource(R.string.manga_config_reading)) {
                    MangaConfigDropdown(R.string.manga_config_default_mode, settings.scrollMode,
                        arrayOf(R.string.manga_reader_left_to_right, R.string.manga_reader_right_to_left,
                            R.string.manga_reader_top_to_bottom, R.string.webtoon, R.string.manga_reader_webtoon_gap), 1..5,
                        description = stringResource(R.string.manga_config_defaults_hint)) { value ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(scrollMode = value) })
                    }
                    if (webtoon) MangaConfigSlider(R.string.manga_config_default_padding, settings.webtoonSidePaddingDp, 0..45) { value ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(webtoonSidePaddingDp = value) })
                    } else {
                        MangaConfigDropdown(R.string.manga_reader_scale_type, settings.pageScaleType,
                            arrayOf(R.string.manga_reader_scale_fit_screen, R.string.manga_reader_scale_stretch, R.string.manga_reader_scale_fit_width,
                                R.string.manga_reader_scale_fit_height, R.string.manga_reader_scale_original, R.string.manga_reader_scale_smart)) { value ->
                            onIntent(MangaConfigIntent.ChangeSettings { it.copy(pageScaleType = value) })
                        }
                        MangaConfigDropdown(R.string.manga_reader_wide_page, settings.widePageMode,
                            arrayOf(R.string.manga_reader_wide_normal, R.string.manga_reader_wide_fit_width, R.string.manga_reader_wide_rotate, R.string.manga_reader_wide_split)) { value ->
                            onIntent(MangaConfigIntent.ChangeSettings { it.copy(widePageMode = value) })
                        }
                        if (horizontal) {
                            MangaConfigDropdown(R.string.manga_reader_zoom_start, settings.zoomStartPosition,
                                arrayOf(R.string.manga_reader_position_automatic, R.string.manga_reader_position_left, R.string.manga_reader_position_right, R.string.manga_reader_position_center)) { value ->
                                onIntent(MangaConfigIntent.ChangeSettings { it.copy(zoomStartPosition = value) })
                            }
                            MangaConfigDropdown(R.string.manga_reader_double_page, settings.doublePageMode,
                                arrayOf(R.string.manga_reader_mode_off, R.string.manga_reader_mode_landscape, R.string.manga_reader_mode_always)) { value ->
                                onIntent(MangaConfigIntent.ChangeSettings { it.copy(doublePageMode = value) })
                            }
                            if (settings.doublePageMode != MangaDoublePageMode.OFF) {
                                SwitchSettingItem(stringResource(R.string.manga_reader_double_page_cover_single), checked = settings.doublePageCoverSingle) { enabled ->
                                    onIntent(MangaConfigIntent.ChangeSettings { it.copy(doublePageCoverSingle = enabled) })
                                }
                                SwitchSettingItem(stringResource(R.string.manga_reader_double_page_invert), checked = settings.doublePageInvert) { enabled ->
                                    onIntent(MangaConfigIntent.ChangeSettings { it.copy(doublePageInvert = enabled) })
                                }
                                SwitchSettingItem(stringResource(R.string.manga_reader_double_page_shift), checked = settings.doublePageShift) { enabled ->
                                    onIntent(MangaConfigIntent.ChangeSettings { it.copy(doublePageShift = enabled) })
                                }
                            }
                        }
                    }
                    SwitchSettingItem(stringResource(R.string.manga_reader_hide_edge_prompt), checked = settings.hideTitle) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(hideTitle = enabled) })
                    }
                }
            }
            item("interaction") {
                SplicedColumnGroup(title = stringResource(R.string.manga_config_interaction)) {
                    SwitchSettingItem(stringResource(R.string.manga_reader_pinch_zoom), checked = !settings.disableMangaScale) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(disableMangaScale = !enabled) })
                    }
                    SwitchSettingItem(stringResource(R.string.manga_reader_tap_turn), checked = !settings.disableClickScroll) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(disableClickScroll = !enabled) })
                    }
                    SwitchSettingItem(stringResource(R.string.manga_reader_long_press_save), checked = settings.longClick) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(longClick = enabled) })
                    }
                    SwitchSettingItem(stringResource(R.string.manga_reader_volume_page), checked = settings.volumeKeyPage) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(volumeKeyPage = enabled) })
                    }
                    if (settings.volumeKeyPage) SwitchSettingItem(stringResource(R.string.manga_reader_reverse_volume), checked = settings.reverseVolumeKeyPage) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(reverseVolumeKeyPage = enabled) })
                    }
                    MangaConfigSlider(R.string.manga_reader_auto_speed, settings.autoPageSpeed, 1..15, default = 3) { value ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(autoPageSpeed = value) })
                    }
                }
            }
            item("images") {
                SplicedColumnGroup(title = stringResource(R.string.manga_config_images)) {
                    SwitchSettingItem(stringResource(R.string.manga_reader_scroll_animation), checked = !settings.disableMangaScrollAnimation) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(disableMangaScrollAnimation = !enabled) })
                    }
                    SwitchSettingItem(stringResource(R.string.manga_reader_image_fade), checked = !settings.disableMangaCrossFade) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(disableMangaCrossFade = !enabled) })
                    }
                    SwitchSettingItem(stringResource(R.string.manga_reader_auto_background), checked = settings.autoBackground) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(autoBackground = enabled) })
                    }
                    if (!settings.autoBackground) ClickableSettingItem(stringResource(R.string.background_color),
                        trailingContent = { AccentColorButton(color = settings.background, onClick = { pickBackground = true }) },
                        onClick = { pickBackground = true })
                    SwitchSettingItem(stringResource(R.string.manga_reader_grayscale), checked = settings.enableGray) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(enableGray = enabled, enableEInk = if (enabled) false else it.enableEInk) })
                    }
                    SwitchSettingItem(stringResource(R.string.manga_reader_eink), checked = settings.enableEInk) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(enableEInk = enabled, enableGray = if (enabled) false else it.enableGray) })
                    }
                    if (settings.enableEInk) MangaConfigSlider(R.string.manga_reader_eink_threshold, settings.eInkThreshold, 0..255, default = 150) { value ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(eInkThreshold = value) })
                    }
                }
            }
            item("cache") {
                SplicedColumnGroup(title = stringResource(R.string.manga_config_cache)) {
                    SwitchSettingItem(stringResource(R.string.manga_reader_preload_chapter), checked = settings.preDownloadNum > 0) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(preDownloadNum = if (enabled) 10 else 0) })
                    }
                    SwitchSettingItem(stringResource(R.string.manga_reader_auto_offline_cache), checked = settings.autoOfflineCache) { enabled ->
                        onIntent(MangaConfigIntent.ChangeSettings { it.copy(autoOfflineCache = enabled) })
                    }
                    if (settings.autoOfflineCache) {
                        AppText(stringResource(R.string.manga_config_cache_shared), style = LegadoTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                        MangaConfigSlider(R.string.pre_download, state.nextChapters, 0..100) { onIntent(MangaConfigIntent.SetNextChapters(it)) }
                        MangaConfigSlider(R.string.pre_download_prev, state.previousChapters, 0..100) { onIntent(MangaConfigIntent.SetPreviousChapters(it)) }
                    }
                    ClickableSettingItem(stringResource(R.string.download_cache_config), onClick = onOpenCacheSettings)
                }
            }
            item("translation") {
                Column {
                    SplicedColumnGroup(title = stringResource(R.string.manga_translation_title)) {
                        SwitchSettingItem(stringResource(R.string.manga_translation_automatic_global), checked = state.automaticTranslation,
                            description = stringResource(R.string.manga_config_translation_hint), enabled = state.translationLoaded) {
                            onIntent(MangaConfigIntent.SetAutomaticTranslation(it))
                        }
                        ClickableSettingItem(stringResource(R.string.ai_config), onClick = onOpenAiSettings)
                    }
                    translationContent()
                }
            }
            state.error?.let { error -> item("error") {
                AppText(error, color = LegadoTheme.colorScheme.error, modifier = Modifier.padding(16.dp), style = LegadoTheme.typography.bodyMedium)
                SmallPlainButton(onClick = { onIntent(MangaConfigIntent.DismissError) }, text = stringResource(R.string.close))
            } }
        }
    }
    if (pickBackground) ColorPickerSheet(show = true, initialColor = settings.background,
        onDismissRequest = { pickBackground = false }, onColorSelected = { color ->
            onIntent(MangaConfigIntent.ChangeSettings { it.copy(background = color) }); pickBackground = false
        })
}

private const val TranslationGroupIndex = 5

@Composable
private fun MangaConfigDropdown(title: Int, value: Int, labels: Array<Int>, values: IntRange = labels.indices,
    description: String? = null, onChange: (Int) -> Unit) {
    DropdownListSettingItem(stringResource(title), value.toString(), description = description,
        displayEntries = labels.map { stringResource(it) }.toTypedArray(), entryValues = values.map(Int::toString).toTypedArray()) {
        onChange(it.toInt())
    }
}

@Composable
private fun MangaConfigSlider(title: Int, value: Int, range: IntRange, default: Int = range.first, onChange: (Int) -> Unit) {
    SliderSettingItem(title = stringResource(title), value = value.toFloat(), defaultValue = default.toFloat(),
        valueRange = range.first.toFloat()..range.last.toFloat(), onValueChange = { onChange(it.roundToInt()) })
}
