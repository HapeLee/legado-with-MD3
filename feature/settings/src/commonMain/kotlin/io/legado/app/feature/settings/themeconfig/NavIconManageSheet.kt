package io.legado.app.feature.settings.themeconfig

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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.bookshelf
import io.legado.app.feature.settings.res.delete
import io.legado.app.feature.settings.res.discovery
import io.legado.app.feature.settings.res.home
import io.legado.app.feature.settings.res.my
import io.legado.app.feature.settings.res.rss
import io.legado.app.feature.settings.res.theme_config_add_nav_icon
import io.legado.app.feature.settings.res.theme_config_nav_icon_selected
import io.legado.app.feature.settings.res.theme_config_nav_icon_unselected
import io.legado.app.feature.settings.res.theme_config_nav_icons
import io.legado.app.feature.settings.res.theme_config_replace_nav_icon
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.main.mainDestinationIcon
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private data class NavIconDestination(
    val key: String,
    val labelRes: StringResource,
    val unselectedPath: String,
    val selectedPath: String,
)

@OptIn(ExperimentalMaterial3Api::class)
/**
 * M5-19c：从 `:app` 的 `ui/config/themeConfig/NavIconManageSheet.kt` 迁入。
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
            Res.string.home,
            settings.navIconHome,
            settings.navIconHomeSelected,
        ),
        NavIconDestination(
            "bookshelf",
            Res.string.bookshelf,
            settings.navIconBookshelf,
            settings.navIconBookshelfSelected,
        ),
        NavIconDestination(
            "explore",
            Res.string.discovery,
            settings.navIconExplore,
            settings.navIconExploreSelected,
        ),
        NavIconDestination("rss", Res.string.rss, settings.navIconRss, settings.navIconRssSelected),
        NavIconDestination("my", Res.string.my, settings.navIconMy, settings.navIconMySelected),
    )

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.theme_config_nav_icons),
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
                NavigationIconColumnHeader(stringResource(Res.string.theme_config_nav_icon_unselected))
                NavigationIconColumnHeader(stringResource(Res.string.theme_config_nav_icon_selected))
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
                            imageVector = mainDestinationIcon(
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
                            label = stringResource(Res.string.theme_config_nav_icon_unselected),
                            path = destination.unselectedPath,
                            onSelect = { onSelectIcon(destination.key) },
                            onClear = { onClearIcon(destination.key) },
                        )
                        NavigationIconSlot(
                            label = stringResource(Res.string.theme_config_nav_icon_selected),
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
                            Res.string.theme_config_add_nav_icon,
                            label
                        ),
                        modifier = Modifier.size(24.dp),
                        tint = LegadoTheme.colorScheme.primary,
                    )
                }
            }
        }
        RoundDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(Res.string.theme_config_replace_nav_icon),
                onClick = {
                    dismiss()
                    onSelect()
                },
            )
            RoundDropdownMenuItem(
                text = stringResource(Res.string.delete),
                onClick = {
                    dismiss()
                    onClear()
                },
            )
        }
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
