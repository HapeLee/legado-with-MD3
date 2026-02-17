package io.legado.app.ui.book.bookmark

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.widget.CollapsibleHeader
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.bookmark.BookmarkEditSheet
import io.legado.app.ui.widget.components.bookmark.BookmarkItem
import io.legado.app.ui.widget.components.button.SmallTopBarButton
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.lazylist.Scroller
import org.koin.androidx.compose.koinViewModel

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportBookmark(it, pendingExportIsMd)
            Toast.makeText(context, "开始导出...", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = {
                        Text(
                            text = "所有书签"
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        SmallTopBarButton(onClick = onBack)
                    },
                    actions = {
                        if (bookmarksGrouped.isNotEmpty()) {
                            IconButton(onClick = { viewModel.toggleAllCollapse(allKeys) }) {
                                Icon(
                                    imageVector = if (isAllCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                    contentDescription = null
                                )
                            }
                        }
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) {
                                viewModel.onSearchQueryChanged("")
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("导出 JSON") },
                                onClick = {
                                    showMenu = false
                                    pendingExportIsMd = false
                                    exportLauncher.launch(null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出 Markdown") },
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
                    visible = showSearch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchBarSection(
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
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = contentState,
                label = "bookmarkTransition"
            ) { state ->
                when (state) {
                    "LOADING" -> {
                        EmptyMessageView(
                            message = "加载中...",
                            isLoading = true,
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    }

                    "EMPTY" -> {

                        EmptyMessageView(
                            message = "没有书签！",
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    }

                    "CONTENT" -> {
                        FastScrollLazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            uiState.bookmarks.forEach { (headerKey, bookmarks) ->
                                val isCollapsed = collapsedGroups.contains(headerKey.toString())

                                stickyHeader(key = "${Scroller.STICKY_HEADER_KEY_PREFIX}${headerKey}") {
                                    CollapsibleHeader(
                                        modifier = Modifier.animateItem(),
                                        title = headerKey.bookName,
                                        subtitle = headerKey.bookAuthor,
                                        isCollapsed = isCollapsed,
                                        onToggle = { viewModel.toggleGroupCollapse(headerKey) }
                                    )
                                }

                                if (!isCollapsed) {
                                    items(
                                        items = bookmarks,
                                        key = { it.id }
                                    ) { bookmarkUi ->
                                        BookmarkItem(
                                            bookmark = bookmarkUi.rawBookmark,
                                            modifier = Modifier
                                                .animateItem()
                                                .fillMaxWidth(),
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
            }
        }

        if (showBottomSheet && editingBookmark != null) {
            BookmarkEditSheet(
                bookmark = editingBookmark!!,
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
}