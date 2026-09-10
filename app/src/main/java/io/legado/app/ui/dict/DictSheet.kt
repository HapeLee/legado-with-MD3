package io.legado.app.ui.dict

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.rss.read.VisibleWebViewCompose
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.pager.pagerHeight
import io.legado.app.ui.widget.components.pager.rememberPagerAnimatedHeight
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.HtmlContent
import io.legado.app.utils.openUrl
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
    var webPage by remember { mutableStateOf<DictWebPage?>(null) }

    LaunchedEffect(show, word) {
        if (show) {
            viewModel.onIntent(DictIntent.Load(word))
        }
    }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DictEffect.ShowToast -> Unit
                is DictEffect.OpenWebPage -> webPage = DictWebPage(effect.title, effect.url)
            }
        }
    }

    AppModalBottomSheet(
        show = show && webPage == null,
        onDismissRequest = onDismissRequest,
        title = word,
        endAction = {
            MediumTonalButton(
                onClick = { viewModel.onIntent(DictIntent.OpenSelectedRuleInWebView) },
                icon = Icons.Default.OpenInBrowser,
                contentDescription = stringResource(R.string.source_tab_web_view),
            )
        },
        contentPaddingEnabled = false,
        animateContentSize = false,
    ) {
        DictSheetContent(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }

    DictWebSheet(
        page = webPage,
        onDismissRequest = { webPage = null },
    )
}

@Composable
private fun DictSheetContent(
    state: DictUiState,
    onIntent: (DictIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DictContent(
        state = state,
        onIntent = onIntent,
        modifier = modifier,
    )
}

private data class DictWebPage(
    val title: String,
    val url: String,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DictWebSheet(
    page: DictWebPage?,
    onDismissRequest: () -> Unit,
) {
    page ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    var progress by remember(page.url) { mutableIntStateOf(0) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = page.title,
        contentPaddingEnabled = false,
        sheetGesturesEnabled = false,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 520.dp)
        ) {
            VisibleWebViewCompose(
                modifier = Modifier.fillMaxSize(),
                onCreated = { webView ->
                    webView.apply {
                        settings.apply {
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            javaScriptEnabled = true
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = handleUrl(request.url)

                            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                url: String
                            ): Boolean =
                                handleUrl(Uri.parse(url))

                            private fun handleUrl(uri: Uri): Boolean {
                                if (uri.scheme == "http" || uri.scheme == "https") return false
                                context.openUrl(uri)
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        loadUrl(page.url)
                    }
                },
            )
            if (progress in 0..99) {
                AppLinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
                HtmlContent(
                    html = state.htmlContent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                )
            }
        }
    }
}
