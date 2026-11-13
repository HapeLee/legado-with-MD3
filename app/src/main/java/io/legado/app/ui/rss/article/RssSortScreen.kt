package io.legado.app.ui.rss.article

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.ui.rss.read.RedirectPolicy
import io.legado.app.ui.rss.read.title
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPaddingTab
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallToggleButton
import io.legado.app.ui.widget.components.button.series.ToggleStyle
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.theme.isMiuixHyperOs4Enabled
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.variable.VariableEditorSheet
import io.legado.app.ui.widget.components.variable.VariableEditorUiState
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.topbar.TopBarActionMenu
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** 重定向策略子项的稳定 ID 前缀：`redirect_` + `RedirectPolicy.name`。 */
private const val REDIRECT_PREFIX = "redirect_"

/** 底部分组切换菜单的稳定 ID：分组标题可能重名，用序号定位。 */
private const val GROUP_SWITCHER_MENU = "rss_sort_group_switcher"
private const val GROUP_SWITCHER_PREFIX = "rss_sort_group:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSortScreen(
    title: String,
    sortList: List<Pair<String, String>>,
    preferredSortUrl: String?,
    searchKey: String?,
    hasSearch: Boolean,
    hasLogin: Boolean,
    redirectPolicy: RedirectPolicy,
    showReadRecordSheet: Boolean,
    readRecords: List<RssReadRecord>,
    sourceVariableSheet: VariableEditorUiState?,
    shouldShowExpandButton: Boolean,
    onBackClick: () -> Unit,
    onSearch: (String) -> Unit,
    onLogin: () -> Unit,
    onRefreshSort: () -> Unit,
    onSetSourceVariable: () -> Unit,
    onDismissSourceVariable: () -> Unit,
    onSourceVariableChange: (String) -> Unit,
    onSaveSourceVariable: () -> Unit,
    onEditSource: () -> Unit,
    onSwitchLayout: () -> Unit,
    onReadRecord: () -> Unit,
    onDismissReadRecord: () -> Unit,
    onClearReadRecord: () -> Unit,
    onOpenReadRecord: (RssReadRecord) -> Unit,
    onClearArticles: () -> Unit,
    onRedirectPolicyChanged: (RedirectPolicy) -> Unit,
    pagerContent: @Composable (index: Int, item: Pair<String, String>, paddingValues: PaddingValues) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    val initialPage = remember(sortList, preferredSortUrl) {
        val index = sortList.indexOfFirst { it.second == preferredSortUrl }
        if (index >= 0) index else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) {
        sortList.size.coerceAtLeast(1)
    }
    val selectedTabIndex = pagerState.currentPage.coerceIn(0, (sortList.size - 1).coerceAtLeast(0))
    val tabTitles = sortList.map { it.first }

    val alwaysShowSearch = isMiuixHyperOs4Enabled()
    var showSearchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember(searchKey) { mutableStateOf(searchKey.orEmpty()) }
    var showGroupMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = showSearchSheet || showGroupMenu) {
        showSearchSheet = false
        showGroupMenu = false
    }

    fun submitSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        showSearchSheet = false
        onSearch(normalized)
    }

    LaunchedEffect(sortList.size) {
        if (sortList.isEmpty()) return@LaunchedEffect
        val safeIndex = pagerState.currentPage.coerceIn(0, sortList.lastIndex)
        if (safeIndex != pagerState.currentPage) {
            pagerState.scrollToPage(safeIndex)
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick, imageVector = AppIcons.Back)
                },
                actions = {
                    if (hasSearch && !alwaysShowSearch) {
                        TopBarActionButton(
                            onClick = { showSearchSheet = true },
                            imageVector = AppIcons.Search,
                            contentDescription = stringResource(R.string.search)
                        )
                    }
                    TopBarActionMenu(
                        groups = persistentListOf(
                            AppActionGroup(
                                id = "rss_sort_menu",
                                actions = buildList {
                                    if (hasLogin) {
                                        add(
                                            AppAction(
                                                id = "login",
                                                label = stringResource(R.string.login),
                                                icon = Icons.AutoMirrored.Filled.Login,
                                            )
                                        )
                                    }
                                    add(
                                        AppAction(
                                            id = "refresh_sort",
                                            label = stringResource(R.string.refresh_sort),
                                            icon = Icons.Default.Refresh,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "set_source_variable",
                                            label = stringResource(R.string.set_source_variable),
                                            icon = Icons.Default.Dataset,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "edit_source",
                                            label = stringResource(R.string.edit_source),
                                            icon = Icons.Default.Edit,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "switch_layout",
                                            label = stringResource(R.string.switchLayout),
                                            icon = Icons.Default.Style,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "read_record",
                                            label = stringResource(R.string.read_record),
                                            icon = Icons.Default.History,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "clear_articles",
                                            label = stringResource(R.string.clear),
                                            icon = Icons.Default.CleaningServices,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "redirect_policy",
                                            label = stringResource(R.string.redirect_policy),
                                            children = RedirectPolicy.entries.map { policy ->
                                                AppAction(
                                                    id = "redirect_${policy.name}",
                                                    label = policy.title(),
                                                    checked = policy == redirectPolicy,
                                                )
                                            }.toImmutableList(),
                                        )
                                    )
                                }.toImmutableList(),
                            ),
                        ),
                        onAction = { id ->
                            when {
                                id == "login" -> onLogin()
                                id == "refresh_sort" -> onRefreshSort()
                                id == "set_source_variable" -> onSetSourceVariable()
                                id == "edit_source" -> onEditSource()
                                id == "switch_layout" -> onSwitchLayout()
                                id == "read_record" -> onReadRecord()
                                id == "clear_articles" -> onClearArticles()
                                id.startsWith(REDIRECT_PREFIX) -> RedirectPolicy.entries
                                    .firstOrNull { id == "$REDIRECT_PREFIX${it.name}" }
                                    ?.let(onRedirectPolicyChanged)
                            }
                        },
                    )
                },
                bottomContent = {
                    if (hasSearch && alwaysShowSearch) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = ::submitSearch,
                            modifier = Modifier.adaptiveHorizontalPadding(),
                            autoFocus = false,
                        )
                    }
                    if (sortList.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .adaptiveHorizontalPaddingTab(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppTabRow(
                                tabTitles = tabTitles,
                                selectedTabIndex = selectedTabIndex,
                                onTabSelected = { index ->
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (shouldShowExpandButton) {
                                Box {
                                    SmallToggleButton(
                                        checked = showGroupMenu,
                                        onCheckedChange = { showGroupMenu = it },
                                        style = ToggleStyle.Outlined,
                                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = stringResource(R.string.group_manage)
                                    )
                                    AppActionMenu(
                                        groups = persistentListOf(
                                            AppActionGroup(
                                                id = GROUP_SWITCHER_MENU,
                                                actions = sortList.mapIndexed { index, group ->
                                                    AppAction(
                                                        id = "$GROUP_SWITCHER_PREFIX$index",
                                                        label = group.first,
                                                        selected = selectedTabIndex == index,
                                                    )
                                                }.toImmutableList(),
                                            ),
                                        ),
                                        onAction = { id ->
                                            id.removePrefix(GROUP_SWITCHER_PREFIX)
                                                .toIntOrNull()
                                                ?.takeIf { index -> index in sortList.indices }
                                                ?.let { index ->
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(index)
                                                    }
                                                }
                                        },
                                        modifier = Modifier.matchParentSize(),
                                        embeddedExpanded = showGroupMenu,
                                        onDismissRequest = { showGroupMenu = false },
                                        trigger = {},
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (sortList.isEmpty()) {
            EmptyMessage(
                message = stringResource(R.string.empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp)
            )
        } else {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = 0.dp),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = sortList.getOrNull(page) ?: return@HorizontalPager
                pagerContent(page, item, paddingValues)
            }
        }
    }

    RssReadRecordSheet(
        show = showReadRecordSheet,
        records = readRecords,
        onDismissRequest = onDismissReadRecord,
        onClear = onClearReadRecord,
        onOpen = onOpenReadRecord
    )

    VariableEditorSheet(
        state = sourceVariableSheet,
        onValueChange = onSourceVariableChange,
        onDismissRequest = onDismissSourceVariable,
        onSave = onSaveSourceVariable
    )

    RssSearchSheet(
        show = showSearchSheet && !alwaysShowSearch,
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        onSearch = ::submitSearch,
        onDismissRequest = { showSearchSheet = false }
    )
}
@Composable
private fun RssSearchSheet(
    show: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.search),
        endAction = {
            MediumTonalButton(
                onClick = { onSearch(query) },
                icon = AppIcons.Search,
                contentDescription = stringResource(R.string.search)
            )
        }
    ) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            placeholder = stringResource(R.string.search_placeholder),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RssReadRecordSheet(
    show: Boolean,
    records: List<RssReadRecord>,
    onDismissRequest: () -> Unit,
    onClear: () -> Unit,
    onOpen: (RssReadRecord) -> Unit
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_record),
        endAction = {
            MediumTonalButton(
                onClick = onClear,
                icon = Icons.Default.CleaningServices,
                contentDescription = stringResource(R.string.clear)
            )
        }
    ) {
        if (records.isEmpty()) {
            EmptyMessage(
                message = stringResource(R.string.empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(records, key = { it.record }) { record ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = { onOpen(record) },
                        containerColor = LegadoTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            AppText(
                                text = record.title.orEmpty(),
                                style = LegadoTheme.typography.titleSmall,
                                color = LegadoTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            AppText(
                                text = record.record,
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
