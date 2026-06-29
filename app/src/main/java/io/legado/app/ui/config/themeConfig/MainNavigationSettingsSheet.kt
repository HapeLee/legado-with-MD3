package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.move
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MainNavigationSettingsSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    val homeLabel = stringResource(R.string.home)
    val bookshelfLabel = stringResource(R.string.bookshelf)
    val discoveryLabel = stringResource(R.string.discovery)
    val rssLabel = stringResource(R.string.rss)
    val myLabel = stringResource(R.string.my)
    val destinations = listOfNotNull(
        if (ThemeConfig.showHome) homeLabel to MainDestination.Home.route else null,
        bookshelfLabel to MainDestination.Bookshelf.route,
        if (ThemeConfig.showDiscovery) {
            discoveryLabel to MainDestination.Explore.route
        } else {
            null
        },
        if (ThemeConfig.showRss) rssLabel to MainDestination.Rss.route else null,
        myLabel to MainDestination.My.route,
    )
    val selectedDefault = ThemeConfig.defaultHomePage.takeIf { route ->
        destinations.any { it.second == route }
    } ?: MainDestination.Bookshelf.route
    val configuredOrder = MainDestination.ordered(ThemeConfig.mainNavigationOrder)
    var navigationItems by remember(show) { mutableStateOf(configuredOrder) }
    val navigationListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(navigationListState) { from, to ->
            navigationItems = navigationItems.toMutableList().apply {
                move(from.index, to.index)
            }
        }

    LaunchedEffect(configuredOrder, reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            navigationItems = configuredOrder
        }
    }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            ThemeConfig.mainNavigationOrder =
                navigationItems.joinToString(",") { it.route }
        }
    }

    fun updateVisibility(
        route: String,
        visible: Boolean,
        update: (Boolean) -> Unit,
    ) {
        update(visible)
        if (!visible && ThemeConfig.defaultHomePage == route) {
            ThemeConfig.defaultHomePage = MainDestination.Bookshelf.route
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.main_navigation_settings),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_home),
                checked = ThemeConfig.showHome,
                onCheckedChange = {
                    updateVisibility(
                        MainDestination.Home.route,
                        it,
                    ) { value -> ThemeConfig.showHome = value }
                },
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_discovery),
                checked = ThemeConfig.showDiscovery,
                onCheckedChange = {
                    updateVisibility(
                        MainDestination.Explore.route,
                        it,
                    ) { value -> ThemeConfig.showDiscovery = value }
                },
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_rss),
                checked = ThemeConfig.showRss,
                onCheckedChange = {
                    updateVisibility(
                        MainDestination.Rss.route,
                        it,
                    ) { value -> ThemeConfig.showRss = value }
                },
            )
            CompactDropdownSettingItem(
                title = stringResource(R.string.default_home_page),
                selectedValue = selectedDefault,
                displayEntries = destinations.map { it.first }.toTypedArray(),
                entryValues = destinations.map { it.second }.toTypedArray(),
                onValueChange = { ThemeConfig.defaultHomePage = it },
            )
            AppText(
                text = stringResource(R.string.navigation_order),
                style = LegadoTheme.typography.titleSmallEmphasized,
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 8.dp,
                ),
            )
            LazyColumn(
                state = navigationListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = navigationItems,
                    key = { it.route },
                ) { destination ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = destination.route,
                        title = stringResource(destination.labelId),
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                    )
                }
            }
        }
    }
}
