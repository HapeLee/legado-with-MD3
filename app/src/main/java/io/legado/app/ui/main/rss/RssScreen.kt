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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.favorites.RssFavoritesActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.manage.RssSourceActivity
import io.legado.app.ui.rss.subscription.RuleSubActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.SourceIcon
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.TopBarActionButton
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(
    viewModel: RssViewModel = koinViewModel(),
    onOpenSort: (sourceUrl: String, sortUrl: String?, key: String?) -> Unit,
    onOpenRead: (title: String?, origin: String, link: String?, openUrl: String?) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var sourceToDelete by remember { mutableStateOf<RssSource?>(null) }

    val openRss: (RssSource) -> Unit = { rssSource ->
        if (rssSource.singleUrl) {
            viewModel.getSingleUrl(rssSource) { url ->
                if (url.startsWith("http", true)) {
                    onOpenRead(
                        rssSource.sourceName,
                        url,
                        null,
                        null
                    )
                } else {
                    context.openUrl(url)
                }
            }
        } else {
            onOpenSort(rssSource.sourceUrl, null, null)
        }
    }

    val edit: (RssSource) -> Unit = { rssSource ->
        context.startActivity<RssSourceEditActivity> {
            putExtra("sourceUrl", rssSource.sourceUrl)
        }
    }

    val login: (RssSource) -> Unit = { rssSource ->
        context.startActivity<SourceLoginActivity> {
            putExtra("type", "rssSource")
            putExtra("key", rssSource.sourceUrl)
        }
    }

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
                onClick = { context.startActivity<RuleSubActivity>() },
                imageVector = Icons.Default.Subscriptions,
                contentDescription = stringResource(R.string.rule_subscription)
            )
            TopBarActionButton(
                onClick = { context.startActivity<RssFavoritesActivity>() },
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.favorite)
            )
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                onClick = {
                    context.startActivity<RssSourceActivity>()
                    dismiss()
                },
                text = "订阅源管理"
            )
            PillDivider()
            RoundDropdownMenuItem(
                text = stringResource(R.string.all),
                onClick = {
                    viewModel.setGroup("")
                    dismiss()
                }
            )
            uiState.groups.forEach { group ->
                RoundDropdownMenuItem(
                    text = group,
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
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.items, key = { it.sourceUrl }) { source ->
                RssSourceGridItem(
                    modifier = Modifier.animateItem(),
                    source = source,
                    onClick = { openRss(source) },
                    onTop = { viewModel.topSource(source) },
                    onEdit = { edit(source) },
                    onDelete = { sourceToDelete = source },
                    onDisable = { viewModel.disable(source) },
                    onLogin = { login(source) }
                )
            }
        }
    }

    AppAlertDialog(
        data = sourceToDelete,
        onDismissRequest = { sourceToDelete = null },
        title = stringResource(R.string.draw),
        confirmText = stringResource(R.string.yes),
        onConfirm = { source ->
            viewModel.del(source)
            sourceToDelete = null
        },
        dismissText = stringResource(R.string.no),
        onDismiss = { sourceToDelete = null }
    )
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
                    text = stringResource(R.string.to_top),
                    onClick = {
                        onTop()
                        showMenu = false
                    }
                )
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                    text = stringResource(R.string.edit),
                    onClick = {
                        onEdit()
                        showMenu = false
                    }
                )
                if (!source.loginUrl.isNullOrBlank()) {
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                        text = stringResource(R.string.login),
                        onClick = {
                            onLogin()
                            showMenu = false
                        }
                    )
                }
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.Default.Close) },
                    text = stringResource(R.string.disable_source),
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
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDelete()
                        showMenu = false
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = source.sourceName,
            style = LegadoTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
