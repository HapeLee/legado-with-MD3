package io.legado.app.ui.rss.article

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.rss.read.RedirectPolicy
import io.legado.app.ui.rss.read.title
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPaddingTab
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.button.series.SmallToggleButton
import io.legado.app.ui.widget.components.button.series.ToggleStyle
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.launch

@Stable
data class RssSourceVariableSheetState(
    val title: String,
    val variable: String,
    val comment: String
)

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
    sourceVariableSheet: RssSourceVariableSheetState?,
    onBackClick: () -> Unit,
    onSearch: (String) -> Unit,
    onLogin: () -> Unit,
    onRefreshSort: () -> Unit,
    onSetSourceVariable: () -> Unit,
    onDismissSourceVariable: () -> Unit,
    onSaveSourceVariable: (String) -> Unit,
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

    var showMainMenu by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember(searchKey) { mutableStateOf(searchKey.orEmpty()) }
    var showRedirectMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = showMainMenu || showSearchSheet || showRedirectMenu || showGroupMenu) {
        showMainMenu = false
        showSearchSheet = false
        showRedirectMenu = false
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
                    if (hasSearch) {
                        TopBarActionButton(
                            onClick = { showSearchSheet = true },
                            imageVector = AppIcons.Search,
                            contentDescription = stringResource(R.string.search)
                        )
                    }
                    TopBarActionButton(
                        onClick = { showMainMenu = true },
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "Menu"
                    )
                    RoundDropdownMenu(
                        expanded = showMainMenu,
                        onDismissRequest = { showMainMenu = false }
                    ) { dismiss ->
                        if (hasLogin) {
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.login),
                                leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                                onClick = {
                                    dismiss()
                                    onLogin()
                                }
                            )
                        }
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.refresh_sort),
                            leadingIcon = { MenuItemIcon(Icons.Default.Refresh) },
                            onClick = {
                                dismiss()
                                onRefreshSort()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.set_source_variable),
                            leadingIcon = { MenuItemIcon(Icons.Default.Dataset) },
                            onClick = {
                                dismiss()
                                onSetSourceVariable()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.edit_source),
                            leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                            onClick = {
                                dismiss()
                                onEditSource()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.switchLayout),
                            leadingIcon = { MenuItemIcon(Icons.Default.Style) },
                            onClick = {
                                dismiss()
                                onSwitchLayout()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.read_record),
                            leadingIcon = { MenuItemIcon(Icons.Default.History) },
                            onClick = {
                                dismiss()
                                onReadRecord()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.clear),
                            leadingIcon = { MenuItemIcon(Icons.Default.CleaningServices) },
                            onClick = {
                                dismiss()
                                onClearArticles()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.redirect_policy),
                            onClick = { showRedirectMenu = true }
                        )
                    }

                    RoundDropdownMenu(
                        expanded = showRedirectMenu,
                        onDismissRequest = { showRedirectMenu = false },
                        modifier = Modifier.width(280.dp)
                    ) { dismiss ->
                        RedirectPolicy.entries.forEach { policy ->
                            RoundDropdownMenuItem(
                                text = policy.title(),
                                isSelected = policy == redirectPolicy,
                                onClick = {
                                    dismiss()
                                    showMainMenu = false
                                    onRedirectPolicyChanged(policy)
                                }
                            )
                        }
                    }
                },
                bottomContent = {
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

                            if (BookshelfConfig.shouldShowExpandButton) {
                                Box {
                                    SmallToggleButton(
                                        checked = showGroupMenu,
                                        onCheckedChange = { showGroupMenu = it },
                                        style = ToggleStyle.Outlined,
                                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = stringResource(R.string.group_manage)
                                    )
                                    RoundDropdownMenu(
                                        expanded = showGroupMenu,
                                        onDismissRequest = { showGroupMenu = false }
                                    ) { dismiss ->
                                        sortList.forEachIndexed { index, group ->
                                            RoundDropdownMenuItem(
                                                text = group.first,
                                                onClick = {
                                                    scope.launch { pagerState.animateScrollToPage(index) }
                                                    dismiss()
                                                },
                                                trailingIcon = {
                                                    if (selectedTabIndex == index) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
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

    RssSourceVariableSheet(
        state = sourceVariableSheet,
        onDismissRequest = onDismissSourceVariable,
        onSave = onSaveSourceVariable
    )

    RssSearchSheet(
        show = showSearchSheet,
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
            SmallPlainButton(
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
            SmallPlainButton(
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

@Composable
private fun RssSourceVariableSheet(
    state: RssSourceVariableSheetState?,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit
) {
    var variable by remember(state) { mutableStateOf(state?.variable.orEmpty()) }

    AppModalBottomSheet(
        data = state,
        onDismissRequest = onDismissRequest,
        title = state?.title,
        endAction = {
            MediumPlainButton(
                onClick = { onSave(variable) },
                icon = Icons.Default.Done,
                contentDescription = stringResource(R.string.ok)
            )
        }
    ) { sheetState ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            AppText(
                text = sheetState.comment,
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
            AppTextField(
                value = variable,
                onValueChange = { variable = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8
            )
        }
    }
}
