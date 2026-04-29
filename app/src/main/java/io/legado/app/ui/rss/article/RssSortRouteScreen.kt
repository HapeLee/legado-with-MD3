@file:Suppress("DEPRECATION")

package io.legado.app.ui.rss.article

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.read.RedirectPolicy
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.showDialogFragment
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
    onBackClick: () -> Unit,
    onOpenRead: (title: String?, origin: String, link: String?, openUrl: String?) -> Unit,
    viewModel: RssSortViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val scope = rememberCoroutineScope()

    var sortList by remember(sourceUrl) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var articleStyle by remember(sourceUrl) { mutableIntStateOf(0) }
    var redirectPolicy by remember(sourceUrl) { mutableStateOf(RedirectPolicy.ALLOW_ALL) }
    var screenTitle by remember(sourceUrl) { mutableStateOf("") }
    val setSourceVariableText = stringResource(R.string.set_source_variable)
    val errorText = stringResource(R.string.error)

    var showReadRecordSheet by remember { mutableStateOf(false) }
    var readRecords by remember { mutableStateOf<List<RssReadRecord>>(emptyList()) }

    fun reloadSourceState() {
        viewModel.initData(sourceUrl) {
            scope.launch {
                sortList = viewModel.loadSorts()
                articleStyle = viewModel.currentArticleStyle()
                screenTitle = viewModel.rssSource?.sourceName.orEmpty()
                redirectPolicy = RedirectPolicy.fromString(viewModel.rssSource?.redirectPolicy)
            }
        }
    }

    val editSourceResult = rememberLauncherForActivityResult(
        StartActivityContract(RssSourceEditActivity::class.java)
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            reloadSourceState()
        }
    }

    androidx.compose.runtime.LaunchedEffect(sourceUrl) {
        reloadSourceState()
    }

    RssSortScreen(
        title = screenTitle.ifBlank { stringResource(R.string.rss) },
        sortList = sortList,
        preferredSortUrl = initialSortUrl,
        hasLogin = !viewModel.rssSource?.loginUrl.isNullOrBlank(),
        redirectPolicy = redirectPolicy,
        showReadRecordSheet = showReadRecordSheet,
        readRecords = readRecords,
        onBackClick = onBackClick,
        onLogin = {
            context.startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }
        },
        onRefreshSort = {
            viewModel.clearSortCache {
                scope.launch { sortList = viewModel.loadSorts() }
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
                activity?.showDialogFragment(
                    VariableDialog(
                        setSourceVariableText,
                        source.getKey(),
                        variable,
                        comment
                    )
                )
            }
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
                key = "rss_${viewModel.url}_${sort.first}_${sort.second}"
            )
            RssArticlesPage(
                sortName = sort.first,
                sortUrl = sort.second,
                articleStyle = articleStyle,
                rssUrl = viewModel.url,
                rssSource = viewModel.rssSource,
                viewModel = pageViewModel,
                paddingValues = paddingValues,
                onRead = { article ->
                    viewModel.read(article)
                    onOpenRead(article.title, article.origin, article.link, null)
                }
            )
        }
    )
}
