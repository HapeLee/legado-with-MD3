package io.legado.app.ui.book.explore

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.model.BookShelfState
import io.legado.app.ui.widget.components.AnimatedTextLine
import io.legado.app.ui.widget.components.Cover
import io.legado.app.ui.widget.components.EmptyMessageView
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreShowScreen(
    title: String,
    intent: Intent,
    onBack: () -> Unit,
    onBookClick: (SearchBook) -> Unit,
    viewModel: ExploreShowViewModel = koinViewModel()
) {

    LaunchedEffect(Unit) {
        viewModel.initData(intent)
    }

    val books by viewModel.uiBooks.collectAsState()
    val kinds by viewModel.kinds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.errorMsg.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val selectedTitle by viewModel.selectedKindTitle.collectAsState()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showKindSheet by remember { mutableStateOf(false) }
    val layoutState by viewModel.layoutState.collectAsState()
    val isGridMode = layoutState == 1
    if (showKindSheet) {
        val scrollState = rememberScrollState() // 记住滚动状态

        ModalBottomSheet(
            onDismissRequest = { showKindSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                Text(
                    "选择分类",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    kinds.forEach { kind ->
                        KindListItem(
                            kind = kind,
                            currentTitle = selectedTitle ?: title,
                            onClick = {
                                showKindSheet = false
                                viewModel.switchExploreUrl(kind)
                            }
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ExploreTopBar(
                title = selectedTitle ?: title,
                filterState = filterState,
                onBack = onBack,
                onFilterSelect = viewModel::setFilterState,
                onSelectKindClick = { showKindSheet = true },
                onToggleGridMode = { viewModel.setLayout() },
                isGridMode = isGridMode,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!isLoading && books.isEmpty() && errorMsg == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyMessageView(message = "暂无书籍")
                }
            } else {
                Crossfade(
                    targetState = isGridMode,
                    animationSpec = tween(250),
                    label = "LayoutCrossfade"
                ) { isGrid ->
                    if (isGrid) {
                        LazyVerticalGrid(
                            modifier = Modifier.fillMaxSize(),
                            columns = GridCells.Adaptive(minSize = 90.dp),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = 12.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = books,
                                key = { it.bookUrl }
                            ) { book ->
                                val shelfState = viewModel.getCurrentBookShelfState(book)
                                ExploreBookGridItem(
                                    book = book,
                                    shelfState = shelfState,
                                    onClick = { onBookClick(book) },
                                    modifier = Modifier.animateItem()
                                )
                            }

                            item {
                                LoadMoreFooter(
                                    isLoading = isLoading,
                                    errorMsg = errorMsg,
                                    onRetry = viewModel::loadMore
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(
                                items = books,
                                key = { it.bookUrl }
                            ) { book ->
                                val shelfState = viewModel.getCurrentBookShelfState(book)
                                ExploreBookItem(
                                    book = book,
                                    shelfState = shelfState,
                                    onClick = { onBookClick(book) },
                                    modifier = Modifier.animateItem()
                                )
                            }

                            item {
                                LoadMoreFooter(
                                    isLoading = isLoading,
                                    errorMsg = errorMsg,
                                    onRetry = viewModel::loadMore
                                )
                            }
                        }
                    }
                }
            }

            val shouldLoadMore = remember {
                derivedStateOf {
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    totalItems > 0 && lastVisibleIndex >= totalItems - 9
                }
            }

            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value) {
                    viewModel.loadMore()
                }
            }
        }
    }
}

@Composable
fun KindListItem(
    kind: ExploreKind,
    currentTitle: String?,
    onClick: () -> Unit
) {
    val isClickable = !kind.url.isNullOrBlank()
    val isSelected = kind.title == currentTitle
    FilterChip(
        onClick = { if (isClickable) onClick() },
        enabled = isClickable,
        selected = isSelected,
        label = {
            Text(
                text = kind.title,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .then(
                if (!isClickable) Modifier.fillMaxWidth()
                else Modifier.fillMaxWidth(1 / 3f)
            )
            .padding(horizontal = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreTopBar(
    title: String,
    filterState: BookFilterState,
    onBack: () -> Unit,
    onFilterSelect: (BookFilterState) -> Unit,
    onSelectKindClick: () -> Unit,
    onToggleGridMode: () -> Unit,
    isGridMode: Boolean,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    MediumTopAppBar(
        title = { AnimatedTextLine(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "Filter")
            }
            IconButton(onClick = { onSelectKindClick() }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "分类")
            }
            IconButton(onClick = { onToggleGridMode() }) {
                Icon(
                    imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "切换布局"
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("全部显示") },
                    onClick = {
                        onFilterSelect(BookFilterState.SHOW_ALL)
                        showMenu = false
                    },
                    trailingIcon = { if(filterState == BookFilterState.SHOW_ALL) Icon(Icons.Default.Check, null) }
                )
                DropdownMenuItem(
                    text = { Text("隐藏已在书架的同源书籍") },
                    onClick = {
                        onFilterSelect(BookFilterState.HIDE_IN_SHELF)
                        showMenu = false
                    },
                    trailingIcon = { if(filterState == BookFilterState.HIDE_IN_SHELF) Icon(Icons.Default.Check, null) }
                )
                DropdownMenuItem(
                    text = { Text("隐藏已在书架的非同源书籍") },
                    onClick = {
                        onFilterSelect(BookFilterState.HIDE_SAME_NAME_AUTHOR)
                        showMenu = false
                    },
                    trailingIcon = { if(filterState == BookFilterState.HIDE_SAME_NAME_AUTHOR) Icon(Icons.Default.Check, null) }
                )
                DropdownMenuItem(
                    text = { Text("只显示不在书架的书籍") },
                    onClick = {
                        onFilterSelect(BookFilterState.SHOW_NOT_IN_SHELF_ONLY)
                        showMenu = false
                    },
                    trailingIcon = { if(filterState == BookFilterState.SHOW_NOT_IN_SHELF_ONLY) Icon(Icons.Default.Check, null) }
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun ExploreBookItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column {

            Cover(path = book.coverUrl)

            if (shelfState != BookShelfState.NOT_IN_SHELF) {
                Spacer(modifier = Modifier.height(4.dp))
                BookshelfStatusBadge(
                    shelfState = shelfState,
                    modifier = Modifier
                        .width(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically)) {

            Text(
                text = book.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )

                val latestChapter = book.latestChapterTitle
                if (!latestChapter.isNullOrEmpty()) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1
                    )

                    Text(
                        text = "最新: $latestChapter",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val intro = book.intro?.replace("\\s+".toRegex(), "") ?: ""
            if (intro.isNotEmpty()) {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val kinds = book.getKindList()
            if (kinds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    kinds.forEach { kind ->
                        TagChip(text = kind)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreBookGridItem(
    book: SearchBook,
    onClick: () -> Unit,
    shelfState: BookShelfState,
    modifier: Modifier = Modifier
) {

    val badgeText: String? = when (shelfState) {
        BookShelfState.IN_SHELF -> "已在书架"
        BookShelfState.SAME_NAME_AUTHOR -> "同名书籍"
        BookShelfState.NOT_IN_SHELF -> null
    }

    val content: (@Composable RowScope.() -> Unit)? = if (!badgeText.isNullOrBlank()) {
        {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp)
            )
        }
    } else {
        null
    }

    Column(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {

        Cover(
            path = book.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(12 / 17f),
            badgeContent = content
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = book.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BookshelfStatusBadge(
    shelfState: BookShelfState,
    modifier: Modifier = Modifier
) {
    val text = when (shelfState) {
        BookShelfState.IN_SHELF -> "已在书架"
        BookShelfState.SAME_NAME_AUTHOR -> "同名书籍"
        else -> null
    }

    if (text != null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

// 简单的标签组件
@Composable
fun TagChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadMoreFooter(
    isLoading: Boolean,
    errorMsg: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> LoadingIndicator()

            errorMsg != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败: $errorMsg", color = Color.Red)
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }

            else -> {
                Text(
                    text = "已经到底了~",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
