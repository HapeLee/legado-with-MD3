package io.legado.app.ui.main.rss

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.widget.components.SourceIcon
import io.legado.app.ui.widget.components.button.TopBarActionButton
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(
    viewModel: RssViewModel,
    onOpenRss: (RssSource) -> Unit,
    onEdit: (RssSource) -> Unit,
    onOpenStar: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenRuleSub: () -> Unit,
    onDelete: (RssSource) -> Unit,
    onLogin: (RssSource) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    ListScaffold(
        title = stringResource(R.string.rss),
        state = uiState,
        subtitle = uiState.group.ifEmpty { "全部" },
        onBackClick = null,
        onSearchToggle = { viewModel.toggleSearchVisible(it) },
        onSearchQueryChange = { viewModel.search(it) },
        searchPlaceholder = stringResource(R.string.search_rss_source),
        topBarActions = {
            TopBarActionButton(
                onClick = onOpenRuleSub,
                imageVector = Icons.Outlined.Subscriptions,
                contentDescription = stringResource(R.string.rule_subscription)
            )
            TopBarActionButton(
                onClick = onOpenStar,
                imageVector = Icons.Outlined.Star,
                contentDescription = stringResource(R.string.favorite)
            )
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                onClick = onOpenConfig,
                leadingIcon = { MenuItemIcon(Icons.Outlined.Settings) },
                text = { Text("订阅源管理") }
            )
            PillDivider()
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.Default.Group) },
                text = { Text(stringResource(R.string.all)) },
                onClick = {
                    viewModel.setGroup("")
                    dismiss()
                }
            )
            uiState.groups.forEach { group ->
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Outlined.Label) },
                    text = { Text(group) },
                    onClick = {
                        viewModel.setGroup(group)
                        dismiss()
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 72.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.items, key = { it.sourceUrl }) { source ->
                RssSourceGridItem(
                    modifier = Modifier.animateItem(),
                    source = source,
                    onClick = { onOpenRss(source) },
                    onTop = { viewModel.topSource(source) },
                    onEdit = { onEdit(source) },
                    onDelete = { onDelete(source) },
                    onDisable = { viewModel.disable(source) },
                    onLogin = { onLogin(source) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RssSourceGridItem(
    modifier: Modifier = Modifier,
    source: RssSource,
    onClick: () -> Unit,
    onTop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDisable: () -> Unit,
    onLogin: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            SourceIcon(
                path = source.sourceIcon.ifEmpty { R.drawable.image_rss },
                sourceOrigin = source.sourceUrl,
                modifier = Modifier.size(48.dp)
            )
            RoundDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                PillHeaderDivider(title = source.sourceName)
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.Default.VerticalAlignTop) },
                    text = { Text(stringResource(R.string.to_top)) },
                    onClick = {
                        onTop()
                        showMenu = false
                    }
                )
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = {
                        onEdit()
                        showMenu = false
                    }
                )
                if (!source.loginUrl.isNullOrBlank()) {
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                        text = { Text(stringResource(R.string.login)) },
                        onClick = {
                            onLogin()
                            showMenu = false
                        }
                    )
                }
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.Default.Close) },
                    text = { Text(stringResource(R.string.disable_source)) },
                    onClick = {
                        onDisable()
                        showMenu = false
                    }
                )
                RoundDropdownMenuItem(
                    leadingIcon = {
                        MenuItemIcon(
                            Icons.Default.Delete,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        onDelete()
                        showMenu = false
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = source.sourceName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
