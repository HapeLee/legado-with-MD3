package io.legado.app.ui.book.explore

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.model.BookShelfState
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.theme.responsiveHazeSource
import io.legado.app.ui.widget.components.AnimatedTextLine
import io.legado.app.ui.widget.components.Cover
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.button.AnimatedTextButton
import io.legado.app.ui.widget.components.button.TopBarActionButton
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel

@SuppressLint("LocalContextConfigurationRead", "ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
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
    val isBookEnd by viewModel.isEnd.collectAsState()
    val shouldTriggerAutoLoad by viewModel.shouldTriggerAutoLoad.collectAsState()
    val kinds by viewModel.kinds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.errorMsg.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val selectedTitle by viewModel.selectedKindTitle.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showKindSheet by remember { mutableStateOf(false) }
    val layoutState by viewModel.layoutState.collectAsState()
    val isGridMode = layoutState == 1
    var showGridCountSheet by remember { mutableStateOf(false) }
    val gridColumnCount by viewModel.gridCount.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    val hazeState = remember { HazeState() }
    val shouldLoadMoreList = remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
    }

    val shouldLoadMoreGrid = remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 1
        }
    }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(shouldLoadMoreList.value, isGridMode) {
        if (!isGridMode && shouldLoadMoreList.value) viewModel.loadMore()
    }

    LaunchedEffect(shouldLoadMoreGrid.value, isGridMode) {
        if (isGridMode && shouldLoadMoreGrid.value) viewModel.loadMore()
    }

    LaunchedEffect(shouldTriggerAutoLoad) {
        if (shouldTriggerAutoLoad) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(isGridMode) {
        if (isGridMode) {
            if (listState.firstVisibleItemIndex > 0) {
                gridState.scrollToItem(listState.firstVisibleItemIndex)
            }
        } else {
            if (gridState.firstVisibleItemIndex > 0) {
                listState.scrollToItem(gridState.firstVisibleItemIndex)
            }
        }
    }

    if (showGridCountSheet) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

        GlassModalBottomSheet(
            sheetState = sheetState,
            modifier = Modifier
                .padding(16.dp),
            onDismissRequest = { showGridCountSheet = false }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "布局列数",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.width(12.dp))

                TextCard(
                    text = "$gridColumnCount 列",
                    textStyle = MaterialTheme.typography.titleSmall,
                    verticalPadding = 4.dp,
                    horizontalPadding = 12.dp,
                    cornerRadius = 12.dp
                )
            }

            Slider(
                value = gridColumnCount.toFloat(),
                onValueChange = {
                    val col = it.toInt().coerceIn(1, 10)
                    viewModel.saveGridCount(col)
                },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = { showGridCountSheet = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("完成")
            }
        }
    }

    if (showKindSheet) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { newValue ->
                newValue != SheetValue.PartiallyExpanded
            }
        )

        GlassModalBottomSheet(
            onDismissRequest = { showKindSheet = false },
            sheetState = sheetState,
            modifier = Modifier
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.72f)
        ) {

            var kindQuery by remember { mutableStateOf("") }

            SearchBarSection(
                query = kindQuery,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                onQueryChange = { kindQuery = it },
                placeholder = "选择或搜索分类",
            )

            val filteredKinds = remember(kindQuery, kinds) {
                if (kindQuery.isBlank()) kinds
                else kinds.filter { kind ->
                    kind.title.contains(kindQuery, ignoreCase = true) ||
                            (kind.url?.contains(kindQuery, ignoreCase = true) == true)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                itemsIndexed(
                    items = filteredKinds,
                    key = { index, kind -> "${kind.url ?: kind.title}_$index" },
                    span = { _, kind ->
                        val isClickable = !kind.url.isNullOrBlank()
                        if (isClickable) GridItemSpan(1) else GridItemSpan(3)
                    }
                ) { _, kind ->
                    KindGridItem(
                        modifier = Modifier.animateItem(),
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

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                modifier = Modifier.responsiveHazeEffect(
                    state = hazeState
                ),
                title = {
                    AnimatedTextLine(
                        selectedTitle ?: title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = GlassTopAppBarDefaults.glassColors(),
                navigationIcon = {
                    TopbarNavigationButton(onClick = onBack)
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.animateContentSize(tween(300))
                    ) {
                        TopBarActionButton(
                            onClick = { showMenu = true },
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter"
                        )

                        TopBarActionButton(
                            onClick = { showKindSheet = true },
                            imageVector = Icons.Outlined.FilterAlt,
                            contentDescription = "分类"
                        )

                        AnimatedVisibility(
                            visible = isGridMode,
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(300))
                        ) {
                            TopBarActionButton(
                                onClick = { showGridCountSheet = true },
                                imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                contentDescription = "列数设置"
                            )
                        }
                    }

                    TopBarActionButton(
                        onClick = { viewModel.setLayout() },
                        imageVector = if (!isGridMode) Icons.AutoMirrored.Outlined.FormatListBulleted else Icons.Default.GridView,
                        contentDescription = "切换布局"
                    )

                    RoundDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        RoundDropdownMenuItem(
                            text = { Text("全部显示") },
                            onClick = {
                                viewModel.setFilterState(BookFilterState.SHOW_ALL)
                                showMenu = false
                            },
                            trailingIcon = {
                                if (filterState == BookFilterState.SHOW_ALL)
                                    Icon(Icons.Default.Check, null)
                            }
                        )

                        RoundDropdownMenuItem(
                            text = { Text("隐藏已在书架的同源书籍") },
                            onClick = {
                                viewModel.setFilterState(BookFilterState.HIDE_IN_SHELF)
                                showMenu = false
                            },
                            trailingIcon = {
                                if (filterState == BookFilterState.HIDE_IN_SHELF)
                                    Icon(Icons.Default.Check, null)
                            }
                        )

                        RoundDropdownMenuItem(
                            text = { Text("隐藏已在书架的非同源书籍") },
                            onClick = {
                                viewModel.setFilterState(BookFilterState.HIDE_SAME_NAME_AUTHOR)
                                showMenu = false
                            },
                            trailingIcon = {
                                if (filterState == BookFilterState.HIDE_SAME_NAME_AUTHOR)
                                    Icon(Icons.Default.Check, null)
                            }
                        )

                        RoundDropdownMenuItem(
                            text = { Text("只显示不在书架的书籍") },
                            onClick = {
                                viewModel.setFilterState(BookFilterState.SHOW_NOT_IN_SHELF_ONLY)
                                showMenu = false
                            },
                            trailingIcon = {
                                if (filterState == BookFilterState.SHOW_NOT_IN_SHELF_ONLY)
                                    Icon(Icons.Default.Check, null)
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = isRefreshing,
            state = pullToRefreshState,
            onRefresh = { viewModel.loadMore(isRefresh = true) },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                )
            }
        ) {
            Crossfade(
                targetState = isGridMode,
                animationSpec = tween(250),
                label = "LayoutCrossfade"
            ) { isGrid ->
                if (isGrid) {
                    LazyVerticalGrid(
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .responsiveHazeSource(hazeState),
                        columns = GridCells.Fixed(gridColumnCount),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 12.dp,
                            bottom = paddingValues.calculateBottomPadding() + 12.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = books,
                            key = { it.bookUrl }
                        ) { book ->
                            ExploreBookGridItem(
                                book = book,
                                shelfState = viewModel.getBookShelfStateFlow(book),
                                onClick = { onBookClick(book) },
                                modifier = Modifier.animateItem()
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LoadMoreFooter(
                                isLoading = isLoading,
                                errorMsg = errorMsg,
                                isEnd = isBookEnd,
                                onRetry = viewModel::loadMore
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .responsiveHazeSource(hazeState),
                        state = listState,
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + 16.dp
                        )
                    ) {
                        items(
                            items = books,
                            key = { it.bookUrl }
                        ) { book ->
                            ExploreBookItem(
                                book = book,
                                shelfState = viewModel.getBookShelfStateFlow(book),
                                onClick = { onBookClick(book) },
                                modifier = Modifier.animateItem()
                            )
                        }

                        item {
                            LoadMoreFooter(
                                isLoading = isLoading,
                                errorMsg = errorMsg,
                                isEnd = isBookEnd,
                                onRetry = viewModel::loadMore
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun KindGridItem(
    kind: ExploreKind,
    currentTitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ExploreBookItem(
    book: SearchBook,
    shelfState: Flow<BookShelfState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shelfState by shelfState.collectAsState(initial = BookShelfState.NOT_IN_SHELF)

    val badge: (@Composable RowScope.() -> Unit)?
            = when (shelfState) {

        BookShelfState.IN_SHELF -> {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已在书架",
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        BookShelfState.SAME_NAME_AUTHOR -> {
            {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "同名书籍",
                    modifier = Modifier.size(12.dp)
                )

            }
        }

        BookShelfState.NOT_IN_SHELF -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {

        Cover(
            path = book.coverUrl,
            modifier = Modifier.width(72.dp),
            badgeContent = badge)

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
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        maxLines = 1
                    )

                    Text(
                        text = "最新: $latestChapter",
                        style = MaterialTheme.typography.labelSmall,
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
                    style = MaterialTheme.typography.labelSmall,
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
    shelfState: Flow<BookShelfState>,
    modifier: Modifier = Modifier
) {

    val shelfState by shelfState.collectAsState(initial = BookShelfState.NOT_IN_SHELF)

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
    isEnd: Boolean,
    onRetry: () -> Unit
) {

    LaunchedEffect(isLoading, errorMsg, isEnd) {
        if (!isLoading && errorMsg == null && !isEnd) {
            while (true) {
                onRetry()
                delay(1000L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            AnimatedContent(
                targetState = when {
                    isLoading -> "加载中…"
                    errorMsg != null -> "加载失败: $errorMsg"
                    isEnd -> "已经到底了~"
                    else -> "我爱你"
                },
                label = "FooterTextChange"
            ) { text ->
                Text(
                    text = text,
                    color = when {
                        errorMsg != null -> Color.Red
                        else -> Color.Gray
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedTextButton(
                isLoading = isLoading,
                onClick = onRetry,
                text = if (errorMsg != null) "重试" else "再试一次",
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}