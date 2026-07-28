package io.legado.app.ui.config.themeConfig

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.domain.model.settings.AppShellSettings

private data class NavIconDestination(
    val key: String,
    @param:StringRes val labelRes: Int,
    val path: String,
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
            settings.navIconHome
        ),
        NavIconDestination("bookshelf", R.string.bookshelf, settings.navIconBookshelf),
        NavIconDestination("explore", R.string.discovery, settings.navIconExplore),
        NavIconDestination("rss", R.string.rss, settings.navIconRss),
        NavIconDestination("my", R.string.my, settings.navIconMy),
    )

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.theme_config_nav_icons),
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                destinations.forEach { dest ->
                    val label = stringResource(dest.labelRes)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NormalCard(
                            onClick = {
                                onSelectIcon(dest.key)
                            },
                            cornerRadius = 12.dp,
                            containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            if (dest.path.isNotEmpty()) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = dest.path,
                                        contentDescription = label,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    SmallTonalButton(
                                        onClick = { onClearIcon(dest.key) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp),
                                        icon = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(
                                            R.string.theme_config_add_nav_icon,
                                            label
                                        ),
                                        modifier = Modifier.size(32.dp),
                                        tint = LegadoTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        AppText(
                            text = label,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
