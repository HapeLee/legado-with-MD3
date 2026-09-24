package io.legado.app.feature.settings.themeconfig

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
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.default_home_page
import io.legado.app.feature.settings.res.main_navigation_settings
import io.legado.app.feature.settings.res.nav_label_mode
import io.legado.app.feature.settings.res.theme_config_nav_icons
import io.legado.app.feature.settings.res.theme_config_nav_icons_custom_count
import io.legado.app.feature.settings.res.theme_config_nav_icons_default
import io.legado.app.feature.settings.res.label_vis_mode
import io.legado.app.feature.settings.res.label_vis_mode_value
import io.legado.app.feature.settings.res.home
import io.legado.app.feature.settings.res.bookshelf
import io.legado.app.feature.settings.res.discovery
import io.legado.app.feature.settings.res.rss
import io.legado.app.feature.settings.res.my
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.utils.move
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import io.legado.app.ui.main.MainNavLabel
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * M5-19c：从 `:app` 的 `ui/config/themeConfig/MainNavigationSettingsSheet.kt` 迁入。
 *
 * **正文逐字保留**（脚本化等价改写），改写类别：
 * 1. 包名 → `io.legado.app.feature.settings.themeconfig`；
 * 2. `stringResource` / `stringArrayResource` → CMP 版，`stringArrayResource(…)` 补
 *    `.toTypedArray()`（CMP 返回 `List`，组件要 `Array`）；
 * 3. `R.string.*` → `Res.string.*` + 逐 key import；`R.array.*` 同理；
 * 4. ⚠️ `stringResource(x.label.toRes())` → `stringResource(mainNavLabelRes(x.label))`：
 *    `toRes()` 是**宿主**的文案映射（`:app/ui/main/MainNavLabelText.kt`），共享层用不了 ⇒
 *    改为本文件内的 `mainNavLabelRes`（`Res.string.home/…`）。这与 M5-16a/19b 的
 *    「共享层承载语义、宿主承载文案」同向：共享层自己要有文案时就用 `Res`。
 *
 * 前置（M5-19c-pre 已就位）：`MainDestination` / `MainDestinationIcons` / `MutableList.move`
 * 都已上提到共享层。
 */

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
    onSetLabelVisibilityMode: (String) -> Unit,
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
        title = stringResource(Res.string.main_navigation_settings),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            CompactDropdownSettingItem(
                title = stringResource(Res.string.default_home_page),
                selectedValue = selectedDefault,
                displayEntries = visibleItems.map { stringResource(mainNavLabelRes(it.label)) }.toTypedArray(),
                entryValues = visibleItems.map { it.route }.toTypedArray(),
                onValueChange = onSetDefault,
            )
            CompactDropdownSettingItem(
                title = stringResource(Res.string.nav_label_mode),
                selectedValue = settings.labelVisibilityMode,
                displayEntries = stringArrayResource(Res.array.label_vis_mode).toTypedArray(),
                entryValues = stringArrayResource(Res.array.label_vis_mode_value).toTypedArray(),
                onValueChange = onSetLabelVisibilityMode,
            )
            Spacer(modifier = Modifier.padding(bottom = 4.dp))
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
            CompactClickableSettingItem(
                title = stringResource(Res.string.theme_config_nav_icons),
                description = if (customIconCount > 0) {
                    stringResource(Res.string.theme_config_nav_icons_custom_count, customIconCount)
                } else {
                    stringResource(Res.string.theme_config_nav_icons_default)
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
                        title = stringResource(mainNavLabelRes(destination.label)),
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
                            title = stringResource(mainNavLabelRes(destination.label)),
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

/** M5-19c：共享侧的 [MainNavLabel] → 文案（宿主那份 `toRes()` 不适用于共享层）。 */
private fun mainNavLabelRes(label: MainNavLabel): StringResource = when (label) {
    MainNavLabel.Home -> Res.string.home
    MainNavLabel.Bookshelf -> Res.string.bookshelf
    MainNavLabel.Explore -> Res.string.discovery
    MainNavLabel.Rss -> Res.string.rss
    MainNavLabel.My -> Res.string.my
}
