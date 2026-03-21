package io.legado.app.ui.main.bookshelf

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSliderSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfConfigSheet(
    onDismissRequest: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    GlassModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .animateContentSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.bookshelf_layout),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Group Style
            CompactDropdownSettingItem(
                title = stringResource(R.string.group_style),
                selectedValue = BookshelfConfig.bookGroupStyle.toString(),
                displayEntries = stringArrayResource(R.array.group_style),
                entryValues = Array(stringArrayResource(R.array.group_style).size) { it.toString() },
                imageVector = Icons.Default.Style,
                onValueChange = { BookshelfConfig.bookGroupStyle = it.toInt() }
            )

            // Sort
            CompactDropdownSettingItem(
                title = stringResource(R.string.sort),
                selectedValue = BookshelfConfig.bookshelfSort.toString(),
                displayEntries = stringArrayResource(R.array.bookshelf_px_array),
                entryValues = Array(stringArrayResource(R.array.bookshelf_px_array).size) { it.toString() },
                imageVector = Icons.AutoMirrored.Filled.Sort,
                onValueChange = { BookshelfConfig.bookshelfSort = it.toInt() }
            )

            // Sort Order
            CompactDropdownSettingItem(
                title = "排序方向",
                selectedValue = BookshelfConfig.bookshelfSortOrder.toString(),
                displayEntries = arrayOf(
                    stringResource(R.string.ascending_order),
                    stringResource(R.string.descending_order)
                ),
                entryValues = arrayOf("0", "1"),
                imageVector = Icons.Default.SortByAlpha,
                onValueChange = { BookshelfConfig.bookshelfSortOrder = it.toInt() }
            )

            // Layout Mode
            val layoutMode =
                if (isLandscape) BookshelfConfig.bookshelfLayoutModeLandscape else BookshelfConfig.bookshelfLayoutModePortrait
            CompactDropdownSettingItem(
                title = "布局模式",
                description = stringResource(if (isLandscape) R.string.screen_landscape else R.string.screen_portrait),
                selectedValue = layoutMode.toString(),
                displayEntries = arrayOf("列表", "网格"),
                entryValues = arrayOf("0", "1"),
                imageVector = Icons.AutoMirrored.Filled.ViewList,
                onValueChange = {
                    if (isLandscape) BookshelfConfig.bookshelfLayoutModeLandscape = it.toInt()
                    else BookshelfConfig.bookshelfLayoutModePortrait = it.toInt()
                }
            )

            if (layoutMode == 1) {
                CompactDropdownSettingItem(
                    title = "网格样式",
                    selectedValue = BookshelfConfig.bookshelfGridLayout.toString(),
                    displayEntries = stringArrayResource(R.array.bookshelf_grid_layout),
                    entryValues = Array(stringArrayResource(R.array.bookshelf_grid_layout).size) { it.toString() },
                    imageVector = Icons.Default.ViewCompact,
                    onValueChange = { BookshelfConfig.bookshelfGridLayout = it.toInt() }
                )
            } else {
                CompactSwitchSettingItem(
                    title = "紧凑模式",
                    checked = BookshelfConfig.bookshelfLayoutCompact,
                    imageVector = Icons.Default.ViewCompact,
                    color = MaterialTheme.colorScheme.surface,
                    onCheckedChange = { BookshelfConfig.bookshelfLayoutCompact = it }
                )
            }

            // Grid Count
            val gridCount =
                if (isLandscape) BookshelfConfig.bookshelfLayoutGridLandscape else BookshelfConfig.bookshelfLayoutGridPortrait
            CompactSliderSettingItem(
                title = stringResource(R.string.number_rows_columns),
                value = gridCount.toFloat(),
                valueRange = 1f..15f,
                steps = 14,
                onValueChange = {
                    if (isLandscape) BookshelfConfig.bookshelfLayoutGridLandscape = it.toInt()
                    else BookshelfConfig.bookshelfLayoutGridPortrait = it.toInt()
                }
            )

            CompactSwitchSettingItem(
                title = "标题小字体",
                checked = BookshelfConfig.bookshelfTitleSmallFont,
                imageVector = Icons.Default.TextFormat,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.bookshelfTitleSmallFont = it }
            )

            CompactSwitchSettingItem(
                title = "标题居中",
                checked = BookshelfConfig.bookshelfTitleCenter,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.bookshelfTitleCenter = it }
            )

            CompactSliderSettingItem(
                title = "标题最大行数",
                value = BookshelfConfig.bookshelfTitleMaxLines.toFloat(),
                valueRange = 1f..5f,
                steps = 4,
                onValueChange = { BookshelfConfig.bookshelfTitleMaxLines = it.toInt() }
            )

            CompactSwitchSettingItem(
                title = "封面阴影",
                checked = BookshelfConfig.bookshelfCoverShadow,
                imageVector = Icons.Default.BlurOn,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.bookshelfCoverShadow = it }
            )

            // Switches
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_unread),
                checked = BookshelfConfig.showUnread,
                imageVector = Icons.Default.Notifications,
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
                imageVector = Icons.Default.Info,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.showTip = it }
            )

            CompactSwitchSettingItem(
                title = stringResource(R.string.show_last_update_time),
                checked = BookshelfConfig.showLastUpdateTime,
                imageVector = Icons.Default.History,
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
                imageVector = Icons.Default.Menu,
                color = MaterialTheme.colorScheme.surface,
                onCheckedChange = { BookshelfConfig.shouldShowExpandButton = it }
            )

            // Refresh Limit
            CompactSliderSettingItem(
                title = stringResource(R.string.bookshelf_update_limit),
                description = if (BookshelfConfig.bookshelfRefreshingLimit <= 0) "无限制" else "${BookshelfConfig.bookshelfRefreshingLimit} 本",
                value = BookshelfConfig.bookshelfRefreshingLimit.toFloat(),
                valueRange = 0f..100f,
                steps = 100,
                onValueChange = { BookshelfConfig.bookshelfRefreshingLimit = it.toInt() }
            )
        }
    }
}
