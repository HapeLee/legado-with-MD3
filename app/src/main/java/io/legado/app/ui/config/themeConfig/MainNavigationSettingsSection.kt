package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.feature.settings.search.SearchableSetting
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.move
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** 首页导航的常用设置，显示与排序通过独立面板调整。 */
@Composable
fun MainNavigationSettingsSection(
    settings: AppShellSettings,
    onSetDefault: (String) -> Unit,
    onSetLabelVisibilityMode: (String) -> Unit,
    onOpenNavIcons: () -> Unit,
    onOpenNavigationOrder: () -> Unit,
    navigationLayout: @Composable (appearance: @Composable () -> Unit) -> Unit,
) {
    val visibleItems = MainDestination.ordered(settings.mainNavigationOrder)
        .filter { it.isVisible(settings) }
    val selectedDefault = settings.defaultHomePage.takeIf { route ->
        visibleItems.any { it.route == route }
    } ?: MainDestination.Bookshelf.route
    val customIconCount = listOf(
        settings.navIconHome,
        settings.navIconBookshelf,
        settings.navIconExplore,
        settings.navIconRss,
        settings.navIconMy,
        settings.navIconHomeSelected,
        settings.navIconBookshelfSelected,
        settings.navIconExploreSelected,
        settings.navIconRssSelected,
        settings.navIconMySelected,
    ).count { it.isNotEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SearchableSetting("theme.default_home_page") {
            DropdownListSettingItem(
                title = stringResource(R.string.default_home_page),
                selectedValue = selectedDefault,
                displayEntries = visibleItems.map { stringResource(it.labelId) }.toTypedArray(),
                entryValues = visibleItems.map { it.route }.toTypedArray(),
                onValueChange = onSetDefault,
            )
        }
        SearchableSetting("theme.main_navigation_settings") {
            ClickableSettingItem(
                title = stringResource(R.string.theme_main_navigation_order),
                description = stringResource(R.string.a11y_long_press_reorder),
                onClick = onOpenNavigationOrder,
            )
        }
        navigationLayout {
            SearchableSetting("theme.nav_label_mode") {
                DropdownListSettingItem(
                    title = stringResource(R.string.nav_label_mode),
                    selectedValue = settings.labelVisibilityMode,
                    displayEntries = stringArrayResource(R.array.label_vis_mode),
                    entryValues = stringArrayResource(R.array.label_vis_mode_value),
                    onValueChange = onSetLabelVisibilityMode,
                )
            }
            SearchableSetting("theme.theme_config_nav_icons") {
                ClickableSettingItem(
                    title = stringResource(R.string.theme_config_nav_icons),
                    description = if (customIconCount > 0) {
                        stringResource(R.string.theme_config_nav_icons_custom_count, customIconCount)
                    } else {
                        stringResource(R.string.theme_config_nav_icons_default)
                    },
                    onClick = onOpenNavIcons,
                )
            }
        }
    }
}

@Composable
internal fun MainNavigationOrderSheet(
    show: Boolean,
    settings: AppShellSettings,
    onDismissRequest: () -> Unit,
    onSetVisible: (String, Boolean) -> Unit,
    onSetOrder: (String) -> Unit,
) {
    val order = settings.mainNavigationOrder
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.theme_main_navigation_order),
    ) {
        var navigationItems by remember {
            mutableStateOf(MainDestination.ordered(order))
        }
        fun moveItem(from: Int, to: Int) {
            if (from == to) return
            navigationItems = navigationItems.toMutableList().apply { move(from, to) }
            onSetOrder(navigationItems.joinToString(",") { it.route })
        }

        val navigationListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(navigationListState) { from, to ->
            moveItem(from.index, to.index)
        }

        // 重置默认值、导入配置等外部改动能同步回来；拖动中不回灌，避免与手势抢状态。
        LaunchedEffect(order, reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                navigationItems = MainDestination.ordered(order)
            }
        }

        val hapticFeedback = LocalHapticFeedback.current
        LazyColumn(
            state = navigationListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(navigationItems, key = { _, destination -> destination.route }) { index, destination ->
                val title = stringResource(destination.labelId)
                val visibilityLabel = stringResource(R.string.theme_navigation_item_visibility, title)
                val canChangeVisibility = when (destination) {
                    MainDestination.Home, MainDestination.Explore, MainDestination.Rss -> true
                    MainDestination.Bookshelf, MainDestination.My -> false
                }
                ReorderableItem(reorderableState, key = destination.route) { isDragging ->
                    NormalCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        elevation = if (isDragging) 4.dp else 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .reorderAccessibility(
                                        index = index,
                                        itemCount = navigationItems.size,
                                        description = stringResource(R.string.a11y_reorder_named, title),
                                        onMove = ::moveItem,
                                    )
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                AppText(
                                    text = title,
                                    modifier = Modifier.clearAndSetSemantics {},
                                    style = LegadoTheme.typography.titleSmall,
                                )
                            }
                            AdaptiveSwitch(
                                checked = destination.isVisible(settings),
                                onCheckedChange = { onSetVisible(destination.route, it) },
                                enabled = canChangeVisibility,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .semantics {
                                        contentDescription = visibilityLabel
                                        role = Role.Switch
                                        if (!canChangeVisibility) disabled()
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun MainDestination.isVisible(settings: AppShellSettings): Boolean = when (this) {
    MainDestination.Home -> settings.showHome
    MainDestination.Explore -> settings.showDiscovery
    MainDestination.Rss -> settings.showRss
    MainDestination.Bookshelf, MainDestination.My -> true
}
