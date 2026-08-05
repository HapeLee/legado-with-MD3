package io.legado.app.ui.dict

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.pager.pagerHeight
import io.legado.app.ui.widget.components.pager.rememberPagerAnimatedHeight
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun DictSheet(
    show: Boolean,
    word: String,
    onDismissRequest: () -> Unit,
    viewModel: DictViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(show, word) {
        if (show) {
            viewModel.onIntent(DictIntent.Load(word))
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = word,
        //内部pager管理高度，避免二次动画
        animateContentSize = false,
    ) {
        DictSheetContent(
            state = state,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun DictSheetContent(
    state: DictUiState,
    onIntent: (DictIntent) -> Unit,
    effects: Flow<DictEffect>,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is DictEffect.ShowToast -> Unit
            }
        }
    }

    DictContent(
        state = state,
        onIntent = onIntent,
        modifier = modifier,
    )
}

@Composable
private fun DictContent(
    state: DictUiState,
    onIntent: (DictIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blankWordText = stringResource(R.string.cannot_empty)
    val emptyText = stringResource(R.string.empty)
    val searchEmptyText = stringResource(R.string.search_empty)
    val tabTitles = remember(state.rules) { state.rules.map { it.name } }
    val emptyMessageFor: (DictEmptyReason?) -> String? = { reason ->
        when (reason) {
            DictEmptyReason.BlankWord -> blankWordText
            DictEmptyReason.NoRules -> emptyText
            DictEmptyReason.NoResult -> searchEmptyText
            null -> null
        }
    }
    val selectedIndex = state.selectedIndex.coerceIn(0, state.rules.lastIndex.coerceAtLeast(0))

    if (state.rules.size > 1) {
        DictPagerContent(
            state = state,
            tabTitles = tabTitles,
            selectedIndex = selectedIndex,
            emptyMessageFor = emptyMessageFor,
            onIntent = onIntent,
            modifier = modifier,
        )
    } else {
        val pageState = state.pages.getOrNull(selectedIndex)
            ?: DictPageUiState(emptyReason = state.emptyReason)
        DictPageContent(
            state = pageState,
            emptyMessage = emptyMessageFor(pageState.emptyReason),
            modifier = modifier,
        )
    }
}

@Composable
private fun DictPagerContent(
    state: DictUiState,
    tabTitles: List<String>,
    selectedIndex: Int,
    emptyMessageFor: (DictEmptyReason?) -> String?,
    onIntent: (DictIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentOnIntent by rememberUpdatedState(onIntent)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { state.rules.size }
    val pageHeights = remember(state.word, state.rules) { mutableStateMapOf<Int, Int>() }
    val animatedHeight by rememberPagerAnimatedHeight(
        pagerState = pagerState,
        pageHeights = pageHeights,
        fallbackHeight = 200.dp,
        heightAnimationSpec = spring(),
    )

    Column(
        modifier = modifier,
    ) {
        AppTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = pagerState.currentPage.coerceIn(0, state.rules.lastIndex),
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isScrollable = true,
        )

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            overscrollEffect = null,
            modifier = Modifier
                .weight(1f, fill = false)
                .clipToBounds()
                .pagerHeight(animatedHeight),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        pageHeights[page] = size.height
                    }
            ) {
                val pageState = state.pages.getOrNull(page) ?: DictPageUiState()
                DictPageContent(
                    state = pageState,
                    emptyMessage = emptyMessageFor(pageState.emptyReason),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    LaunchedEffect(selectedIndex, state.rules.size) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page ->
                currentOnIntent(DictIntent.SelectRule(page))
            }
    }
}

@Composable
private fun DictPageContent(
    state: DictPageUiState,
    emptyMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppCircularProgressIndicator()
                }
            }

            emptyMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyMessage(message = emptyMessage)
                }
            }

            state.htmlContent.isNotBlank() -> {
                DictHtmlContent(
                    htmlContent = state.htmlContent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun DictHtmlContent(
    htmlContent: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(htmlContent) { parseHtmlBlocks(htmlContent) }
    Column(modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
        blocks.forEach { block ->
            when (block) {
                is HtmlBlock.Text -> {
                    AppText(
                        text = remember(block.content) {
                            AnnotatedString.fromHtml(block.content)
                        },
                    )
                }
                is HtmlBlock.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}

private sealed interface HtmlBlock {
    data class Text(val content: String) : HtmlBlock
    data class Image(val url: String) : HtmlBlock
}

private val IMG_REGEX = Regex("""<img\s+[^>]*src\s*=\s*["']([^"']+)["'][^>]*/?>""")

private fun parseHtmlBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    var lastIndex = 0
    IMG_REGEX.findAll(html).forEach { match ->
        if (match.range.first > lastIndex) {
            val textPart = html.substring(lastIndex, match.range.first).trim()
            if (textPart.isNotBlank()) blocks.add(HtmlBlock.Text(textPart))
        }
        blocks.add(HtmlBlock.Image(match.groupValues[1]))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < html.length) {
        val remaining = html.substring(lastIndex).trim()
        if (remaining.isNotBlank()) blocks.add(HtmlBlock.Text(remaining))
    }
    return blocks
}
