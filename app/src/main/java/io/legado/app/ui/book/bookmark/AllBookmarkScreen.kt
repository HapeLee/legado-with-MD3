package io.legado.app.ui.book.bookmark

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.SearchBarSection
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
    val searchText by viewModel.searchQuery.collectAsState()
    val uiState by viewModel.bookmarksState.collectAsState()
    val collapsedGroups by viewModel.collapsedGroups.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var pendingExportIsMd by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val bookmarksGrouped = (uiState as? BookmarkUiState.Success)?.bookmarks ?: emptyMap()
    val allKeys = bookmarksGrouped.keys
    val isAllCollapsed = allKeys.isNotEmpty() && allKeys.all { collapsedGroups.contains(it.toString()) }

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
                MediumFlexibleTopAppBar(
                    title = {
                        Text(
                            text = "所有书签"
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
                        placeholder = "搜索..."
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
                targetState = uiState,
                label = "bookmarkTransition"
            ) { state ->
                when (state) {
                    BookmarkUiState.Loading -> {
                        EmptyMessageView(
                            message = "加载中...",
                            isLoading = true,
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    }

                    is BookmarkUiState.Success -> {
                        if (state.bookmarks.isEmpty()) {
                            EmptyMessageView(
                                message = "没有书签！",
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                        } else {
                            FastScrollLazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                state.bookmarks.forEach { (headerKey, bookmarks) ->

                                    val isCollapsed = collapsedGroups.contains(headerKey.toString())

                                    stickyHeader(key = "${Scroller.STICKY_HEADER_KEY_PREFIX}${headerKey}") {
                                        BookAuthorHeader(
                                            bookTitle = headerKey.bookName,
                                            bookAuthor = headerKey.bookAuthor,
                                            isCollapsed = isCollapsed,
                                            onToggle = { viewModel.toggleGroupCollapse(headerKey) }
                                        )
                                    }

                                    item(key = "content_${headerKey}") {
                                        AnimatedVisibility(
                                            visible = !isCollapsed,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column(
                                                modifier = Modifier.animateContentSize()
                                            ) {
                                                bookmarks.forEach { bookmark ->
                                                    BookmarkItem(
                                                        bookmark = bookmark,
                                                        modifier = Modifier
                                                            .animateItem()
                                                            .fillMaxWidth(),
                                                        onClick = {
                                                            editingBookmark = bookmark
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

                    is BookmarkUiState.Error -> {
                        EmptyMessageView(
                            message = state.throwable.localizedMessage ?: "发生错误",
                        )
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

@Composable
fun BookAuthorHeader(
    bookTitle: String,
    bookAuthor: String,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = bookAuthor,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val rotation by animateFloatAsState(
                targetValue = if (isCollapsed) 0f else 180f,
                label = "arrowRotation"
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isCollapsed) "展开书签" else "折叠书签",
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun BookmarkItem(
    bookmark: Bookmark,
    modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = bookmark.chapterName,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        if (bookmark.bookText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = bookmark.bookText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (bookmark.content.isNotEmpty()) {
            Text(
                text = bookmark.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkEditSheet(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onSave: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var bookText by remember { mutableStateOf(bookmark.bookText) }
    var content by remember { mutableStateOf(bookmark.content) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = bookmark.chapterName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = bookText,
                onValueChange = { bookText = it },
                label = { Text("原文") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 10
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("摘要/笔记") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("删除")
                }

                Button(
                    onClick = {
                        val newBookmark = bookmark.apply {
                            this.bookText = bookText
                            this.content = content
                        }
                        onSave(newBookmark)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("你确定要删除这条书签吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(bookmark)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}
