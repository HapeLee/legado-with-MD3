package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.feature.settings.search.SearchableSetting
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.utils.move
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 「首页与导航」区块：默认主页、导航标签、导航栏图标以及导航项的显示与排序。
 *
 * 直接内联在外观设置页的分组里（不再用浮层承载），所以顺序的提交仍走
 * [MainDestination.ordered] 的持久化字符串。排序列表会随分组一起滚动，
 * 这里限制自身高度以便嵌套在页面的 LazyColumn 内。
 */
@Composable
fun MainNavigationSettingsSection(
    settings: AppShellSettings,
    onSetDefault: (String) -> Unit,
    onSetLabelVisibilityMode: (String) -> Unit,
    onOpenNavIcons: () -> Unit,
    onSetVisible: (String, Boolean) -> Unit,
    onSetOrder: (String) -> Unit,
) {
    // 拖动期间真实顺序只在这一份本地列表里变，松手（或无障碍上下移）后才提交。
    var navigationItems by remember {
        mutableStateOf(MainDestination.ordered(settings.mainNavigationOrder))
    }
    val navigationListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(navigationListState) { from, to ->
        if (from.index != to.index) {
            navigationItems = navigationItems.toMutableList().apply {
                move(from.index, to.index)
            }
            onSetOrder(navigationItems.joinToString(",") { it.route })
        }
    }

    // 重置默认值、导入配置等外部改动能同步回来；拖动中不回灌，避免与手势抢状态。
    LaunchedEffect(settings.mainNavigationOrder, reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            navigationItems = MainDestination.ordered(settings.mainNavigationOrder)
        }
    }

    fun isRouteVisible(route: String): Boolean = when (route) {
        MainDestination.Home.route -> settings.showHome
        MainDestination.Explore.route -> settings.showDiscovery
        MainDestination.Rss.route -> settings.showRss
        else -> true
    }

    // 书架与我的恒显示：AppShellSettings 没有对应开关，关闭会立刻回弹。
    fun isRouteToggleable(route: String): Boolean =
        route == MainDestination.Home.route ||
            route == MainDestination.Explore.route ||
            route == MainDestination.Rss.route

    val visibleItems = navigationItems.filter { isRouteVisible(it.route) }
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
    val hapticFeedback = LocalHapticFeedback.current

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
        SearchableSetting("theme.main_navigation_settings") {
            LazyColumn(
                state = navigationListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    items = navigationItems,
                    key = { it.route },
                ) { destination ->
                    val visible = isRouteVisible(destination.route)
                    val toggleable = isRouteToggleable(destination.route)
                    ReorderableItem(
                        state = reorderableState,
                        key = destination.route,
                    ) { isDragging ->
                        val title = stringResource(destination.labelId)
                        val dragHandleModifier = remember(reorderableState, destination.route) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate
                                    )
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                            )
                        }
                        SwitchSettingItem(
                            title = title,
                            checked = visible,
                            enabled = toggleable,
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .reorderAccessibility(
                                    index = navigationItems.indexOf(destination),
                                    itemCount = navigationItems.size,
                                    description = stringResource(R.string.a11y_reorder_named, title),
                                    onMove = { from, to ->
                                        navigationItems = navigationItems.toMutableList().apply {
                                            add(to, removeAt(from))
                                        }
                                        onSetOrder(navigationItems.joinToString(",") { it.route })
                                    },
                                )
                                .then(dragHandleModifier),
                            onCheckedChange = { checked ->
                                onSetVisible(destination.route, checked)
                            },
                        )
                    }
                }
            }
        }
    }
}
