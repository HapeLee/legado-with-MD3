package io.legado.app.ui.main.bookshelf

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.config.themeConfig.LabelColorManageSheet
import io.legado.app.ui.config.themeConfig.ThemeConfig
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
                .animateContentSize()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactDropdownSettingItem(
                title = stringResource(R.string.group_style),
                selectedValue = BookshelfConfig.bookGroupStyle.toString(),
                displayEntries = stringArrayResource(R.array.group_style),
                entryValues = Array(stringArrayResource(R.array.group_style).size) { it.toString() },
                onValueChange = { BookshelfConfig.bookGroupStyle = it.toInt() }
            )

            // Sort
            CompactDropdownSettingItem(
                title = stringResource(R.string.sort),
                selectedValue = BookshelfConfig.bookshelfSort.toString(),
                displayEntries = stringArrayResource(R.array.bookshelf_px_array),
                entryValues = Array(stringArrayResource(R.array.bookshelf_px_array).size) { it.toString() },
                onValueChange = { BookshelfConfig.bookshelfSort = it.toInt() }
            )

            // Sort Order
            CompactDropdownSettingItem(
                title = stringResource(R.string.sort_order),
                selectedValue = BookshelfConfig.bookshelfSortOrder.toString(),
                displayEntries = arrayOf(
                    stringResource(R.string.ascending_order),
                    stringResource(R.string.descending_order)
                ),
                entryValues = arrayOf("0", "1"),
                onValueChange = { BookshelfConfig.bookshelfSortOrder = it.toInt() }
            )

            // Layout Mode (non-folder)
            val layoutMode =
                if (isLandscape) BookshelfConfig.bookshelfLayoutModeLandscape
                else BookshelfConfig.bookshelfLayoutModePortrait
            val folderLayoutMode =
                if (isLandscape) BookshelfConfig.bookshelfFolderLayoutModeLandscape
                else BookshelfConfig.bookshelfFolderLayoutModePortrait

            // Folder Layout Mode
            AnimatedVisibility(visible = BookshelfConfig.bookGroupStyle == 2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    PillDivider()

                    CompactDropdownSettingItem(
                        title = "文件夹布局模式",
                        description = stringResource(if (isLandscape) R.string.screen_landscape else R.string.screen_portrait),
                        selectedValue = folderLayoutMode.toString(),
                        displayEntries = arrayOf(stringResource(R.string.layout_mode_list), stringResource(R.string.layout_mode_grid)),
                        entryValues = arrayOf("0", "1"),
                        onValueChange = {
                            if (isLandscape) BookshelfConfig.bookshelfFolderLayoutModeLandscape = it.toInt()
                            else BookshelfConfig.bookshelfFolderLayoutModePortrait = it.toInt()
                        }
                    )

                    AnimatedVisibility(visible = folderLayoutMode == 1) {
                        val folderGridCount =
                            if (isLandscape) BookshelfConfig.bookshelfFolderLayoutGridLandscape
                            else BookshelfConfig.bookshelfFolderLayoutGridPortrait
                        CompactSliderSettingItem(
                            title = stringResource(R.string.number_rows_columns),
                            value = folderGridCount.toFloat(),
                            valueRange = 1f..15f,
                            steps = 14,
                            onValueChange = {
                                if (isLandscape) BookshelfConfig.bookshelfFolderLayoutGridLandscape = it.toInt()
                                else BookshelfConfig.bookshelfFolderLayoutGridPortrait = it.toInt()
                            }
                        )
                    }

                    AnimatedVisibility(visible = folderLayoutMode != 1) {
                        val folderListCount =
                            if (isLandscape) BookshelfConfig.bookshelfFolderLayoutListLandscape
                            else BookshelfConfig.bookshelfFolderLayoutListPortrait
                        CompactSliderSettingItem(
                            title = stringResource(R.string.number_rows_columns),
                            value = folderListCount.toFloat(),
                            valueRange = 1f..5f,
                            steps = 4,
                            onValueChange = {
                                if (isLandscape) BookshelfConfig.bookshelfFolderLayoutListLandscape = it.toInt()
                                else BookshelfConfig.bookshelfFolderLayoutListPortrait = it.toInt()
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = BookshelfConfig.bookGroupStyle == 2 && folderLayoutMode == 0
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactDropdownSettingItem(
                                title = "文件夹列表样式",
                                selectedValue = BookshelfConfig.bookshelfGroupListStyle.toString(),
                                displayEntries = arrayOf("默认", "紧凑", "横排封面"),
                                entryValues = arrayOf("0", "1", "2"),
                                onValueChange = {
                                    BookshelfConfig.bookshelfGroupListStyle = it.toInt()
                                }
                            )
                            AnimatedVisibility(visible = BookshelfConfig.bookshelfGroupListStyle == 2) {
                                CompactSliderSettingItem(
                                    title = "横排封面数量",
                                    value = BookshelfConfig.bookshelfGroupCoverCount.toFloat(),
                                    valueRange = 1f..10f,
                                    steps = 9,
                                    onValueChange = {
                                        BookshelfConfig.bookshelfGroupCoverCount = it.toInt()
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
                    if (isLandscape) BookshelfConfig.bookshelfLayoutModeLandscape = it.toInt()
                    else BookshelfConfig.bookshelfLayoutModePortrait = it.toInt()
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
                        selectedValue = BookshelfConfig.bookshelfGridLayout.toString(),
                        displayEntries = stringArrayResource(R.array.bookshelf_grid_layout),
                        entryValues = Array(stringArrayResource(R.array.bookshelf_grid_layout).size) { it.toString() },
                        onValueChange = { BookshelfConfig.bookshelfGridLayout = it.toInt() }
                    )

                    val gridCount =
                        if (isLandscape) BookshelfConfig.bookshelfLayoutGridLandscape else BookshelfConfig.bookshelfLayoutGridPortrait
                    CompactSliderSettingItem(
                        title = stringResource(R.string.number_rows_columns),
                        value = gridCount.toFloat(),
                        valueRange = 1f..15f,
                        steps = 14,
                        onValueChange = {
                            if (isLandscape) BookshelfConfig.bookshelfLayoutGridLandscape =
                                it.toInt()
                            else BookshelfConfig.bookshelfLayoutGridPortrait = it.toInt()
                        }
                    )

                    CompactSwitchSettingItem(
                        title = stringResource(R.string.compact_title_font),
                        checked = BookshelfConfig.bookshelfTitleSmallFont,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { BookshelfConfig.bookshelfTitleSmallFont = it }
                    )

                    CompactSwitchSettingItem(
                        title = stringResource(R.string.center_aligned_title),
                        checked = BookshelfConfig.bookshelfTitleCenter,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { BookshelfConfig.bookshelfTitleCenter = it }
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
                        checked = BookshelfConfig.bookshelfShowDivider,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { BookshelfConfig.bookshelfShowDivider = it }
                    )

                    CompactClickableSettingItem(
                        title = "日间卡片背景颜色",
                        color = LegadoTheme.colorScheme.surface,
                        onClick = { showColorPicker = true },
                        trailingContent = {
                            if (BookshelfConfig.bookshelfCardColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(BookshelfConfig.bookshelfCardColor))
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
                        title = "夜间卡片背景颜色",
                        color = LegadoTheme.colorScheme.surface,
                        onClick = { showColorPickerDark = true },
                        trailingContent = {
                            if (BookshelfConfig.bookshelfCardColorDark != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(BookshelfConfig.bookshelfCardColorDark))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    if (BookshelfConfig.bookGroupStyle != 2) {
                        CompactSwitchSettingItem(
                            title = stringResource(R.string.compact_mode),
                            checked = BookshelfConfig.bookshelfLayoutCompact,
                            color = LegadoTheme.colorScheme.surface,
                            onCheckedChange = { BookshelfConfig.bookshelfLayoutCompact = it }
                        )
                    }

                    val listColCount =
                        if (isLandscape) BookshelfConfig.bookshelfLayoutListLandscape else BookshelfConfig.bookshelfLayoutListPortrait
                    CompactSliderSettingItem(
                        title = stringResource(R.string.number_rows_columns),
                        value = listColCount.toFloat(),
                        valueRange = 1f..5f,
                        steps = 4,
                        onValueChange = {
                            if (isLandscape) BookshelfConfig.bookshelfLayoutListLandscape =
                                it.toInt()
                            else BookshelfConfig.bookshelfLayoutListPortrait = it.toInt()
                        }
                    )

                    CompactSwitchSettingItem(
                        title = "显示更多信息",
                        checked = BookshelfConfig.showBookIntro,
                        color = LegadoTheme.colorScheme.surface,
                        onCheckedChange = { BookshelfConfig.showBookIntro = it }
                    )

                    AnimatedVisibility(visible = BookshelfConfig.showBookIntro) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactSwitchSettingItem(
                                title = "显示最新章节",
                                checked = BookshelfConfig.bookshelfShowLatestChapter,
                                color = LegadoTheme.colorScheme.surface,
                                onCheckedChange = {
                                    BookshelfConfig.bookshelfShowLatestChapter = it
                                }
                            )

                            CompactSwitchSettingItem(
                                title = "显示简介",
                                checked = BookshelfConfig.bookshelfShowIntro,
                                color = LegadoTheme.colorScheme.surface,
                                onCheckedChange = { BookshelfConfig.bookshelfShowIntro = it }
                            )
                            AnimatedVisibility(
                                visible = BookshelfConfig.bookshelfShowIntro
                            ) {
                                CompactSliderSettingItem(
                                    title = "简介行数",
                                    description = if (BookshelfConfig.bookshelfIntroMaxLines == 0) "显示全部简介" else "显示 ${BookshelfConfig.bookshelfIntroMaxLines} 行简介",
                                    value = BookshelfConfig.bookshelfIntroMaxLines.toFloat(),
                                    valueRange = 0f..10f,
                                    steps = 10,
                                    onValueChange = {
                                        BookshelfConfig.bookshelfIntroMaxLines = it.toInt()
                                    }
                                )
                            }
                            CompactSwitchSettingItem(
                                title = "显示标签",
                                checked = BookshelfConfig.bookshelfShowTag,
                                color = LegadoTheme.colorScheme.surface,
                                onCheckedChange = { BookshelfConfig.bookshelfShowTag = it }
                            )
                            AnimatedVisibility(
                                visible = BookshelfConfig.bookshelfShowTag
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompactSwitchSettingItem(
                                        title = "自定义标签颜色",
                                        checked = ThemeConfig.enableCustomTagColors,
                                        color = LegadoTheme.colorScheme.surface,
                                        onCheckedChange = { ThemeConfig.enableCustomTagColors = it }
                                    )
                                    AnimatedVisibility(visible = ThemeConfig.enableCustomTagColors) {
                                        CompactClickableSettingItem(
                                            title = "管理标签颜色",
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
                value = BookshelfConfig.bookshelfTitleMaxLines.toFloat(),
                valueRange = 1f..5f,
                steps = 4,
                onValueChange = { BookshelfConfig.bookshelfTitleMaxLines = it.toInt() }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.cover_shadow),
                checked = BookshelfConfig.bookshelfCoverShadow,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.bookshelfCoverShadow = it }
            )

            CompactSwitchSettingItem(
                title = "搜索按钮优先打开筛选栏",
                checked = BookshelfConfig.bookshelfSearchActionDirectToSearch,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.bookshelfSearchActionDirectToSearch = it }
            )

            // Switches
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_unread),
                checked = BookshelfConfig.showUnread,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showUnread = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_unread_new),
                checked = BookshelfConfig.showUnreadNew,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showUnreadNew = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_tip),
                checked = BookshelfConfig.showTip,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showTip = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_book_count),
                checked = BookshelfConfig.showBookCount,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showBookCount = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_last_update_time),
                checked = BookshelfConfig.showLastUpdateTime,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showLastUpdateTime = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_wait_up_count),
                checked = BookshelfConfig.showWaitUpCount,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showWaitUpCount = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bookshelf_fast_scroller),
                checked = BookshelfConfig.showBookshelfFastScroller,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showBookshelfFastScroller = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bookshelf_tab_menu),
                checked = BookshelfConfig.shouldShowExpandButton,
                color = LegadoTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.shouldShowExpandButton = it }
            )

            // Refresh Limit
            CompactSliderSettingItem(
                title = stringResource(R.string.bookshelf_update_limit),
                description = if (BookshelfConfig.bookshelfRefreshingLimit <= 0) stringResource(R.string.refresh_limit_unlimited) else stringResource(R.string.refresh_limit_books, BookshelfConfig.bookshelfRefreshingLimit),
                value = BookshelfConfig.bookshelfRefreshingLimit.toFloat(),
                valueRange = 0f..100f,
                steps = 100,
                onValueChange = { BookshelfConfig.bookshelfRefreshingLimit = it.toInt() }
            )
        }

        LabelColorManageSheet(
            show = showLabelColorManage,
            onDismissRequest = { showLabelColorManage = false }
        )

        ColorPickerSheet(
            show = showColorPicker,
            initialColor = if (BookshelfConfig.bookshelfCardColor != 0) BookshelfConfig.bookshelfCardColor else LegadoTheme.colorScheme.surfaceVariant.toArgb(),
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { BookshelfConfig.bookshelfCardColor = it }
        )

        ColorPickerSheet(
            show = showColorPickerDark,
            initialColor = if (BookshelfConfig.bookshelfCardColorDark != 0) BookshelfConfig.bookshelfCardColorDark else LegadoTheme.colorScheme.surfaceVariant.toArgb(),
            onDismissRequest = { showColorPickerDark = false },
            onColorSelected = { BookshelfConfig.bookshelfCardColorDark = it }
        )
    }
}
