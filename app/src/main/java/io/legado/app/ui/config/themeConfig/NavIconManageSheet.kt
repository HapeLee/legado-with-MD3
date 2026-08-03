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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

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

@Composable
private fun NavigationIconSlot(
    label: String,
    path: String,
    onSelect: () -> Unit,
    onClear: () -> Unit,
) {
    NormalCard(
        onClick = onSelect,
        cornerRadius = 12.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        modifier = Modifier.size(40.dp),
    ) {
        if (path.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = path,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                SmallTonalButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp),
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppIcon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.theme_config_add_nav_icon, label),
                    modifier = Modifier.size(24.dp),
                    tint = LegadoTheme.colorScheme.primary,
                )
            }
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
