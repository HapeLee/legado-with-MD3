package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.utils.move
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MainNavigationSettingsSheet(
    show: Boolean,
    settings: AppShellSettings,
    onDismissRequest: () -> Unit,
    onSetVisible: (String, Boolean) -> Unit,
    onSetOrder: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onRequestNavigationIcon: (String) -> Unit,
    onClearNavigationIcon: (String) -> Unit,
) {
    var showNavigationIcons by remember(show) { mutableStateOf(false) }
    var navigationItems by remember(show) {
        mutableStateOf(MainDestination.ordered(settings.mainNavigationOrder))
    }
    val navigationListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(navigationListState) { from, to ->
            navigationItems = navigationItems.toMutableList().apply {
                move(from.index, to.index)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            onSetOrder(navigationItems.joinToString(",") { it.route })
        }
    }

    fun isRouteVisible(route: String): Boolean = when (route) {
        MainDestination.Home.route -> settings.showHome
        MainDestination.Explore.route -> settings.showDiscovery
        MainDestination.Rss.route -> settings.showRss
        else -> true
    }

    fun getVisibilityForRoute(route: String): Boolean = isRouteVisible(route)

    fun setVisibilityForRoute(route: String, visible: Boolean) {
        onSetVisible(route, visible)
        if (!visible) {
            val item = navigationItems.find { it.route == route } ?: return
            navigationItems = navigationItems.filter { it.route != route } + item
            onSetOrder(navigationItems.joinToString(",") { it.route })
        }
    }

    val visibleItems = navigationItems.filter { isRouteVisible(it.route) }
    val hiddenItems = navigationItems.filter { !isRouteVisible(it.route) }
    val selectedDefault = settings.defaultHomePage.takeIf { route ->
        visibleItems.any { it.route == route }
    } ?: MainDestination.Bookshelf.route

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
            CompactDropdownSettingItem(
                title = stringResource(R.string.default_home_page),
                selectedValue = selectedDefault,
                displayEntries = visibleItems.map { stringResource(it.labelId) }.toTypedArray(),
                entryValues = visibleItems.map { it.route }.toTypedArray(),
                onValueChange = onSetDefault,
            )
            Spacer(modifier = Modifier.padding(bottom = 4.dp))
            val customIconCount = listOf(
                settings.navIconHome,
                settings.navIconBookshelf,
                settings.navIconExplore,
                settings.navIconRss,
                settings.navIconMy,
            ).count { it.isNotEmpty() }
            CompactClickableSettingItem(
                title = stringResource(R.string.theme_config_nav_icons),
                description = if (customIconCount > 0) {
                    stringResource(R.string.theme_config_nav_icons_custom_count, customIconCount)
                } else {
                    stringResource(R.string.theme_config_nav_icons_default)
                },
                onClick = { showNavigationIcons = true },
            )
            Spacer(modifier = Modifier.padding(bottom = 4.dp))
            LazyColumn(
                state = navigationListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = visibleItems,
                    key = { it.route },
                ) { destination ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = destination.route,
                        reorderIndex = visibleItems.indexOf(destination),
                        reorderItemCount = visibleItems.size,
                        onMoveItem = { from, to ->
                            val fromItem = visibleItems[from]
                            val toItem = visibleItems[to]
                            navigationItems = navigationItems.toMutableList().apply {
                                move(indexOf(fromItem), indexOf(toItem))
                            }
                            onSetOrder(navigationItems.joinToString(",") { it.route })
                        },
                        title = stringResource(destination.labelId),
                        isEnabled = true,
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        onEnabledChange = {
                            setVisibilityForRoute(destination.route, false)
                        },
                    )
                }
                if (hiddenItems.isNotEmpty()) {
                    items(
                        items = hiddenItems,
                        key = { it.route },
                    ) { destination ->
                        ReorderableSelectionItem(
                            state = reorderableState,
                            key = destination.route,
                            title = stringResource(destination.labelId),
                            isEnabled = false,
                            canReorder = false,
                            containerColor = LegadoTheme.colorScheme.onSheetContent,
                            onEnabledChange = {
                                setVisibilityForRoute(destination.route, true)
                            },
                        )
                    }
                }
            }
        }
    }

    NavIconManageSheet(
        show = showNavigationIcons,
        settings = settings,
        onDismissRequest = { showNavigationIcons = false },
        onSelectIcon = onRequestNavigationIcon,
        onClearIcon = onClearNavigationIcon,
    )
}
