package io.legado.app.ui.config.themeConfig

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.MochiSheetLayout
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private data class NavIconDestination(
    val key: String,
    @param:StringRes val labelRes: Int,
    val unselectedPath: String,
    val selectedPath: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavIconManageSheet(
    show: Boolean,
    settings: AppShellSettings,
    onDismissRequest: () -> Unit,
    onSelectIcon: (String) -> Unit,
    onClearIcon: (String) -> Unit,
) {
    val destinations = listOf(
        NavIconDestination(
            "home",
            R.string.home,
            settings.navIconHome,
            settings.navIconHomeSelected,
        ),
        NavIconDestination(
            "bookshelf",
            R.string.bookshelf,
            settings.navIconBookshelf,
            settings.navIconBookshelfSelected,
        ),
        NavIconDestination(
            "explore",
            R.string.discovery,
            settings.navIconExplore,
            settings.navIconExploreSelected,
        ),
        NavIconDestination("rss", R.string.rss, settings.navIconRss, settings.navIconRssSelected),
        NavIconDestination("my", R.string.my, settings.navIconMy, settings.navIconMySelected),
    )

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.theme_config_nav_icons),
        mochiLayout = MochiSheetLayout.Compact,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f))
                NavigationIconColumnHeader(stringResource(R.string.theme_config_nav_icon_unselected))
                NavigationIconColumnHeader(stringResource(R.string.theme_config_nav_icon_selected))
            }
            destinations.forEach { destination ->
                NormalCard(
                    modifier = Modifier
                        .fillMaxWidth(),
                    cornerRadius = 16.dp,
                    containerColor = LegadoTheme.colorScheme.onSheetContent,
                ) {
                    Row(
                        modifier = Modifier.padding(all = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            imageVector = AppIcons.mainDestination(
                                destination.mainDestination,
                                selected = false,
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(24.dp),
                        )
                        AppText(
                            text = stringResource(destination.labelRes),
                            style = LegadoTheme.typography.labelMediumEmphasized,
                            modifier = Modifier.weight(1f),
                        )
                        NavigationIconSlot(
                            label = stringResource(R.string.theme_config_nav_icon_unselected),
                            path = destination.unselectedPath,
                            onSelect = { onSelectIcon(destination.key) },
                            onClear = { onClearIcon(destination.key) },
                        )
                        NavigationIconSlot(
                            label = stringResource(R.string.theme_config_nav_icon_selected),
                            path = destination.selectedPath,
                            onSelect = { onSelectIcon("${destination.key}:selected") },
                            onClear = { onClearIcon("${destination.key}:selected") },
                        )
                    }
                }
            }
        }
    }
}

/** 导航图标卡菜单的稳定 ID：声明与分派共用，避免字面量漂移。 */
private enum class NavIconMenuAction(val id: String, @param:StringRes val labelRes: Int) {
    Replace("replace_nav_icon", R.string.theme_config_replace_nav_icon),
    Clear("clear_nav_icon", R.string.delete),
}

@Composable
private fun navIconMenuGroups(): ImmutableList<AppActionGroup> {
    val actions = NavIconMenuAction.entries.map { action ->
        AppAction(id = action.id, label = stringResource(action.labelRes))
    }
    return persistentListOf(
        AppActionGroup(id = "nav_icon_slot", actions = actions.toImmutableList()),
    )
}

@Composable
private fun NavigationIconSlot(
    label: String,
    path: String,
    onSelect: () -> Unit,
    onClear: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        NormalCard(
            onClick = {
                if (path.isNotEmpty()) menuExpanded = true else onSelect()
            },
            cornerRadius = 12.dp,
            containerColor = LegadoTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(40.dp),
        ) {
            if (path.isNotEmpty()) {
                AsyncImage(
                    model = path,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppIcon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(
                            R.string.theme_config_add_nav_icon,
                            label
                        ),
                        modifier = Modifier.size(24.dp),
                        tint = LegadoTheme.colorScheme.primary,
                    )
                }
            }
        }
        // 卡片自身的点击语义（未设图标时直接选图）留在页面，开合由页面持有，
        // 入口只渲染弹层；受控模式没有 trigger 节点，因此锚点取这层 Box 的矩形。
        AppActionMenu(
            groups = navIconMenuGroups(),
            onAction = { id ->
                when (id) {
                    NavIconMenuAction.Replace.id -> onSelect()
                    NavIconMenuAction.Clear.id -> onClear()
                }
            },
            modifier = Modifier.matchParentSize(),
            embeddedExpanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            trigger = {},
        )
    }
}

@Composable
private fun NavigationIconColumnHeader(label: String) {
    Box(
        modifier = Modifier.width(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = label,
            style = LegadoTheme.typography.labelSmallEmphasized
        )
    }
}

private val NavIconDestination.mainDestination: MainDestination
    get() = when (key) {
        MainDestination.Home.route -> MainDestination.Home
        MainDestination.Bookshelf.route -> MainDestination.Bookshelf
        MainDestination.Explore.route -> MainDestination.Explore
        MainDestination.Rss.route -> MainDestination.Rss
        else -> MainDestination.My
    }
