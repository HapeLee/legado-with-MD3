package io.legado.app.ui.main.rss

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.manage.RssSourceActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.SourceIcon
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(
    viewModel: RssViewModel = koinViewModel(),
    onOpenSort: (sourceUrl: String, sortUrl: String?, key: String?) -> Unit,
    onOpenRead: (title: String?, origin: String, link: String?, openUrl: String?) -> Unit,
    onOpenRuleSub: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sourceToDeleteUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceToDelete = remember(sourceToDeleteUrl, uiState.items) {
        uiState.items.firstOrNull { it.sourceUrl == sourceToDeleteUrl }
    }
    val currentContext by rememberUpdatedState(context)
    val currentOnOpenSort by rememberUpdatedState(onOpenSort)
    val currentOnOpenRead by rememberUpdatedState(onOpenRead)
    val currentOnOpenRuleSub by rememberUpdatedState(onOpenRuleSub)
    val currentOnOpenFavorites by rememberUpdatedState(onOpenFavorites)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RssEffect.OpenSort -> {
                    currentOnOpenSort(effect.sourceUrl, effect.sortUrl, effect.key)
                }

                is RssEffect.OpenRead -> {
                    currentOnOpenRead(effect.title, effect.origin, effect.link, effect.openUrl)
                }

                is RssEffect.OpenExternalUrl -> {
                    currentContext.openUrl(effect.url)
                }

                is RssEffect.OpenSourceEdit -> {
                    currentContext.startActivity<RssSourceEditActivity> {
                        putExtra("sourceUrl", effect.sourceUrl)
                    }
                }

                is RssEffect.Login -> {
                    currentContext.startActivity<SourceLoginActivity> {
                        putExtra("type", "rssSource")
                        putExtra("key", effect.sourceUrl)
                    }
                }

                RssEffect.OpenRuleSub -> {
                    currentOnOpenRuleSub()
                }

                RssEffect.OpenFavorites -> {
                    currentOnOpenFavorites()
                }

                RssEffect.OpenSourceManage -> {
                    currentContext.startActivity<RssSourceActivity>()
                }
            }
        }
    }

    ListScaffold(
        title = stringResource(R.string.rss),
        state = uiState,
        subtitle = uiState.group.ifEmpty { stringResource(R.string.all) },
        onBackClick = null,
        onSearchToggle = { viewModel.toggleSearchVisible(it) },
        onSearchQueryChange = { viewModel.search(it) },
        searchPlaceholder = stringResource(R.string.search_rss_source),
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                onClick = {
                    viewModel.openSourceManage()
                    dismiss()
                },
                text = stringResource(R.string.rss_feed_management),
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openRuleSub() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AppIcon(
                                imageVector = Icons.Default.Subscriptions,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            AppText(
                                text = stringResource(R.string.rule_subscription),
                                style = LegadoTheme.typography.labelMediumEmphasized
                            )
                        }
                    }
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openFavorites() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AppIcon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            AppText(
                                text = stringResource(R.string.favorite),
                                style = LegadoTheme.typography.labelMediumEmphasized
                            )
                        }
                    }
                }
            }

            items(uiState.items, key = { it.sourceUrl }) { source ->
                RssSourceGridItem(
                    modifier = Modifier.animateItem(),
                    source = source,
                    onClick = { viewModel.openSource(source) },
                    onTop = { viewModel.topSource(source) },
                    onEdit = { viewModel.openSourceEdit(source) },
                    onDelete = { sourceToDeleteUrl = source.sourceUrl },
                    onDisable = { viewModel.disable(source) },
                    onLogin = { viewModel.login(source) }
                )
            }
        }
    }

    AppAlertDialog(
        data = sourceToDelete,
        onDismissRequest = { sourceToDeleteUrl = null },
        title = stringResource(R.string.draw),
        confirmText = stringResource(R.string.yes),
        onConfirm = { source ->
            viewModel.del(source)
            sourceToDeleteUrl = null
        },
        dismissText = stringResource(R.string.no),
        onDismiss = { sourceToDeleteUrl = null }
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
