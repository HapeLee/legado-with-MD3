package io.legado.app.ui.main.explore

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalBottomOverlayPadding
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun ExploreRouteScreen(
    viewModel: ExploreViewModel = koinViewModel(),
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onOpenLogin: (sourceUrl: String) -> Unit,
    onOpenEdit: (sourceUrl: String) -> Unit,
    onOpenSearch: (scopeRaw: String) -> Unit,
    embeddedPadding: PaddingValues? = null,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exploreKindUseCase: ExploreKindUiUseCase = koinInject()

    LaunchedEffect(viewModel, activity, exploreKindUseCase) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExploreEffect.ExecuteKindAction -> {
                    val infoMap = getExploreInfoMap(effect.sourceUrl)
                    exploreKindUseCase.executeAction(
                        action = effect.kind.action,
                        title = effect.kind.title,
                        sourceUrl = effect.sourceUrl,
                        infoMap = infoMap,
                        activity = activity,
                        onRefreshKinds = {
                            uiState.items.firstOrNull { it.bookSourceUrl == effect.sourceUrl }
                                ?.let { viewModel.onIntent(ExploreIntent.RefreshKinds(it)) }
                        }
                    )
                }
                is ExploreEffect.OpenEdit -> {
                    onOpenEdit(effect.sourceUrl)
                }
                is ExploreEffect.OpenSearch -> {
                    onOpenSearch(SearchScope(effect.source).toString())
                }
                is ExploreEffect.OpenLogin -> {
                    onOpenLogin(effect.sourceUrl)
                }
            }
        }
    }

    ExploreScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        onOpenExploreShow = onOpenExploreShow,
        embeddedPadding = embeddedPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    state: ExploreViewModel.ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    embeddedPadding: PaddingValues? = null,
) {
    val listItems by remember(state.items, state.expandedId, state.exploreKinds) {
        derivedStateOf { buildExploreListItems(state) }
    }
    var sourceToDeleteUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceToDelete = remember(sourceToDeleteUrl, state.items) {
        state.items.firstOrNull { it.bookSourceUrl == sourceToDeleteUrl }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val stickyHeaderSource by remember(listItems, state.items) {
        derivedStateOf {
            val firstIndex = listState.firstVisibleItemIndex
            val item = listItems.getOrNull(firstIndex)
            if (item is ExploreListItem.KindRow) {
                state.items.find { it.bookSourceUrl == item.sourceUrl }
            } else {
                null
            }
        }
    }

    val isMiuix = composeEngine == ComposeEngine.Miuix

    val sourceContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.items.isEmpty()) {
                EmptyMessage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding()
                        ),
                    messageResId = R.string.explore_empty
                )
                return@Box
            }

            FastScrollLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 120.dp.coerceAtLeast(LocalBottomOverlayPadding.current)
                )
            ) {
                items(
                    items = listItems,
                    key = { it.key }
                ) { listItem ->
                    when (listItem) {
                        is ExploreListItem.Header -> {
                            val item = listItem.source
                            val isExpanded = state.expandedId == item.bookSourceUrl
                        ExploreSourceHeader(
                            modifier = Modifier.animateItem(),
                            item = item,
                            isExpanded = isExpanded,
                            loadingKinds = if (isExpanded) state.loadingKinds else false,
                            onClick = { onIntent(ExploreIntent.ToggleExpand(item)) },
                            onTop = { onIntent(ExploreIntent.TopSource(item)) },
                            onEdit = { onIntent(ExploreIntent.OpenEdit(item)) },
                            onSearch = { onIntent(ExploreIntent.OpenSearch(item)) },
                            onLogin = { onIntent(ExploreIntent.OpenLogin(item)) },
                            onRefresh = { onIntent(ExploreIntent.RefreshKinds(item)) },
                            onDelete = { sourceToDeleteUrl = item.bookSourceUrl },
                            isMiuix = isMiuix
                        )
                        }

                        is ExploreListItem.KindRow -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listItem.rowItems.forEach { (kind, span) ->
                                    ExploreKindMultiTypeItem(
                                        kind = kind,
                                        sourceUrl = listItem.sourceUrl,
                                        onOpenUrl = { url ->
                                            onOpenExploreShow(kind.title, listItem.sourceUrl, url)
                                        },
                                        modifier = Modifier.weight(span.toFloat()),
                                        isMiuix = isMiuix,
                                        displayNameOverride = state.kindDisplayNames[kind.title],
                                        valueOverride = state.kindValues[kind.title],
                                        onValueChange = { value ->
                                            onIntent(
                                                ExploreIntent.UpdateKindValue(
                                                    listItem.sourceUrl,
                                                    kind,
                                                    value,
                                                )
                                            )
                                        },
                                        onRunAction = {
                                            onIntent(
                                                ExploreIntent.RunKindAction(listItem.sourceUrl, kind)
                                            )
                                        }
                                    )
                                }

                                val totalSpan = listItem.rowItems.sumOf { it.second }
                                if (totalSpan < 6) {
                                    Spacer(
                                        modifier = Modifier.weight((6 - totalSpan).toFloat())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TopFloatingStickyItem(
                item = stickyHeaderSource,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = paddingValues.calculateTopPadding() + 4.dp, start = 8.dp)
            ) { item ->
                TextCard(
                    text = item.bookSourceName,
                    textStyle = LegadoTheme.typography.labelMediumEmphasized,
                    cornerRadius = 12.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 8.dp,
                    modifier = Modifier.semantics {
                        contentDescription = item.bookSourceName
                        role = Role.Button
                    },
                    onClick = {
                        scope.launch {
                            val index = listItems.indexOfFirst {
                                it is ExploreListItem.Header && it.source.bookSourceUrl == item.bookSourceUrl
                            }
                            if (index >= 0) listState.animateScrollToItem(index)
                        }
                    }
                )
            }
        }
    }

    if (embeddedPadding != null) {
        sourceContent(embeddedPadding)
    } else {
        ListScaffold(
            title = stringResource(R.string.discovery),
            state = state,
            subtitle = state.selectedGroup.ifEmpty { stringResource(R.string.all) },
            onSearchQueryChange = { onIntent(ExploreIntent.Search(it)) },
            onSearchToggle = { onIntent(ExploreIntent.ToggleSearch(it)) },
            searchPlaceholder = stringResource(R.string.search),
            menuGroups = persistentListOf(
                AppActionGroup(
                    id = "explore_groups",
                    actions = buildList {
                        add(
                            AppAction(
                                "group:",
                                stringResource(R.string.all),
                                icon = Icons.Default.Group,
                            )
                        )
                        state.groups.forEach { group ->
                            add(
                                AppAction(
                                    "group:$group",
                                    group,
                                    icon = Icons.AutoMirrored.Outlined.Label,
                                )
                            )
                        }
                    }.toImmutableList(),
                ),
            ),
            onMenuAction = { id ->
                if (id.startsWith("group:")) {
                    onIntent(ExploreIntent.SetGroup(id.removePrefix("group:")))
                }
            },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            sourceContent(paddingValues)
        }
    }


    AppAlertDialog(
        data = sourceToDelete,
        onDismissRequest = { sourceToDeleteUrl = null },
        title = stringResource(R.string.sure_del),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { source ->
            onIntent(ExploreIntent.DeleteSource(source))
            sourceToDeleteUrl = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { sourceToDeleteUrl = null },
    )
}


/** 发现页源长按菜单的稳定 ID：声明与分派共用，避免字面量漂移。 */
private enum class ExploreSourceMenuAction(val id: String, @StringRes val labelRes: Int) {
    ToTop("to_top", R.string.to_top),
    Edit("edit", R.string.edit),
    Search("search", R.string.search),
    Login("login", R.string.login),
    Refresh("refresh", R.string.refresh),
    Delete("delete", R.string.delete),
}

@Composable
private fun exploreSourceMenuGroups(hasLogin: Boolean): ImmutableList<AppActionGroup> {
    val actions = ExploreSourceMenuAction.entries.mapNotNull { action ->
        if (action == ExploreSourceMenuAction.Login && !hasLogin) return@mapNotNull null
        AppAction(
            id = action.id,
            label = stringResource(action.labelRes),
            icon = when (action) {
                ExploreSourceMenuAction.ToTop -> Icons.Default.VerticalAlignTop
                ExploreSourceMenuAction.Edit -> Icons.Default.Edit
                ExploreSourceMenuAction.Search -> Icons.Default.Search
                ExploreSourceMenuAction.Login -> Icons.AutoMirrored.Filled.Login
                ExploreSourceMenuAction.Refresh -> Icons.Default.Refresh
                ExploreSourceMenuAction.Delete -> Icons.Default.Delete
            },
            destructive = action == ExploreSourceMenuAction.Delete,
        )
    }
    return persistentListOf(
        AppActionGroup(id = "explore_source_item", actions = actions.toImmutableList()),
    )
}

private fun exploreSourceMenuActionFor(
    id: String,
    onTop: () -> Unit,
    onEdit: () -> Unit,
    onSearch: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    when (id) {
        ExploreSourceMenuAction.ToTop.id -> onTop()
        ExploreSourceMenuAction.Edit.id -> onEdit()
        ExploreSourceMenuAction.Search.id -> onSearch()
        ExploreSourceMenuAction.Login.id -> onLogin()
        ExploreSourceMenuAction.Refresh.id -> onRefresh()
        ExploreSourceMenuAction.Delete.id -> onDelete()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreSourceHeader(
    modifier: Modifier = Modifier,
    item: BookSourcePart,
    isExpanded: Boolean,
    loadingKinds: Boolean,
    onClick: () -> Unit,
    onTop: () -> Unit,
    onEdit: () -> Unit,
    onSearch: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    isMiuix: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (isExpanded) 90f else 0f, label = "rotation")
    val expandActionLabel = stringResource(if (isExpanded) R.string.collapse else R.string.expand)
    val loadingLabel = stringResource(R.string.loading)
    val moreMenuLabel = stringResource(R.string.more_menu)

    val containerColor by animateColorAsState(
        targetValue = if (isExpanded)
            if (isMiuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer
        else
            if (isMiuix) MiuixTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isExpanded)
            if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
        else
            if (isMiuix) MiuixTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        cornerRadius = 12.dp,
        containerColor = containerColor,
    ) {
        ListItem(
            modifier = Modifier
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = expandActionLabel,
                    onLongClickLabel = moreMenuLabel,
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = item.bookSourceName
                    role = Role.Button
                    if (loadingKinds) {
                        stateDescription = loadingLabel
                    }
                }
                .fillMaxWidth(),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            trailingContent = {
                AnimatedContent(
                    targetState = loadingKinds,
                    label = "LoadingSwitch"
                ) { loading ->
                    if (loading) {
                        AppContainedLoadingIndicator(
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier
                                .rotate(rotation)
                                .size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 源名不再放进菜单：原生菜单不渲染分组标题文字，条目正文已有源名，
                // 长按菜单只保留命令项；删除项走 destructive 落色。锚点取这层 Box（尾部区域）。
                Box {
                    AnimatedContent(
                        targetState = loadingKinds,
                        label = "LoadingSwitch"
                    ) { loading ->
                        if (loading) {
                            AppContainedLoadingIndicator(
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier
                                    .rotate(rotation)
                                    .size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AppActionMenu(
                        groups = exploreSourceMenuGroups(hasLogin = item.hasLoginUrl),
                        onAction = { id ->
                            exploreSourceMenuActionFor(
                                id, onTop, onEdit, onSearch, onLogin, onRefresh, onDelete,
                            )
                        },
                        modifier = Modifier.matchParentSize(),
                        embeddedExpanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        trigger = {},
                    )
                }
            },
        ) {
            AppText(
                text = item.bookSourceName,
                style = LegadoTheme.typography.titleMedium,
                color = contentColor
            )
        }
    }
}
