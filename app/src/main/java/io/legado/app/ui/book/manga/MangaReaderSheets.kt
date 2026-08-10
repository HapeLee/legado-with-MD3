package io.legado.app.ui.book.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import kotlin.math.roundToInt

@Composable
internal fun MangaReaderSettingsSheet(
    sheet: MangaReaderSheet,
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val settings = state.settings
    AppModalBottomSheet(
        show = true,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissSheet) },
        title = when (sheet) {
            MangaReaderSheet.Reader -> stringResource(R.string.manga_setting)
            MangaReaderSheet.AutoRead -> stringResource(R.string.manga_reader_auto_read)
            MangaReaderSheet.ColorFilter -> stringResource(R.string.manga_reader_display_filter)
            MangaReaderSheet.ClickActions -> stringResource(R.string.manga_reader_click_areas)
            MangaReaderSheet.ChangeSource -> stringResource(R.string.change_origin)
            MangaReaderSheet.SourceActions -> stringResource(R.string.manga_reader_source_actions)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (sheet) {
                MangaReaderSheet.Reader -> {
                    Text(stringResource(R.string.read_type), style = MaterialTheme.typography.titleMedium)
                    listOf(
                        MangaScrollMode.WEBTOON to stringResource(R.string.webtoon),
                        MangaScrollMode.WEBTOON_WITH_GAP to stringResource(R.string.manga_reader_webtoon_gap),
                        MangaScrollMode.PAGE_LEFT_TO_RIGHT to stringResource(R.string.manga_reader_left_to_right),
                        MangaScrollMode.PAGE_RIGHT_TO_LEFT to stringResource(R.string.manga_reader_right_to_left),
                        MangaScrollMode.PAGE_TOP_TO_BOTTOM to stringResource(R.string.manga_reader_top_to_bottom),
                    ).forEach { (mode, label) ->
                        TextButton(onClick = {
                            onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.SCROLL_MODE, mode))
                        }) { Text(if (settings.scrollMode == mode) "✓ $label" else label) }
                    }
                    SettingSlider(stringResource(R.string.manga_reader_side_padding), settings.sidePaddingPercent, 0..45) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.SIDE_PADDING, it))
                    }
                    SettingSlider(stringResource(R.string.manga_reader_preload_pages), settings.preDownloadCount, 0..30) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.PRE_DOWNLOAD, it))
                    }
                    Text(stringResource(R.string.background_color), style = MaterialTheme.typography.titleMedium)
                    SettingSlider(stringResource(R.string.manga_reader_red), (settings.backgroundColor.red * 255).roundToInt(), 0..255) {
                        updateInt(onIntent, MangaReaderSettingKey.BACKGROUND_RED, it)
                    }
                    SettingSlider(stringResource(R.string.manga_reader_green), (settings.backgroundColor.green * 255).roundToInt(), 0..255) {
                        updateInt(onIntent, MangaReaderSettingKey.BACKGROUND_GREEN, it)
                    }
                    SettingSlider(stringResource(R.string.manga_reader_blue), (settings.backgroundColor.blue * 255).roundToInt(), 0..255) {
                        updateInt(onIntent, MangaReaderSettingKey.BACKGROUND_BLUE, it)
                    }
                    SettingSwitch(stringResource(R.string.manga_reader_pinch_zoom), !settings.disableScale) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.DISABLE_SCALE, (!it).intValue))
                    }
                    SettingSwitch(stringResource(R.string.manga_reader_tap_turn), !settings.disableClickScroll) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.DISABLE_CLICK_SCROLL, (!it).intValue))
                    }
                    SettingSwitch(stringResource(R.string.manga_reader_scroll_animation), !settings.disableScrollAnimation) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.DISABLE_SCROLL_ANIMATION, (!it).intValue))
                    }
                    SettingSwitch(stringResource(R.string.manga_reader_image_fade), !settings.disableCrossFade) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.DISABLE_CROSS_FADE, (!it).intValue))
                    }
                    SettingSwitch(stringResource(R.string.manga_reader_long_press_save), settings.longPressEnabled) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.LONG_PRESS, it.intValue))
                    }
                    SettingSwitch(stringResource(R.string.manga_reader_volume_page), settings.volumeKeyPage) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.VOLUME_KEY_PAGE, it.intValue))
                    }
                    SettingSwitch(stringResource(R.string.manga_reader_reverse_volume), settings.reverseVolumeKeyPage) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.REVERSE_VOLUME_KEY_PAGE, it.intValue))
                    }
                    SettingSwitch(stringResource(R.string.manga_reader_hide_edge_prompt), settings.hideMangaTitle) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.HIDE_MANGA_TITLE, it.intValue))
                    }
                    Text(stringResource(R.string.footer), style = MaterialTheme.typography.titleMedium)
                    SettingSwitch(stringResource(R.string.manga_reader_hide_footer), settings.hideFooter) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_FOOTER, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_hide_chapter_name), settings.hideChapterName) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER_NAME, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_hide_page_number), settings.hidePageNumber) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PAGE_NUMBER, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_hide_page_label), settings.hidePageNumberLabel) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PAGE_NUMBER_LABEL, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_hide_chapter_progress), settings.hideChapter) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_hide_chapter_label), settings.hideChapterLabel) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER_LABEL, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_hide_total_progress), settings.hideProgress) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PROGRESS, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_hide_progress_label), settings.hideProgressLabel) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PROGRESS_LABEL, it) }
                    Text(stringResource(R.string.manga_reader_footer_alignment), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { updateInt(onIntent, MangaReaderSettingKey.FOOTER_ALIGNMENT, 0) }) {
                        val label = stringResource(R.string.manga_reader_left_align)
                        Text(if (settings.footerAlignment == 0) "✓ $label" else label)
                    }
                    TextButton(onClick = { updateInt(onIntent, MangaReaderSettingKey.FOOTER_ALIGNMENT, 1) }) {
                        val label = stringResource(R.string.manga_reader_center_align)
                        Text(if (settings.footerAlignment == 1) "✓ $label" else label)
                    }
                }
                MangaReaderSheet.AutoRead -> {
                    SettingSwitch(stringResource(R.string.manga_reader_enable_auto_read), state.autoReadEnabled) {
                        onIntent(MangaReaderIntent.ToggleAutoRead)
                    }
                    SettingSlider(stringResource(R.string.manga_reader_auto_speed), settings.autoReadSpeed, 1..15) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.AUTO_READ_SPEED, it))
                    }
                }
                MangaReaderSheet.ColorFilter -> {
                    SettingSwitch(stringResource(R.string.manga_reader_grayscale), settings.enableGray) { updateBoolean(onIntent, MangaReaderSettingKey.ENABLE_GRAY, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_eink), settings.enableEInk) { updateBoolean(onIntent, MangaReaderSettingKey.ENABLE_EINK, it) }
                    SettingSlider(stringResource(R.string.manga_reader_eink_threshold), settings.eInkThreshold, 0..255) {
                        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.EINK_THRESHOLD, it))
                    }
                    SettingSlider(stringResource(R.string.manga_reader_filter_red), settings.filterRed, 0..255) { updateInt(onIntent, MangaReaderSettingKey.FILTER_RED, it) }
                    SettingSlider(stringResource(R.string.manga_reader_filter_green), settings.filterGreen, 0..255) { updateInt(onIntent, MangaReaderSettingKey.FILTER_GREEN, it) }
                    SettingSlider(stringResource(R.string.manga_reader_filter_blue), settings.filterBlue, 0..255) { updateInt(onIntent, MangaReaderSettingKey.FILTER_BLUE, it) }
                    SettingSlider(stringResource(R.string.manga_reader_filter_alpha), settings.filterAlpha, 0..255) { updateInt(onIntent, MangaReaderSettingKey.FILTER_ALPHA, it) }
                    SettingSwitch(stringResource(R.string.manga_reader_system_brightness), settings.autoBrightness) { updateBoolean(onIntent, MangaReaderSettingKey.AUTO_BRIGHTNESS, it) }
                    if (!settings.autoBrightness) {
                        SettingSlider(stringResource(R.string.manga_reader_screen_brightness), settings.brightness, 0..255) {
                            updateInt(onIntent, MangaReaderSettingKey.BRIGHTNESS, it)
                        }
                    }
                }
                MangaReaderSheet.ClickActions -> {
                    val labels = mapOf(
                        -1 to stringResource(R.string.previous_chapter),
                        0 to stringResource(R.string.manga_reader_menu),
                        1 to stringResource(R.string.manga_reader_next_page),
                        2 to stringResource(R.string.manga_reader_previous_page),
                    )
                    repeat(3) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            repeat(3) { column ->
                                val index = row * 3 + column
                                val action = settings.clickActions.getOrElse(index) { 0 }
                                TextButton(onClick = {
                                    val next = nextMangaClickAction(action)
                                    onIntent(MangaReaderIntent.UpdateClickAction(index, next))
                                }) { Text(labels[action].orEmpty()) }
                            }
                        }
                    }
                    Text(stringResource(R.string.manga_reader_click_cycle_hint), style = MaterialTheme.typography.bodySmall)
                }
                MangaReaderSheet.ChangeSource -> Unit
                MangaReaderSheet.SourceActions -> {
                    TextButton(onClick = { onIntent(MangaReaderIntent.OpenSourceLogin) }) { Text(stringResource(R.string.login)) }
                    TextButton(onClick = { onIntent(MangaReaderIntent.RequestPayCurrentChapter) }) { Text(stringResource(R.string.manga_reader_buy_chapter)) }
                    TextButton(onClick = { onIntent(MangaReaderIntent.OpenSourceEdit) }) { Text(stringResource(R.string.edit_source)) }
                    TextButton(onClick = {
                        onIntent(MangaReaderIntent.DisableCurrentSource)
                        onIntent(MangaReaderIntent.DismissSheet)
                    }) { Text(stringResource(R.string.disable_source)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val Boolean.intValue: Int get() = if (this) 1 else 0

private fun updateBoolean(
    onIntent: (MangaReaderIntent) -> Unit,
    key: MangaReaderSettingKey,
    value: Boolean,
) = onIntent(MangaReaderIntent.UpdateSetting(key, value.intValue))

private fun updateInt(
    onIntent: (MangaReaderIntent) -> Unit,
    key: MangaReaderSettingKey,
    value: Int,
) = onIntent(MangaReaderIntent.UpdateSetting(key, value))

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    var pending by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$label: ${pending.toInt()}")
        Slider(
            value = pending.coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { pending = it },
            onValueChangeFinished = { onValueChange(pending.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}
