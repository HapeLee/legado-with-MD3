package io.legado.app.ui.main.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.widget.components.explore.ExploreKindUiUseCase
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.startActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel = koinViewModel(),
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sourceToDeleteUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceToDelete = remember(sourceToDeleteUrl, uiState.items) {
        uiState.items.firstOrNull { it.bookSourceUrl == sourceToDeleteUrl }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val exploreKindUseCase: ExploreKindUiUseCase = koinInject()

    LaunchedEffect(viewModel, activity, exploreKindUseCase) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExploreEffect.ExecuteKindAction -> {
                    exploreKindUseCase.executeAction(
                        action = effect.kind.action,
                        title = effect.kind.title,
                        sourceUrl = effect.sourceUrl,
                        activity = activity,
                        onRefreshKinds = { viewModel.refreshExploreKinds(effect.sourceUrl) }
                    )
                }
            }
        }
    }

    val expandedHeader = remember(uiState.expandedId, uiState.listItems) {
        val expandedId = uiState.expandedId ?: return@remember null
        val headerIndex = uiState.listItems.indexOfFirst {
            it is ExploreListItem.Header && it.source.bookSourceUrl == expandedId
        }
        val headerItem = uiState.listItems.getOrNull(headerIndex) as? ExploreListItem.Header
        if (headerItem != null) {
            ExpandedExploreHeader(
                source = headerItem.source,
                headerIndex = headerIndex,
                contentRowCount = uiState.listItems.count {
                    it is ExploreListItem.KindRow && it.sourceUrl == expandedId
                }
            )
        } else {
            null
        }
    }

    LaunchedEffect(expandedHeader?.headerIndex) {
        expandedHeader?.let { listState.animateScrollToItem(it.headerIndex) }
    }

    val stickyHeaderSource by remember(expandedHeader) {
        derivedStateOf {
            val header = expandedHeader ?: return@derivedStateOf null
            val lastContentIndex = header.headerIndex + header.contentRowCount
            val firstVisible = listState.firstVisibleItemIndex

            if (firstVisible in (header.headerIndex + 1)..lastContentIndex) {
                header.source
            } else {
                null
            }
        }
    }

    val composeEngine = ThemeResolver.isMiuixEngine(composeEngine)

    ListScaffold(
        title = stringResource(R.string.discovery),
        state = uiState,
        subtitle = uiState.selectedGroup.ifEmpty { stringResource(R.string.all) },
        onSearchQueryChange = { viewModel.search(it) },
        onSearchToggle = { viewModel.toggleSearchVisible(it) },
        searchPlaceholder = stringResource(R.string.search),
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.Default.Group) },
                text = stringResource(R.string.all),
                onClick = { viewModel.setGroup(""); dismiss() }
            )
            uiState.groups.forEach { group ->
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Outlined.Label) },
                    text = group,
                    onClick = { viewModel.setGroup(group); dismiss() }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.items.isEmpty()) {
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
                    bottom = 120.dp
                )
            ) {
                items(
                    items = uiState.listItems,
                    key = { it.key },
                    contentType = {
                        when (it) {
                            is ExploreListItem.Header -> "source-header"
                            is ExploreListItem.KindRow -> "kind-row"
                        }
                    }
                ) { listItem ->
                    when (listItem) {
                        is ExploreListItem.Header -> {
                            val item = listItem.source
                            val isExpanded = uiState.expandedId == item.bookSourceUrl
                        ExploreSourceHeader(
                            modifier = Modifier.animateItem(),
                            item = item,
                            isExpanded = isExpanded,
                            loadingKinds = if (isExpanded) uiState.loadingKinds else false,
                            onClick = { viewModel.toggleExpand(item) },
                            onTop = { viewModel.topSource(item) },
                            onEdit = {
                                context.startActivity<BookSourceEditActivity> {
                                    putExtra("sourceUrl", item.bookSourceUrl)
                                }
                            },
                            onSearch = {
                                context.startActivity<SearchActivity> {
                                    putExtra("searchScope", SearchScope(item).toString())
                                }
                            },
                            onLogin = {
                                context.startActivity<SourceLoginActivity> {
                                    putExtra("type", "bookSource")
                                    putExtra("key", item.bookSourceUrl)
                                }
                            },
                            onRefresh = { viewModel.refreshExploreKinds(item) },
                            onDelete = { sourceToDeleteUrl = item.bookSourceUrl },
                            isMiuix = composeEngine
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
                                        isMiuix = composeEngine,
                                        displayNameOverride = uiState.kindDisplayNames[kind.title],
                                        valueOverride = uiState.kindValues[kind.title],
                                        onValueChange = { value ->
                                            viewModel.updateKindValue(listItem.sourceUrl, kind, value)
                                        },
                                        onRunAction = {
                                            viewModel.requestKindAction(listItem.sourceUrl, kind)
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
                    onClick = {
                        scope.launch {
                            val index =
                                uiState.items.indexOfFirst { it.bookSourceUrl == item.bookSourceUrl }
                            if (index >= 0) listState.animateScrollToItem(index)
                        }
                    }
                )
            }
        }
    }


    AppAlertDialog(
        data = sourceToDelete,
        onDismissRequest = { sourceToDeleteUrl = null },
        title = stringResource(R.string.sure_del),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { source ->
            viewModel.deleteSource(source)
            sourceToDeleteUrl = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { sourceToDeleteUrl = null },
    )
}

private data class ExpandedExploreHeader(
    val source: BookSourcePart,
    val headerIndex: Int,
    val contentRowCount: Int
)

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
        containerColor = containerColor
    ) {
        ListItem(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .fillMaxWidth(),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            headlineContent = {
                AppText(
                    text = item.bookSourceName,
                    style = LegadoTheme.typography.titleMedium,
                    color = contentColor
                )
            },
            trailingContent = {
                if (loadingKinds) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
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
                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    PillHeaderDivider(title = item.bookSourceName)
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.VerticalAlignTop) },
                        text = stringResource(R.string.to_top),
                        onClick = { onTop(); showMenu = false }
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                        text = stringResource(R.string.edit),
                        onClick = { onEdit(); showMenu = false }
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Search) },
                        text = stringResource(R.string.search),
                        onClick = { onSearch(); showMenu = false }
                    )
                    if (item.hasLoginUrl) {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                            text = stringResource(R.string.login),
                            onClick = { onLogin(); showMenu = false }
                        )
                    }
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Refresh) },
                        text = stringResource(R.string.refresh),
                        onClick = { onRefresh(); showMenu = false }
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = {
                            MenuItemIcon(
                                Icons.Default.Delete,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        text = stringResource(R.string.delete),
                        color = LegadoTheme.colorScheme.error,
                        onClick = { onDelete(); showMenu = false }
                    )
                }
            }
        )
    }
}


