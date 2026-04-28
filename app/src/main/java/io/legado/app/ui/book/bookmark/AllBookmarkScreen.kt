package io.legado.app.ui.book.bookmark

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.bookmark.BookmarkEditSheet
import io.legado.app.ui.widget.components.bookmark.BookmarkItem
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun AllBookmarkScreen(
    viewModel: AllBookmarkViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    val contentState = when {
        uiState.isLoading -> "LOADING"
        uiState.bookmarks.isEmpty() -> "EMPTY"
        else -> "CONTENT"
    }
    val searchText = uiState.searchQuery
    val collapsedGroups = uiState.collapsedGroups
    val bookmarksGrouped = uiState.bookmarks
    val bookmarkGroups = remember(bookmarksGrouped) { bookmarksGrouped.entries.toList() }
    val allKeys = bookmarksGrouped.keys
    val isAllCollapsed =
        allKeys.isNotEmpty() && allKeys.all { collapsedGroups.contains(it.toString()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var pendingExportIsMd by remember { mutableStateOf(false) }
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val stickyGroup by remember(bookmarkGroups, collapsedGroups, listState) {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleGroup = bookmarkGroups.getOrNull(firstVisibleIndex)
                ?: return@derivedStateOf null
            val isCollapsed = collapsedGroups.contains(firstVisibleGroup.key.toString())
            val shouldStick = firstVisibleIndex > 0 || listState.firstVisibleItemScrollOffset > 24
            if (!isCollapsed && shouldStick) {
                firstVisibleGroup.key
            } else {
                null
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportBookmark(it, pendingExportIsMd)
            Toast.makeText(context, "开始导出...", Toast.LENGTH_SHORT).show()
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = "所有书签",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarNavigationButton(onClick = onBack)
                    },
                    actions = {
                        if (bookmarksGrouped.isNotEmpty()) {
                            TopBarActionButton(
                                onClick = { viewModel.toggleAllCollapse(allKeys) },
                                imageVector = if (isAllCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                contentDescription = null
                            )
                        }
                        TopBarActionButton(
                            onClick = {
                                showSearch = !showSearch
                                if (!showSearch) {
                                    viewModel.onSearchQueryChanged("")
                                }
                            },
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                        TopBarActionButton(
                            onClick = { showMenu = true },
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu"
                        )
                        RoundDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            RoundDropdownMenuItem(
                                text = "导出 JSON",
                                onClick = {
                                    showMenu = false
                                    pendingExportIsMd = false
                                    exportLauncher.launch(null)
                                }
                            )
                            RoundDropdownMenuItem(
                                text = "导出 Markdown",
                                onClick = {
                                    showMenu = false
                                    pendingExportIsMd = true
                                    exportLauncher.launch(null)
                                }
                            )
                        }
                    }
                )

                AnimatedVisibility(
                    modifier = Modifier.adaptiveHorizontalPadding(),
                    visible = showSearch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchBar(
                        query = searchText,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = "搜索...",
                        scrollState = listState,
                        scope = scope
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = contentState,
                label = "bookmarkTransition"
            ) { state ->
                when (state) {
                    "LOADING" -> {
                        EmptyMessage(
                            message = "加载中...",
                            isLoading = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = paddingValues.calculateTopPadding(),
                                    bottom = 120.dp
                                )
                        )
                    }

                    "EMPTY" -> {
                        EmptyMessage(
                            message = "没有书签！",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = paddingValues.calculateTopPadding(),
                                    bottom = 120.dp
                                )
                        )
                    }

                    "CONTENT" -> {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            FastScrollLazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = adaptiveContentPadding(
                                    top = paddingValues.calculateTopPadding(),
                                    bottom = 120.dp
                                )
                            ) {
                                items(
                                    items = bookmarkGroups,
                                    key = { it.key.toString() }
                                ) { (headerKey, bookmarks) ->
                                    val isCollapsed = collapsedGroups.contains(headerKey.toString())

                                    GlassCard(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        cornerRadius = 12.dp,
                                        containerColor = LegadoTheme.colorScheme.surfaceContainer
                                    ) {
                                        BookmarkGroupHeaderContent(
                                            title = headerKey.bookName,
                                            subtitle = headerKey.bookAuthor,
                                            isCollapsed = isCollapsed,
                                            onToggle = { viewModel.toggleGroupCollapse(headerKey) },
                                            isMiuix = isMiuix
                                        )

                                        AnimatedVisibility(
                                            visible = !isCollapsed && bookmarks.isNotEmpty()
                                        ) {
                                            Column() {
                                                HorizontalDivider(
                                                    color = LegadoTheme.colorScheme.surface
                                                )
                                                bookmarks.forEach { bookmarkUi ->
                                                    BookmarkItem(
                                                        bookmark = bookmarkUi.rawBookmark,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        isDur = false,
                                                        onClick = {
                                                            editingBookmark = bookmarkUi.rawBookmark
                                                            showBottomSheet = true
                                                        },
                                                        onLongClick = {
                                                            editingBookmark = bookmarkUi.rawBookmark
                                                            showBottomSheet = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            TopFloatingStickyItem(
                                item = stickyGroup,
                                modifier = Modifier
                                    .padding(
                                        top = paddingValues.calculateTopPadding() + 4.dp,
                                        start = 8.dp
                                    )
                            ) { group ->
                                TextCard(
                                    text = group.bookName,
                                    textStyle = LegadoTheme.typography.labelLarge,
                                    cornerRadius = 8.dp,
                                    horizontalPadding = 8.dp,
                                    verticalPadding = 6.dp,
                                    onClick = {
                                        scope.launch {
                                            val index =
                                                bookmarkGroups.indexOfFirst { it.key == group }
                                            if (index >= 0) {
                                                listState.animateScrollToItem(index)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        BookmarkEditSheet(
            show = showBottomSheet && editingBookmark != null,
            bookmark = editingBookmark ?: Bookmark(),
            onDismiss = {
                showBottomSheet = false
                editingBookmark = null
            },
            onSave = { updatedBookmark ->
                viewModel.updateBookmark(updatedBookmark)
                showBottomSheet = false
            },
            onDelete = { bookmarkToDelete ->
                viewModel.deleteBookmark(bookmarkToDelete)
                showBottomSheet = false
            }
        )
    }
}

@Composable
private fun BookmarkGroupHeaderContent(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    isMiuix: Boolean
) {

    val contentColor by animateColorAsState(
        if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            supportingColor = LegadoTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = LegadoTheme.colorScheme.onSurfaceVariant
        ),
        headlineContent = {
            AppText(
                text = title,
                style = LegadoTheme.typography.titleMedium,
                color = contentColor
            )
        },
        supportingContent = {
            subtitle?.let {
                AppText(
                    text = it,
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
