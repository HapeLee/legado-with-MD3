package io.legado.app.ui.main.rss

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.LocalBottomOverlayPadding
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.coui.rememberAppVerticalOverscrollEffect
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.image.sourceIcon.SourceIcon
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.openUrl
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssRouteScreen(
    viewModel: RssViewModel = koinViewModel(),
    onOpenSort: (sourceUrl: String, sortUrl: String?, key: String?) -> Unit,
    onOpenRead: (
        title: String?,
        origin: String,
        link: String?,
        openUrl: String?,
        startPage: Boolean
    ) -> Unit,
    onOpenRuleSub: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenLogin: (sourceUrl: String) -> Unit,
    onOpenSourceEdit: (sourceUrl: String) -> Unit,
    onOpenSourceManage: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentContext by rememberUpdatedState(context)
    val currentOnOpenSort by rememberUpdatedState(onOpenSort)
    val currentOnOpenRead by rememberUpdatedState(onOpenRead)
    val currentOnOpenRuleSub by rememberUpdatedState(onOpenRuleSub)
    val currentOnOpenFavorites by rememberUpdatedState(onOpenFavorites)
    val currentOnOpenLogin by rememberUpdatedState(onOpenLogin)
    val currentOnOpenSourceEdit by rememberUpdatedState(onOpenSourceEdit)
    val currentOnOpenSourceManage by rememberUpdatedState(onOpenSourceManage)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RssEffect.ShowMessage -> currentContext.toastOnUi(effect.message)
                is RssEffect.OpenSort -> {
                    currentOnOpenSort(effect.sourceUrl, effect.sortUrl, effect.key)
                }

                is RssEffect.OpenRead -> {
                    currentOnOpenRead(
                        effect.title,
                        effect.origin,
                        effect.link,
                        effect.openUrl,
                        effect.startPage
                    )
                }

                is RssEffect.OpenExternalUrl -> {
                    currentContext.openUrl(effect.url)
                }

                is RssEffect.OpenSourceEdit -> {
                    currentOnOpenSourceEdit(effect.sourceUrl)
                }

                is RssEffect.Login -> {
                    currentOnOpenLogin(effect.sourceUrl)
                }

                RssEffect.OpenRuleSub -> {
                    currentOnOpenRuleSub()
                }

                RssEffect.OpenFavorites -> {
                    currentOnOpenFavorites()
                }

                RssEffect.OpenSourceManage -> {
                    currentOnOpenSourceManage()
                }
            }
        }
    }

    RssScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(
    state: RssUiState,
    onIntent: (RssIntent) -> Unit,
) {
    var sourceToDeleteUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceToDelete = remember(sourceToDeleteUrl, state.items) {
        state.items.firstOrNull { it.sourceUrl == sourceToDeleteUrl }
    }

    ListScaffold(
            title = stringResource(R.string.rss),
            state = state,
            subtitle = state.group.ifEmpty { stringResource(R.string.all) },
            onBackClick = null,
            onSearchToggle = { onIntent(RssIntent.ToggleSearch(it)) },
            onSearchQueryChange = { onIntent(RssIntent.Search(it)) },
            searchPlaceholder = stringResource(R.string.search_rss_source),
            menuGroups = persistentListOf(
                AppActionGroup(
                    id = "rss_manage",
                    actions = persistentListOf(
                        AppAction("source_manage", stringResource(R.string.rss_feed_management)),
                    ),
                ),
                AppActionGroup(
                    id = "rss_groups",
                    actions = buildList {
                        add(AppAction("group:", stringResource(R.string.all)))
                        state.groups.forEach { group ->
                            add(AppAction("group:$group", group))
                        }
                    }.toImmutableList(),
                ),
            ),
            onMenuAction = { id ->
                when {
                    id == "source_manage" -> onIntent(RssIntent.OpenSourceManage)
                    id.startsWith("group:") ->
                        onIntent(RssIntent.SetGroup(id.removePrefix("group:")))
                }
            },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            LazyVerticalGrid(
                overscrollEffect = rememberAppVerticalOverscrollEffect(),
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 120.dp.coerceAtLeast(LocalBottomOverlayPadding.current)
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val ruleSubscriptionLabel = stringResource(R.string.rule_subscription)
                    val favoriteLabel = stringResource(R.string.favorite)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = ruleSubscriptionLabel
                                    role = Role.Button
                                },
                            onClick = { onIntent(RssIntent.OpenRuleSub) }
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
                                    text = ruleSubscriptionLabel,
                                    style = LegadoTheme.typography.labelMediumEmphasized
                                )
                            }
                        }
                        GlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = favoriteLabel
                                    role = Role.Button
                                },
                            onClick = { onIntent(RssIntent.OpenFavorites) }
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
                                    text = favoriteLabel,
                                    style = LegadoTheme.typography.labelMediumEmphasized
                                )
                            }
                        }
                    }
                }

                items(state.items, key = { it.sourceUrl }) { source ->
                    RssSourceGridItem(
                        modifier = Modifier.animateItem(),
                        source = source,
                        onClick = { onIntent(RssIntent.OpenSource(source)) },
                        onTop = { onIntent(RssIntent.TopSource(source)) },
                        onEdit = { onIntent(RssIntent.EditSource(source)) },
                        onDelete = { sourceToDeleteUrl = source.sourceUrl },
                        onDisable = { onIntent(RssIntent.DisableSource(source)) },
                        onLogin = { onIntent(RssIntent.Login(source)) }
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
            onIntent(RssIntent.DeleteSource(source))
            sourceToDeleteUrl = null
        },
        dismissText = stringResource(R.string.no),
        onDismiss = { sourceToDeleteUrl = null }
    )
}

/** RSS 源长按菜单的稳定 ID：声明与分派共用，避免字面量漂移。 */
private enum class RssSourceMenuAction(val id: String, @StringRes val labelRes: Int) {
    ToTop("to_top", R.string.to_top),
    Edit("edit", R.string.edit),
    Login("login", R.string.login),
    Disable("disable_source", R.string.disable_source),
    Delete("delete", R.string.delete),
}

@Composable
private fun rssSourceMenuGroups(hasLogin: Boolean): ImmutableList<AppActionGroup> {
    val actions = RssSourceMenuAction.entries.mapNotNull { action ->
        if (action == RssSourceMenuAction.Login && !hasLogin) return@mapNotNull null
        AppAction(
            id = action.id,
            label = stringResource(action.labelRes),
            icon = when (action) {
                RssSourceMenuAction.ToTop -> Icons.Default.VerticalAlignTop
                RssSourceMenuAction.Edit -> Icons.Default.Edit
                RssSourceMenuAction.Login -> Icons.AutoMirrored.Filled.Login
                RssSourceMenuAction.Disable -> Icons.Default.Close
                RssSourceMenuAction.Delete -> Icons.Default.Delete
            },
            destructive = action == RssSourceMenuAction.Delete,
        )
    }
    return persistentListOf(
        AppActionGroup(id = "rss_source_item", actions = actions.toImmutableList()),
    )
}

private fun rssSourceMenuActionFor(
    id: String,
    onTop: () -> Unit,
    onEdit: () -> Unit,
    onLogin: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
) {
    when (id) {
        RssSourceMenuAction.ToTop.id -> onTop()
        RssSourceMenuAction.Edit.id -> onEdit()
        RssSourceMenuAction.Login.id -> onLogin()
        RssSourceMenuAction.Disable.id -> onDisable()
        RssSourceMenuAction.Delete.id -> onDelete()
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
    val openLabel = stringResource(R.string.open)
    val moreMenuLabel = stringResource(R.string.more_menu)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = openLabel,
                onLongClickLabel = moreMenuLabel,
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = source.sourceName
                role = Role.Button
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            SourceIcon(
                path = source.sourceIcon,
                sourceOrigin = source.sourceUrl,
                modifier = Modifier.size(48.dp)
            )
            // 源名不再放进菜单：原生菜单不渲染分组标题文字，图下已有常驻源名，
            // 长按菜单只保留命令项；删除项走 destructive 落色。
            AppActionMenu(
                groups = rssSourceMenuGroups(hasLogin = !source.loginUrl.isNullOrBlank()),
                onAction = { id ->
                    rssSourceMenuActionFor(id, onTop, onEdit, onLogin, onDisable, onDelete)
                },
                modifier = Modifier.matchParentSize(),
                embeddedExpanded = showMenu,
                onDismissRequest = { showMenu = false },
                trigger = {},
            )
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
