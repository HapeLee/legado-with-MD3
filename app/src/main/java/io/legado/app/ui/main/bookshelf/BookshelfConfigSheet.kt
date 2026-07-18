package io.legado.app.ui.main.bookshelf

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.gateway.BookshelfBooleanSetting
import io.legado.app.domain.gateway.BookshelfIntSetting
import io.legado.app.domain.gateway.BookshelfSettingsUpdate
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.domain.gateway.ThemeBooleanSetting
import io.legado.app.domain.gateway.ThemeSettingsUpdate
import io.legado.app.ui.config.themeConfig.LabelColorManageSheet
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSliderSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfConfigSheet(
    show: Boolean,
    settings: BookshelfSettings,
    onUpdate: (BookshelfSettingsUpdate) -> Unit,
    enableCustomTagColors: Boolean,
    customTagColors: List<TagColorPair>,
    themeColor: Int,
    onThemeUpdate: (ThemeSettingsUpdate) -> Unit,
    onCustomTagColorsChange: (List<TagColorPair>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showLabelColorManage by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showColorPickerDark by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        title = stringResource(R.string.bookshelf_layout),
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactDropdownSettingItem(
                title = stringResource(R.string.group_style),
                selectedValue = settings.bookGroupStyle.toString(),
                displayEntries = stringArrayResource(R.array.group_style),
                entryValues = Array(stringArrayResource(R.array.group_style).size) { it.toString() },
                onValueChange = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.BookGroupStyle, it.toInt())) }
            )

            // Sort
            CompactDropdownSettingItem(
                title = stringResource(R.string.sort),
                selectedValue = settings.bookshelfSort.toString(),
                displayEntries = stringArrayResource(R.array.bookshelf_px_array),
                entryValues = Array(stringArrayResource(R.array.bookshelf_px_array).size) { it.toString() },
                onValueChange = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.Sort, it.toInt())) }
            )

            // Sort Order
            CompactDropdownSettingItem(
                title = stringResource(R.string.sort_order),
                selectedValue = settings.bookshelfSortOrder.toString(),
                displayEntries = arrayOf(
                    stringResource(R.string.ascending_order),
                    stringResource(R.string.descending_order)
                ),
                entryValues = arrayOf("0", "1"),
                onValueChange = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.SortOrder, it.toInt())) }
            )

            // Layout Mode (non-folder)
            val layoutMode =
                if (isLandscape) settings.bookshelfLayoutModeLandscape
                else settings.bookshelfLayoutModePortrait
            val folderLayoutMode =
                if (isLandscape) settings.bookshelfFolderLayoutModeLandscape
                else settings.bookshelfFolderLayoutModePortrait

            // Folder Layout Mode
            AnimatedVisibility(visible = settings.bookGroupStyle == 2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    PillDivider()

                    CompactDropdownSettingItem(
                        title = stringResource(R.string.folder_layout_mode),
                        description = stringResource(if (isLandscape) R.string.screen_landscape else R.string.screen_portrait),
                        selectedValue = folderLayoutMode.toString(),
                        displayEntries = arrayOf(stringResource(R.string.layout_mode_list), stringResource(R.string.layout_mode_grid)),
                        entryValues = arrayOf("0", "1"),
                        onValueChange = {
                            onUpdate(
                                BookshelfSettingsUpdate.IntValue(
                                    if (isLandscape) BookshelfIntSetting.FolderLayoutModeLandscape
                                    else BookshelfIntSetting.FolderLayoutModePortrait,
                                    it.toInt(),
                                )
                            )
                        }
                    )

                    AnimatedVisibility(visible = folderLayoutMode == 1) {
                        val folderGridCount =
                            if (isLandscape) settings.bookshelfFolderLayoutGridLandscape
                            else settings.bookshelfFolderLayoutGridPortrait
                        CompactSliderSettingItem(
                            title = stringResource(R.string.number_rows_columns),
                            value = folderGridCount.toFloat(),
                            valueRange = 1f..15f,
                            steps = 14,
                            onValueChange = {
                                onUpdate(
                                    BookshelfSettingsUpdate.IntValue(
                                        if (isLandscape) BookshelfIntSetting.FolderLayoutGridLandscape
                                        else BookshelfIntSetting.FolderLayoutGridPortrait,
                                        it.toInt(),
                                    )
                                )
                            }
                        )
                    }

                    AnimatedVisibility(visible = folderLayoutMode != 1) {
                        val folderListCount =
                            if (isLandscape) settings.bookshelfFolderLayoutListLandscape
                            else settings.bookshelfFolderLayoutListPortrait
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactSliderSettingItem(
                                title = stringResource(R.string.number_rows_columns),
                                value = folderListCount.toFloat(),
                                valueRange = 1f..5f,
                                steps = 4,
                                onValueChange = {
                                    onUpdate(
                                        BookshelfSettingsUpdate.IntValue(
                                            if (isLandscape) BookshelfIntSetting.FolderLayoutListLandscape
                                            else BookshelfIntSetting.FolderLayoutListPortrait,
                                            it.toInt(),
                                        )
                                    )
                                }
                            )

                            CompactSliderSettingItem(
                                title = stringResource(R.string.list_cover_width),
                                value = settings.bookshelfListCoverWidth.toFloat(),
                                valueRange = 40f..120f,
                                steps = 80,
                                onValueChange = {
                                    onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.ListCoverWidth, it.toInt()))
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = settings.bookGroupStyle == 2 && folderLayoutMode == 0
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactDropdownSettingItem(
                                title = stringResource(R.string.folder_list_style),
                                selectedValue = settings.bookshelfGroupListStyle.toString(),
                                displayEntries = arrayOf(stringResource(R.string.group), stringResource(R.string.compact_list), stringResource(R.string.horizontal_cover_count)),
                                entryValues = arrayOf("0", "1", "2"),
                                onValueChange = {
                                    onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.GroupListStyle, it.toInt()))
                                }
                            )
                            AnimatedVisibility(visible = settings.bookshelfGroupListStyle == 2) {
                                CompactSliderSettingItem(
                                    title = stringResource(R.string.horizontal_cover_count),
                                    value = settings.bookshelfGroupCoverCount.toFloat(),
                                    valueRange = 1f..10f,
                                    steps = 9,
                                    onValueChange = {
                                        onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.GroupCoverCount, it.toInt()))
                                    }
                                )
                            }
                        }
                    }

                    PillDivider()
                }
            }

            CompactDropdownSettingItem(
                title = stringResource(R.string.layout_mode),
                description = stringResource(if (isLandscape) R.string.screen_landscape else R.string.screen_portrait),
                selectedValue = layoutMode.toString(),
                displayEntries = arrayOf(
                    stringResource(R.string.layout_mode_list),
                    stringResource(R.string.layout_mode_grid)
                ),
                entryValues = arrayOf("0", "1"),
                onValueChange = {
                    onUpdate(
                        BookshelfSettingsUpdate.IntValue(
                            if (isLandscape) BookshelfIntSetting.LayoutModeLandscape
                            else BookshelfIntSetting.LayoutModePortrait,
                            it.toInt(),
                        )
                    )
                }
            )

            AnimatedVisibility(
                visible = layoutMode == 1
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactDropdownSettingItem(
                        title = stringResource(R.string.grid_style),
                        selectedValue = settings.bookshelfGridLayout.toString(),
                        displayEntries = stringArrayResource(R.array.bookshelf_grid_layout),
                        entryValues = Array(stringArrayResource(R.array.bookshelf_grid_layout).size) { it.toString() },
                        onValueChange = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.GridLayout, it.toInt())) }
                    )

                    val gridCount =
                        if (isLandscape) settings.bookshelfLayoutGridLandscape else settings.bookshelfLayoutGridPortrait
                    CompactSliderSettingItem(
                        title = stringResource(R.string.number_rows_columns),
                        value = gridCount.toFloat(),
                        valueRange = 1f..15f,
                        steps = 14,
                        onValueChange = {
                            onUpdate(
                                BookshelfSettingsUpdate.IntValue(
                                    if (isLandscape) BookshelfIntSetting.LayoutGridLandscape
                                    else BookshelfIntSetting.LayoutGridPortrait,
                                    it.toInt(),
                                )
                            )
                        }
                    )

                    CompactSwitchSettingItem(
                        title = stringResource(R.string.compact_title_font),
                        checked = settings.bookshelfTitleSmallFont,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.TitleSmallFont, it)) }
                    )

                    CompactSwitchSettingItem(
                        title = stringResource(R.string.center_aligned_title),
                        checked = settings.bookshelfTitleCenter,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.TitleCenter, it)) }
                    )

                    CompactSliderSettingItem(
                        title = stringResource(R.string.grid_cover_width),
                        value = settings.bookshelfGridCoverWidth.toFloat(),
                        valueRange = 40f..150f,
                        steps = 110,
                        onValueChange = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.GridCoverWidth, it.toInt())) }
                    )
                }
            }

            AnimatedVisibility(
                visible = layoutMode != 1
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactSwitchSettingItem(
                        title = stringResource(R.string.show_divider_line),
                        checked = settings.bookshelfShowDivider,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowDivider, it)) }
                    )

                    CompactClickableSettingItem(
                        title = stringResource(R.string.day_card_bg_color),
                        color = LegadoTheme.colorScheme.surface,
                        onClick = { showColorPicker = true },
                        trailingContent = {
                            if (settings.bookshelfCardColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(settings.bookshelfCardColor))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    CompactClickableSettingItem(
                        title = stringResource(R.string.night_card_bg_color),
                        color = LegadoTheme.colorScheme.surface,
                        onClick = { showColorPickerDark = true },
                        trailingContent = {
                            if (settings.bookshelfCardColorDark != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(settings.bookshelfCardColorDark))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    CompactSwitchSettingItem(
                        title = stringResource(R.string.compact_details),
                        checked = settings.bookshelfLayoutCompact,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.LayoutCompact, it)) }
                    )

                    val listColCount =
                        if (isLandscape) settings.bookshelfLayoutListLandscape else settings.bookshelfLayoutListPortrait
                    CompactSliderSettingItem(
                        title = stringResource(R.string.list_cover_width),
                        value = settings.bookshelfListCoverWidth.toFloat(),
                        valueRange = 40f..120f,
                        steps = 80,
                        onValueChange = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.ListCoverWidth, it.toInt())) }
                    )

                    CompactSliderSettingItem(
                        title = stringResource(R.string.number_rows_columns),
                        value = listColCount.toFloat(),
                        valueRange = 1f..5f,
                        steps = 4,
                        onValueChange = {
                            onUpdate(
                                BookshelfSettingsUpdate.IntValue(
                                    if (isLandscape) BookshelfIntSetting.LayoutListLandscape
                                    else BookshelfIntSetting.LayoutListPortrait,
                                    it.toInt(),
                                )
                            )
                        }
                    )

                    CompactSwitchSettingItem(
                        title = stringResource(R.string.show_more_info),
                        checked = settings.showBookIntro,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowBookIntro, it)) }
                    )

                    AnimatedVisibility(visible = settings.showBookIntro) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactSwitchSettingItem(
                                title = stringResource(R.string.show_latest_chapter),
                                checked = settings.bookshelfShowLatestChapter,
                                color = LegadoTheme.colorScheme.surface,
                                onCheckedChange = {
                                    onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowLatestChapter, it))
                                }
                            )

                            CompactSwitchSettingItem(
                                title = stringResource(R.string.show_synopsis),
                                checked = settings.bookshelfShowIntro,
                                color = LegadoTheme.colorScheme.surface,
                                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowIntro, it)) }
                            )
                            AnimatedVisibility(
                                visible = settings.bookshelfShowIntro
                            ) {
                                CompactSliderSettingItem(
                                    title = stringResource(R.string.synopsis_lines),
                                    description = if (settings.bookshelfIntroMaxLines == 0) stringResource(R.string.show_all_synopsis) else stringResource(R.string.show_lines_synopsis, settings.bookshelfIntroMaxLines),
                                    value = settings.bookshelfIntroMaxLines.toFloat(),
                                    valueRange = 0f..10f,
                                    steps = 10,
                                    onValueChange = {
                                        onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.IntroMaxLines, it.toInt()))
                                    }
                                )
                            }
                            CompactSwitchSettingItem(
                                title = stringResource(R.string.show_tags),
                                checked = settings.bookshelfShowTag,
                                color = LegadoTheme.colorScheme.surface,
                                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowTag, it)) }
                            )
                            AnimatedVisibility(
                                visible = settings.bookshelfShowTag
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompactSwitchSettingItem(
                                        title = stringResource(R.string.custom_tag_colors),
                                        checked = enableCustomTagColors,
                                        color = LegadoTheme.colorScheme.surface,
                                        onCheckedChange = {
                                            onThemeUpdate(
                                                ThemeSettingsUpdate.BooleanValue(
                                                    ThemeBooleanSetting.EnableCustomTagColors,
                                                    it,
                                                )
                                            )
                                        }
                                    )
                                    AnimatedVisibility(visible = enableCustomTagColors) {
                                        CompactClickableSettingItem(
                                            title = stringResource(R.string.manage_tag_colors),
                                            color = LegadoTheme.colorScheme.surface,
                                            onClick = { showLabelColorManage = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }



            CompactSliderSettingItem(
                title = stringResource(R.string.max_title_lines),
                value = settings.bookshelfTitleMaxLines.toFloat(),
                valueRange = 1f..5f,
                steps = 4,
                onValueChange = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.TitleMaxLines, it.toInt())) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.cover_shadow),
                checked = settings.bookshelfCoverShadow,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.CoverShadow, it)) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.search_filter_first),
                checked = settings.bookshelfSearchActionDirectToSearch,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.SearchActionDirectToSearch, it)) }
            )

            // Switches
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_unread),
                checked = settings.showUnread,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowUnread, it)) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_unread_new),
                checked = settings.showUnreadNew,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowUnreadNew, it)) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_tip),
                checked = settings.showTip,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowTip, it)) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_book_count),
                checked = settings.showBookCount,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowBookCount, it)) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_last_update_time),
                checked = settings.showLastUpdateTime,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowLastUpdateTime, it)) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_wait_up_count),
                checked = settings.showWaitUpCount,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowWaitUpCount, it)) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bookshelf_fast_scroller),
                checked = settings.showBookshelfFastScroller,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowFastScroller, it)) }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bookshelf_tab_menu),
                checked = settings.shouldShowExpandButton,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { onUpdate(BookshelfSettingsUpdate.BooleanValue(BookshelfBooleanSetting.ShowExpandButton, it)) }
            )

            // Refresh Limit
            CompactSliderSettingItem(
                title = stringResource(R.string.bookshelf_update_limit),
                description = if (settings.bookshelfRefreshingLimit <= 0) stringResource(R.string.refresh_limit_unlimited) else stringResource(R.string.refresh_limit_books, settings.bookshelfRefreshingLimit),
                value = settings.bookshelfRefreshingLimit.toFloat(),
                valueRange = 0f..100f,
                steps = 100,
                onValueChange = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.RefreshingLimit, it.toInt())) }
            )

            Spacer(modifier = Modifier.height(32.dp))

        }

        LabelColorManageSheet(
            show = showLabelColorManage,
            themeColor = themeColor,
            colors = customTagColors,
            onColorsChange = onCustomTagColorsChange,
            onDismissRequest = { showLabelColorManage = false }
        )

        ColorPickerSheet(
            show = showColorPicker,
            initialColor = if (settings.bookshelfCardColor != 0) settings.bookshelfCardColor else LegadoTheme.colorScheme.surfaceVariant.toArgb(),
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.CardColor, it)) }
        )

        ColorPickerSheet(
            show = showColorPickerDark,
            initialColor = if (settings.bookshelfCardColorDark != 0) settings.bookshelfCardColorDark else LegadoTheme.colorScheme.surfaceVariant.toArgb(),
            onDismissRequest = { showColorPickerDark = false },
            onColorSelected = { onUpdate(BookshelfSettingsUpdate.IntValue(BookshelfIntSetting.CardColorDark, it)) }
        )
    }
}
