package io.legado.app.ui.rss.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.ui.rss.read.RedirectPolicy
import io.legado.app.ui.widget.components.variable.VariableEditorUiState
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@Composable
fun RssSortRouteScreen(
    sourceUrl: String?,
    initialSortUrl: String?,
    initialSearchKey: String?,
    onBackClick: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenRead: (title: String?, origin: String, link: String?, openUrl: String?) -> Unit,
    onEditSource: (String) -> Unit,
    onLogin: (String) -> Unit,
    viewModel: RssSortViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var sortList by remember(sourceUrl, initialSortUrl, initialSearchKey) {
        mutableStateOf<List<Pair<String, String>>>(emptyList())
    }
    var articleStyle by remember(sourceUrl, initialSortUrl, initialSearchKey) { mutableIntStateOf(0) }
    var redirectPolicy by remember(sourceUrl, initialSortUrl, initialSearchKey) {
        mutableStateOf(RedirectPolicy.ALLOW_ALL)
    }
    var screenTitle by remember(sourceUrl, initialSortUrl, initialSearchKey) { mutableStateOf("") }
    val setSourceVariableText = stringResource(R.string.set_source_variable)
    val errorText = stringResource(R.string.error)

    var showReadRecordSheet by remember { mutableStateOf(false) }
    var readRecords by remember { mutableStateOf<List<RssReadRecord>>(emptyList()) }
    var sourceVariableSheet by remember { mutableStateOf<VariableEditorUiState?>(null) }
    val shouldShowExpandButton by viewModel.shouldShowExpandButton.collectAsStateWithLifecycle()

    suspend fun reloadSourceState() {
        withContext(Dispatchers.IO) {
            viewModel.initDataSource(sourceUrl)
        }
        sortList = viewModel.loadSorts(initialSortUrl, initialSearchKey)
        articleStyle = viewModel.currentArticleStyle()
        screenTitle = initialSearchKey ?: viewModel.rssSource?.sourceName.orEmpty()
        redirectPolicy = RedirectPolicy.fromString(viewModel.rssSource?.redirectPolicy)
    }

    LaunchedEffect(sourceUrl, initialSortUrl, initialSearchKey) {
        reloadSourceState()
    }
    DisposableEffect(lifecycleOwner, sourceUrl) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reloadSourceState() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    RssSortScreen(
        title = screenTitle.ifBlank { stringResource(R.string.rss) },
        sortList = sortList,
        preferredSortUrl = initialSortUrl,
        searchKey = initialSearchKey,
        hasSearch = !viewModel.rssSource?.searchUrl.isNullOrBlank(),
        hasLogin = !viewModel.rssSource?.loginUrl.isNullOrBlank(),
        redirectPolicy = redirectPolicy,
        showReadRecordSheet = showReadRecordSheet,
        readRecords = readRecords,
        sourceVariableSheet = sourceVariableSheet,
        shouldShowExpandButton = shouldShowExpandButton,
        onBackClick = onBackClick,
        onSearch = onSearch,
        onLogin = {
            viewModel.rssSource?.sourceUrl?.let(onLogin)
        },
        onRefreshSort = {
            scope.launch {
                viewModel.clearSortCache()
                sortList = viewModel.loadSorts(initialSortUrl, initialSearchKey)
            }
        },
        onSetSourceVariable = {
            scope.launch {
                val source = viewModel.rssSource
                if (source == null) {
                    context.toastOnUi("源不存在")
                    return@launch
                }
                val comment = source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
                val variable = withContext(Dispatchers.IO) { source.getVariable() }
                sourceVariableSheet = VariableEditorUiState(
                    title = setSourceVariableText,
                    key = source.getKey(),
                    value = variable.orEmpty(),
                    comment = comment
                )
            }
        },
        onDismissSourceVariable = { sourceVariableSheet = null },
        onSourceVariableChange = { value ->
            sourceVariableSheet = sourceVariableSheet?.copy(value = value)
        },
        onSaveSourceVariable = {
            viewModel.setSourceVariable(sourceVariableSheet?.value)
            sourceVariableSheet = null
            context.toastOnUi(R.string.save_success)
        },
        onEditSource = {
            viewModel.rssSource?.sourceUrl?.let(onEditSource)
        },
        onSwitchLayout = {
            viewModel.switchLayout()
            articleStyle = viewModel.currentArticleStyle()
        },
        onReadRecord = {
            scope.launch(Dispatchers.IO) {
                val records = viewModel.getRecords()
                withContext(Dispatchers.Main) {
                    readRecords = records
                    showReadRecordSheet = true
                }
            }
        },
        onDismissReadRecord = { showReadRecordSheet = false },
        onClearReadRecord = {
            viewModel.deleteAllRecord()
            readRecords = emptyList()
        },
        onOpenReadRecord = { record ->
            showReadRecordSheet = false
            val openOrigin = record.origin.ifBlank {
                viewModel.rssSource?.sourceUrl ?: sourceUrl.orEmpty()
            }
            if (openOrigin.isBlank()) {
                context.toastOnUi(errorText)
            } else {
                onOpenRead(record.title, openOrigin, null, record.record)
            }
        },
        onClearArticles = { viewModel.clearArticles() },
        onRedirectPolicyChanged = { policy ->
            viewModel.rssSource?.let { source ->
                viewModel.updateRssSourceRedirectPolicy(source.sourceUrl, policy.name)
                redirectPolicy = policy
            }
            context.toastOnUi("重定向策略已更新")
        },
        pagerContent = { _, sort, paddingValues ->
            val pageViewModel: RssArticlesViewModel = koinViewModel(
                key = "rss_${viewModel.url}_${sort.first}_${sort.second}_${initialSearchKey.orEmpty()}"
            )
            RssArticlesPage(
                sortName = sort.first,
                sortUrl = sort.second,
                articleStyle = articleStyle,
                rssUrl = viewModel.url,
                rssSource = viewModel.rssSource,
                viewModel = pageViewModel,
                searchKey = initialSearchKey,
                paddingValues = paddingValues,
                onRead = { article ->
                    viewModel.read(article)
                    onOpenRead(article.title, article.origin, article.link, null)
                }
            )
        }
    )
}
