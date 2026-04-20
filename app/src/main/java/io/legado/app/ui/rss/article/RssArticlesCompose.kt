package io.legado.app.ui.rss.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.cover.buildCoverImageRequest
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject

private enum class RssArticleLayout {
    List, LargeCard, GridCard, Waterfall
}

private fun Int.toRssArticleLayout(): RssArticleLayout {
    return when (this) {
        1 -> RssArticleLayout.LargeCard
        2 -> RssArticleLayout.GridCard
        3 -> RssArticleLayout.Waterfall
        else -> RssArticleLayout.List
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssArticlesPage(
    sortName: String,
    sortUrl: String,
    articleStyle: Int,
    rssUrl: String?,
    rssSource: RssSource?,
    viewModel: RssArticlesViewModel,
    onRead: (RssArticle) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layout = remember(articleStyle) { articleStyle.toRssArticleLayout() }
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()

    LaunchedEffect(sortName, sortUrl) {
        viewModel.init(sortName, sortUrl)
    }

    val articleFlow = remember(rssUrl, sortName) {
        val origin = rssUrl.orEmpty()
        if (origin.isBlank()) {
            flowOf(emptyList())
        } else {
            appDb.rssArticleDao.flowByOriginSort(origin, sortName)
        }.catch {
            AppLog.put("订阅文章界面获取数据失败\n${it.localizedMessage}", it)
        }.flowOn(IO)
    }
    val articles by articleFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(rssSource?.sourceUrl, sortName, sortUrl) {
        rssSource?.let(viewModel::loadArticles)
    }

    LaunchedEffect(loadState.errorMessage) {
        loadState.errorMessage?.takeIf { it.isNotBlank() }?.let(context::toastOnUi)
    }

    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = loadState.isRefreshing,
        onRefresh = { rssSource?.let(viewModel::loadArticles) },
        state = refreshState,
        modifier = modifier.fillMaxSize()
    ) {
        when (layout) {
            RssArticleLayout.List, RssArticleLayout.LargeCard -> {
                val listState = rememberLazyListState()
                LoadMoreDetector(
                    state = listState,
                    enabled = loadState.canLoadMore,
                    onLoadMore = { rssSource?.let(viewModel::loadMore) }
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = articles.size,
                        key = { index -> articles[index].origin + articles[index].link }
                    ) { index ->
                        RssArticleItem(
                            article = articles[index],
                            layout = layout,
                            onClick = onRead
                        )
                    }
                    item {
                        LoadMoreFooter(
                            state = loadState,
                            onRetry = { rssSource?.let(viewModel::loadMore) }
                        )
                    }
                }
            }

            RssArticleLayout.GridCard -> {
                val gridState = rememberLazyGridState()
                GridLoadMoreDetector(
                    state = gridState,
                    enabled = loadState.canLoadMore,
                    onLoadMore = { rssSource?.let(viewModel::loadMore) }
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = articles,
                        key = { it.origin + it.link }
                    ) { article ->
                        RssArticleItem(
                            article = article,
                            layout = layout,
                            onClick = onRead
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LoadMoreFooter(
                            state = loadState,
                            onRetry = { rssSource?.let(viewModel::loadMore) }
                        )
                    }
                }
            }

            RssArticleLayout.Waterfall -> {
                val staggeredState = rememberLazyStaggeredGridState()
                StaggeredLoadMoreDetector(
                    state = staggeredState,
                    enabled = loadState.canLoadMore,
                    onLoadMore = { rssSource?.let(viewModel::loadMore) }
                )
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = staggeredState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    items(
                        items = articles,
                        key = { it.origin + it.link }
                    ) { article ->
                        RssArticleItem(
                            article = article,
                            layout = layout,
                            onClick = onRead
                        )
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        LoadMoreFooter(
                            state = loadState,
                            onRetry = { rssSource?.let(viewModel::loadMore) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreDetector(
    state: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(state, enabled) {
        snapshotFlow {
            val info = state.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            enabled && info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }.collect { shouldLoad ->
            if (shouldLoad) onLoadMore()
        }
    }
}

@Composable
private fun GridLoadMoreDetector(
    state: LazyGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(state, enabled) {
        snapshotFlow {
            val info = state.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            enabled && info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }.collect { shouldLoad ->
            if (shouldLoad) onLoadMore()
        }
    }
}

@Composable
private fun StaggeredLoadMoreDetector(
    state: LazyStaggeredGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(state, enabled) {
        snapshotFlow {
            val info = state.layoutInfo
            val lastVisible = info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            enabled && info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }.collect { shouldLoad ->
            if (shouldLoad) onLoadMore()
        }
    }
}

@Composable
private fun LoadMoreFooter(
    state: RssArticlesLoadState,
    onRetry: () -> Unit
) {
    val text = when {
        state.isLoadingMore -> "加载中..."
        !state.hasMore -> "没有更多了"
        state.errorMessage != null -> "加载失败，点击重试"
        else -> "上拉加载更多"
    }
    val contentModifier = if (state.errorMessage != null) {
        Modifier.clickable(onClick = onRetry)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        EmptyMessage(
            message = text,
            isLoading = state.isLoadingMore,
            modifier = contentModifier
        )
    }
}

@Composable
private fun RssArticleItem(
    article: RssArticle,
    layout: RssArticleLayout,
    onClick: (RssArticle) -> Unit
) {
    val containerColor = LegadoTheme.colorScheme.surfaceContainerLow
    val titleColor = if (article.read) {
        LegadoTheme.colorScheme.onSurfaceVariant
    } else {
        LegadoTheme.colorScheme.onSurface
    }
    val titleMaxLines = when (layout) {
        RssArticleLayout.List -> 2
        RssArticleLayout.LargeCard -> 2
        RssArticleLayout.GridCard -> 2
        RssArticleLayout.Waterfall -> 3
    }

    GlassCard(
        onClick = { onClick(article) },
        cornerRadius = 12.dp,
        containerColor = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (layout) {
            RssArticleLayout.List -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = article.title,
                            style = LegadoTheme.typography.titleSmall,
                            color = titleColor,
                            maxLines = titleMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = article.pubDate.orEmpty(),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!article.image.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        RssArticleImage(
                            article = article,
                            showPlaceholder = false,
                            modifier = Modifier
                                .width(110.dp)
                                .height(68.dp)
                        )
                    }
                }
            }

            RssArticleLayout.LargeCard -> {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    RssArticleImage(
                        article = article,
                        showPlaceholder = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = article.title,
                        style = LegadoTheme.typography.titleSmall,
                        color = titleColor,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.pubDate.orEmpty(),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            RssArticleLayout.GridCard -> {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    RssArticleImage(
                        article = article,
                        showPlaceholder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = article.title,
                        style = LegadoTheme.typography.bodyMedium,
                        color = titleColor,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.pubDate.orEmpty(),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            RssArticleLayout.Waterfall -> {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    RssArticleImage(
                        article = article,
                        showPlaceholder = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LegadoTheme.colorScheme.surfaceContainer)
                            .heightIn(min = 80.dp)
                    )
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = article.title,
                            style = LegadoTheme.typography.titleSmall,
                            color = titleColor,
                            maxLines = titleMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = article.pubDate.orEmpty(),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RssArticleImage(
    article: RssArticle,
    showPlaceholder: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader: ImageLoader = koinInject()
    val imageUrl = article.image?.takeIf { it.isNotBlank() }

    var hasError by remember(imageUrl) { mutableStateOf(false) }

    if (imageUrl == null || (hasError && !showPlaceholder)) {
        if (showPlaceholder) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = R.drawable.image_rss_article,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    AsyncImage(
        model = buildCoverImageRequest(
            context = context,
            data = imageUrl,
            sourceOrigin = article.origin,
            loadOnlyWifi = false,
            crossfade = true
        ),
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = if (showPlaceholder) painterResource(R.drawable.image_rss_article) else null,
        error = if (showPlaceholder) painterResource(R.drawable.image_rss_article) else null,
        onError = { hasError = true },
        modifier = modifier
    )
}
