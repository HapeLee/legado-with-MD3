package io.legado.app.ui.rss.article

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.legado.app.R
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.read.RedirectPolicy
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.res.stringResource

@Composable
fun RssSortRouteScreen(
    sourceUrl: String?,
    initialSortUrl: String?,
    initialSearchKey: String?,
    onBackClick: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenRead: (title: String?, origin: String, link: String?, openUrl: String?) -> Unit,
    viewModel: RssSortViewModel = koinViewModel()
) {
    val context = LocalContext.current
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
    var sourceVariableSheet by remember { mutableStateOf<RssSourceVariableSheetState?>(null) }

    suspend fun reloadSourceState() {
        withContext(Dispatchers.IO) {
            viewModel.initDataSource(sourceUrl)
        }
        sortList = viewModel.loadSorts(initialSortUrl, initialSearchKey)
        articleStyle = viewModel.currentArticleStyle()
        screenTitle = initialSearchKey ?: viewModel.rssSource?.sourceName.orEmpty()
        redirectPolicy = RedirectPolicy.fromString(viewModel.rssSource?.redirectPolicy)
    }

    val editSourceResult = rememberLauncherForActivityResult(
        StartActivityContract(RssSourceEditActivity::class.java)
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            scope.launch { reloadSourceState() }
        }
    }

    LaunchedEffect(sourceUrl, initialSortUrl, initialSearchKey) {
        reloadSourceState()
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
        onBackClick = onBackClick,
        onSearch = onSearch,
        onLogin = {
            context.startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }
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
                sourceVariableSheet = RssSourceVariableSheetState(
                    title = setSourceVariableText,
                    variable = variable,
                    comment = comment
                )
            }
        },
        onDismissSourceVariable = { sourceVariableSheet = null },
        onSaveSourceVariable = { variable ->
            viewModel.setSourceVariable(variable)
            sourceVariableSheet = null
            context.toastOnUi(R.string.save_success)
        },
        onEditSource = {
            viewModel.rssSource?.sourceUrl?.let { srcUrl ->
                editSourceResult.launch { putExtra("sourceUrl", srcUrl) }
            }
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
