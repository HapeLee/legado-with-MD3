package io.legado.app.ui.main.bookshelf

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
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

    AppModalBottomSheet(
        title = stringResource(R.string.bookshelf_layout),
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        GlassCard() {

        }
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

            // Layout Mode
            val layoutMode =
                if (isLandscape) BookshelfConfig.bookshelfLayoutModeLandscape
                else BookshelfConfig.bookshelfLayoutModePortrait

            CompactDropdownSettingItem(
                title = stringResource(R.string.layout_mode),
                description = stringResource(if (isLandscape) R.string.screen_landscape else R.string.screen_portrait),
                selectedValue = layoutMode.toString(),
                displayEntries = arrayOf(stringResource(R.string.layout_mode_list),stringResource(R.string.layout_mode_grid)),
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
                        color = MaterialTheme.colorScheme.surface,
                        onCheckedChange = { BookshelfConfig.bookshelfTitleSmallFont = it }
                    )

                    CompactSwitchSettingItem(
                        title = stringResource(R.string.center_aligned_title),
                        checked = BookshelfConfig.bookshelfTitleCenter,
                        color = MaterialTheme.colorScheme.surface,
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
                        title = stringResource(R.string.compact_mode),
                        checked = BookshelfConfig.bookshelfLayoutCompact,
                        color = MaterialTheme.colorScheme.surface,
                        onCheckedChange = { BookshelfConfig.bookshelfLayoutCompact = it }
                    )

                    CompactSwitchSettingItem(
                        title = stringResource(R.string.show_divider_line),
                        checked = BookshelfConfig.bookshelfShowDivider,
                        color = MaterialTheme.colorScheme.surface,
                        onCheckedChange = { BookshelfConfig.bookshelfShowDivider = it }
                    )

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
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.bookshelfCoverShadow = it }
            )

            CompactSwitchSettingItem(
                title = "搜索按钮优先打开筛选栏",
                checked = BookshelfConfig.bookshelfSearchActionDirectToSearch,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.bookshelfSearchActionDirectToSearch = it }
            )

            // Switches
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_unread),
                checked = BookshelfConfig.showUnread,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showUnread = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_unread_new),
                checked = BookshelfConfig.showUnreadNew,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showUnreadNew = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_tip),
                checked = BookshelfConfig.showTip,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showTip = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_book_count),
                checked = BookshelfConfig.showBookCount,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showBookCount = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_last_update_time),
                checked = BookshelfConfig.showLastUpdateTime,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showLastUpdateTime = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_wait_up_count),
                checked = BookshelfConfig.showWaitUpCount,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showWaitUpCount = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bookshelf_fast_scroller),
                checked = BookshelfConfig.showBookshelfFastScroller,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showBookshelfFastScroller = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bookshelf_tab_menu),
                checked = BookshelfConfig.shouldShowExpandButton,
                color = MaterialTheme.colorScheme.surface,
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
    }
}
