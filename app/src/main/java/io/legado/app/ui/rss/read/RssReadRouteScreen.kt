@file:Suppress("DEPRECATION")

package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.help.http.CookieManager
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.topbar.GlassSmallTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.applyDayNight
import io.legado.app.data.entities.RssArticle
import io.legado.app.utils.keepScreenOn
import io.legado.app.utils.openUrl
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import org.koin.androidx.compose.koinViewModel

// 顶栏「更多」菜单的稳定 ID：文案随朗读状态变化，ID 只由语义决定。
private const val RssReadMenuGroup = "rss_read_menu"
private const val RssReadMenuShare = "rss_read_menu_share"
private const val RssReadMenuPlayVideo = "rss_read_menu_play_video"
private const val RssReadMenuReadAloud = "rss_read_menu_read_aloud"
private const val RssReadMenuLogin = "rss_read_menu_login"
private const val RssReadMenuRedirectPolicy = "rss_read_menu_redirect_policy"
private const val RssReadMenuRedirectPrefix = "rss_read_menu_redirect:"
private const val RssReadMenuLog = "rss_read_menu_log"
private const val RssReadMenuOpenInBrowser = "rss_read_menu_open_in_browser"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssReadRouteScreen(
    title: String?,
    origin: String,
    link: String?,
    openUrl: String?,
    startPage: Boolean,
    onBackClick: () -> Unit,
    onOpenArticles: (sortUrl: String?, targetOrigin: String?) -> Unit,
    onOpenVideoPlayer: (RssArticle) -> Unit,
    viewModel: ReadRssViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val appCompatActivity = activity as? AppCompatActivity
    val defaultTopBarTitle = stringResource(R.string.rss)

    var pageTitle by remember(title, defaultTopBarTitle) {
        mutableStateOf(title?.takeIf { it.isNotBlank() } ?: defaultTopBarTitle)
    }
    var webProgress by remember { mutableIntStateOf(100) }
    var showMenu by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember {
        mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
    }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var filePickerInFlight by remember { mutableStateOf(false) }

    fun finishFileChooser(uris: Array<Uri>? = null) {
        val callback = fileChooserCallback
        fileChooserCallback = null
        callback?.onReceiveValue(uris)
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        filePickerInFlight = false
        if (fileChooserCallback != null) {
            val uris = result.data?.takeIf { result.resultCode == Activity.RESULT_OK }
                ?.selectedDocuments(context)
            finishFileChooser(uris)
        }
    }

    var redirectPolicy by remember { mutableStateOf(RedirectPolicy.ALLOW_ALL) }
    val refreshNameList = remember { mutableStateListOf<String>() }
    var redirectRequest by remember { mutableStateOf<RedirectRequest?>(null) }
    var showLogSheet by remember { mutableStateOf(false) }
    var injectedJsSourceUrl by remember { mutableStateOf<String?>(null) }

    var showFavoriteSheet by remember { mutableStateOf(false) }
    var favoriteTitle by remember { mutableStateOf("") }
    var favoriteGroup by remember { mutableStateOf("") }

    val content by viewModel.contentState.collectAsStateWithLifecycle()
    val analyzeUrl by viewModel.urlState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeakingState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fallbackUserAgent = settings.userAgent

    fun hideCustomView() {
        val currentCustomView = customView ?: return
        (currentCustomView.parent as? ViewGroup)?.removeView(currentCustomView)
        customView = null
        customViewCallback = null
        activity?.keepScreenOn(false)
        activity?.toggleSystemBar(settings.showStatusBar)
    }

    val controllerCallbacks = remember {
        RssReadWebControllerCallbacks(
            onProgressChanged = { webProgress = it },
            onPageTitleResolved = { resolved ->
                pageTitle = resolved.ifBlank { defaultTopBarTitle }
            },
            onShowCustomView = { view, callback ->
                if (view == null) {
                    callback?.onCustomViewHidden()
                } else if (customView != null) {
                    callback?.onCustomViewHidden()
                } else {
                    customView = view
                    customViewCallback = callback
                    activity?.keepScreenOn(true)
                    activity?.toggleSystemBar(false)
                }
            },
            onHideCustomView = { hideCustomView() },
            onShowFileChooser = { callback, params ->
                if (filePickerInFlight ||
                    (params.mode != FileChooserParams.MODE_OPEN &&
                        params.mode != FileChooserParams.MODE_OPEN_MULTIPLE)
                ) {
                    false
                } else {
                    fileChooserCallback = callback
                    filePickerInFlight = true
                    try {
                        filePicker.launch(params.openDocumentIntent())
                        true
                    } catch (_: ActivityNotFoundException) {
                        fileChooserCallback = null
                        filePickerInFlight = false
                        context.toastOnUi(R.string.can_not_open)
                        false
                    }
                }
            },
            onPageStarted = { finishFileChooser() },
            navigateToArticles = { sortUrl, targetOrigin ->
                onOpenArticles(sortUrl, targetOrigin)
            },
            onAskRedirect = { from, to, onResult ->
                redirectRequest = RedirectRequest(from, to, onResult)
            },
            onCloseRequested = onBackClick,
            isFullscreenProvider = { customView != null },
        )
    }

    LaunchedEffect(origin, link, openUrl, title, startPage) {
        viewModel.initData(
            ReadRssArgs(
                title = title,
                origin = origin,
                link = link,
                openUrl = openUrl,
                startPage = startPage
            )
        )
    }

    LaunchedEffect(viewModel.rssSource?.redirectPolicy) {
        redirectPolicy = RedirectPolicy.fromString(viewModel.rssSource?.redirectPolicy)
    }

    LaunchedEffect(content, webView, fallbackUserAgent) {
        val body = content ?: return@LaunchedEffect
        val currentWebView = webView ?: return@LaunchedEffect
        // preloadJs 所需的 java/source/cache 接口必须在页面加载前注入，
        // 时序上要等到 initData 完成（hasPreloadJs 确定）之后，否则 startJs 首行会中断
        injectRssReadJsInterfaces(
            webView = currentWebView,
            viewModel = viewModel,
            appCompatActivity = appCompatActivity,
            callbacks = controllerCallbacks,
            injectedSourceUrl = injectedJsSourceUrl,
            onInjected = { injectedJsSourceUrl = it },
        )
        currentWebView.settings.userAgentString = viewModel.headerMap[AppConst.UA_NAME] ?: fallbackUserAgent
        val article = viewModel.rssArticle
        val url = article?.let {
            NetworkUtils.getAbsoluteURL(it.origin, it.link)
        } ?: origin
        val html = viewModel.clHtml(body)
        if (currentWebView.url != url) {
            if (viewModel.rssSource?.loadWithBaseUrl == true) {
                currentWebView.loadDataWithBaseURL(url, html, "text/html", "utf-8", url)
            } else {
                currentWebView.loadDataWithBaseURL(
                    null,
                    html,
                    "text/html;charset=utf-8",
                    "utf-8",
                    url
                )
            }
        }
    }

    LaunchedEffect(analyzeUrl, webView) {
        val url = analyzeUrl ?: return@LaunchedEffect
        val currentWebView = webView ?: return@LaunchedEffect
        if (currentWebView.url != url.url) {
            CookieManager.applyToWebView(url.url)
            currentWebView.settings.userAgentString = url.getUserAgent()
            currentWebView.loadUrl(url.url, url.headerMap)
        }
    }

    BackHandler {
        when {
            customView != null -> customViewCallback?.onCustomViewHidden() ?: hideCustomView()
            webView?.canGoBack() == true -> {
                val list = webView?.copyBackForwardList() ?: return@BackHandler
                val size = list.size
                if (size == 1) {
                    onBackClick()
                    return@BackHandler
                }
                val currentIndex = list.currentIndex
                val currentItem = list.currentItem
                val currentUrl = currentItem?.originalUrl ?: BLANK_HTML
                val currentTitle = currentItem?.title
                //从后往前找，找到第一个不同链接的页面，计算需要回退多少步 避免刷新后导致返回不灵
                var steps = 1
                for (i in currentIndex - 1 downTo 0) {
                    val item = list.getItemAtIndex(i)
                    val itemTitle = item.title
                    val index = refreshNameList.indexOf(itemTitle)
                    if (index != -1) {
                        refreshNameList.removeAt(index)
                        steps++
                        continue
                    }
                    val itemUrl = item.originalUrl
                    if (itemUrl == BLANK_HTML) {
                        onBackClick()
                        return@BackHandler
                    }
                    if (itemUrl != currentUrl || itemTitle != currentTitle) {
                        break
                    }
                    if (currentUrl == DATA_HTML) {
                        break
                    }
                    steps++
                }
                if (steps == size) {
                    onBackClick()
                    return@BackHandler
                }
                webView?.goBackOrForward(-steps)
            }
            else -> onBackClick()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            customViewCallback?.onCustomViewHidden()
            hideCustomView()
        }
    }

    val isNight = LegadoTheme.isDark

    LaunchedEffect(isNight, webView) {
        val currentWebView = webView ?: return@LaunchedEffect
        currentWebView.applyDayNight(isNight)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            disableContentSampling = true,
            topBar = {
                GlassSmallTopAppBar(
                    title = pageTitle.ifBlank { defaultTopBarTitle },
                    navigationIcon = {
                        TopBarNavigationButton(onClick = onBackClick)
                    },
                    actions = {
                        TopBarActionButton(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            onClick = {
                                if (viewModel.rssSource?.singleUrl == true) {
                                    webView?.reload()
                                } else {
                                    webView?.title?.let { refreshNameList.add(it) }
                                    viewModel.refresh { webView?.reload() }
                                }
                            }
                        )
                        if (!startPage) {
                            TopBarActionButton(
                                imageVector = Icons.Default.Star,
                                contentDescription = stringResource(R.string.favorite),
                                onClick = {
                                    viewModel.addFavorite()
                                    favoriteTitle = viewModel.rssArticle?.title.orEmpty()
                                    favoriteGroup = viewModel.rssArticle?.group.orEmpty()
                                    showFavoriteSheet = true
                                }
                            )
                        }
                        Box {
                        TopBarActionButton(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more_menu),
                            onClick = { showMenu = true }
                        )
                            // 条目动作集中在局部函数里：`buildList` / `map` 的 lambda 不是
                            // @Composable，不能在里面求值文案，也不能在其中分派。
                            fun onMenuAction(id: String) {
                                when {
                                    id == RssReadMenuShare -> {
                                        webView?.url?.let {
                                            context.share(it)
                                        } ?: viewModel.rssArticle?.let {
                                            context.share(it.link)
                                        } ?: context.toastOnUi(R.string.null_url)
                                    }

                                    id == RssReadMenuPlayVideo ->
                                        viewModel.rssArticle?.let(onOpenVideoPlayer)

                                    id == RssReadMenuReadAloud -> {
                                        if (isSpeaking) {
                                            viewModel.stopReadAloud()
                                        } else {
                                            webView?.evaluateJavascript(
                                                "document.documentElement.outerHTML"
                                            ) {
                                                val html = StringEscapeUtils.unescapeJson(it)
                                                    .replace("^\"|\"$".toRegex(), "")
                                                viewModel.readAloud(Jsoup.parse(html).text())
                                            }
                                        }
                                    }

                                    id == RssReadMenuLogin -> context.startActivity(
                                        MainActivity.createSourceLoginIntent(
                                            context,
                                            SourceLoginType.RssSource,
                                            viewModel.rssSource?.sourceUrl,
                                        )
                                    )

                                    id.startsWith(RssReadMenuRedirectPrefix) -> {
                                        val policy = RedirectPolicy.entries.firstOrNull {
                                            "$RssReadMenuRedirectPrefix${it.name}" == id
                                        }
                                        if (policy != null) {
                                            viewModel.rssSource?.let { source ->
                                                viewModel.updateRssSourceRedirectPolicy(
                                                    source.sourceUrl,
                                                    policy.name
                                                )
                                            }
                                            redirectPolicy = policy
                                            context.toastOnUi("重定向策略已更新")
                                        }
                                    }

                                    id == RssReadMenuLog -> showLogSheet = true

                                    id == RssReadMenuOpenInBrowser ->
                                        webView?.url?.let { context.openUrl(it) }
                                            ?: context.toastOnUi("url null")
                                }
                            }

                            val shareLabel = stringResource(R.string.share)
                            val playVideoLabel = stringResource(R.string.play_video)
                            val readAloudLabel = if (isSpeaking) {
                                stringResource(R.string.aloud_stop)
                            } else {
                                stringResource(R.string.read_aloud)
                            }
                            val loginLabel = stringResource(R.string.login)
                            val redirectPolicyLabel = stringResource(R.string.redirect_policy)
                            val logLabel = stringResource(R.string.log)
                            val openInBrowserLabel = stringResource(R.string.open_in_browser)
                            val redirectActions = RedirectPolicy.entries.map { policy ->
                                AppAction(
                                    id = "$RssReadMenuRedirectPrefix${policy.name}",
                                    label = policy.title(),
                                    selected = policy == redirectPolicy,
                                )
                            }.toImmutableList()
                            AppActionMenu(
                                groups = persistentListOf(
                                    AppActionGroup(
                                        id = RssReadMenuGroup,
                                        actions = buildList {
                                            add(
                                                AppAction(
                                                    id = RssReadMenuShare,
                                                    label = shareLabel,
                                                    icon = Icons.Default.Share,
                                                )
                                            )
                                            if (viewModel.rssArticle?.type == 2) {
                                                add(
                                                    AppAction(
                                                        id = RssReadMenuPlayVideo,
                                                        label = playVideoLabel,
                                                        icon = Icons.Default.PlayArrow,
                                                    )
                                                )
                                            }
                                            add(
                                                AppAction(
                                                    id = RssReadMenuReadAloud,
                                                    label = readAloudLabel,
                                                    icon = if (isSpeaking) {
                                                        Icons.Default.Stop
                                                    } else {
                                                        Icons.AutoMirrored.Filled.VolumeUp
                                                    },
                                                )
                                            )
                                            if (!viewModel.rssSource?.loginUrl.isNullOrBlank()) {
                                                add(
                                                    AppAction(
                                                        id = RssReadMenuLogin,
                                                        label = loginLabel,
                                                        icon = Icons.AutoMirrored.Filled.Login,
                                                    )
                                                )
                                            }
                                            add(
                                                AppAction(
                                                    id = RssReadMenuRedirectPolicy,
                                                    label = redirectPolicyLabel,
                                                    children = redirectActions,
                                                )
                                            )
                                            add(
                                                AppAction(
                                                    id = RssReadMenuLog,
                                                    label = logLabel,
                                                )
                                            )
                                            add(
                                                AppAction(
                                                    id = RssReadMenuOpenInBrowser,
                                                    label = openInBrowserLabel,
                                                    icon = Icons.Default.OpenInBrowser,
                                                )
                                            )
                                        }.toImmutableList(),
                                    ),
                                ),
                                onAction = { id -> onMenuAction(id) },
                                modifier = Modifier.matchParentSize(),
                                embeddedExpanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                trigger = {},
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!startPage || content != null) {
                    VisibleWebViewCompose(
                        modifier = Modifier.fillMaxSize(),
                        onCreated = { createdWebView ->
                            webView = createdWebView
                            injectedJsSourceUrl = null
                            configureRssReadWebView(
                                webView = createdWebView,
                                context = context,
                                activity = activity,
                                appCompatActivity = appCompatActivity,
                                viewModel = viewModel,
                                initialTitle = title,
                                redirectPolicyProvider = { redirectPolicy },
                                callbacks = controllerCallbacks,
                            )
                        },
                        onDestroyed = {
                            finishFileChooser()
                            webView = null
                        },
                    )
                }
                if (webProgress in 0..99) {
                    AppLinearProgressIndicator(
                        progress = webProgress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
            }
        }
        }

        customView?.let { view ->
            key(view) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(1f),
                    factory = {
                        (view.parent as? ViewGroup)?.removeView(view)
                        view
                    }
                )
            }
        }
    }

    FavoriteEditSheet(
        show = showFavoriteSheet,
        titleValue = favoriteTitle,
        groupValue = favoriteGroup,
        onTitleChange = { favoriteTitle = it },
        onGroupChange = { favoriteGroup = it },
        onDismissRequest = { showFavoriteSheet = false },
        onSave = {
            viewModel.updateFavorite(favoriteTitle, favoriteGroup)
            showFavoriteSheet = false
        },
        onDelete = {
            viewModel.delFavorite()
            showFavoriteSheet = false
        }
    )

    redirectRequest?.let { request ->
        AppAlertDialog(
            show = true,
            onDismissRequest = {
                request.onResult(false)
                redirectRequest = null
            },
            title = "重定向请求",
            text = "是否允许页面跳转？\n\n来源：${request.fromUrl ?: "未知"}\n目标：${request.toUrl}",
            confirmText = "允许",
            onConfirm = {
                request.onResult(true)
                redirectRequest = null
            },
            dismissText = "拒绝",
            onDismiss = {
                request.onResult(false)
                redirectRequest = null
            },
        )
    }

    AppLogSheet(
        show = showLogSheet,
        onDismissRequest = { showLogSheet = false }
    )
}

private data class RedirectRequest(
    val fromUrl: String?,
    val toUrl: String,
    val onResult: (Boolean) -> Unit,
)

private fun FileChooserParams.openDocumentIntent(): Intent {
    val mimeTypes = acceptTypes.filter { it.isNotBlank() }.map { acceptType ->
        val type = acceptType.trim().lowercase()
        if (type.startsWith('.')) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.drop(1)) ?: "*/*"
        } else {
            type
        }
    }.distinct()
    return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = mimeTypes.singleOrNull() ?: "*/*"
        if (mimeTypes.size > 1 && "*/*" !in mimeTypes) {
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        }
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mode == FileChooserParams.MODE_OPEN_MULTIPLE)
    }
}

private fun Intent.selectedDocuments(context: Context): Array<Uri>? {
    val files = buildList {
        data?.let(::add)
        clipData?.let { clip ->
            repeat(clip.itemCount) { index -> clip.getItemAt(index).uri?.let(::add) }
        }
    }.distinct()
    // Only pass documents for which the picker granted this app read access.
    return files.takeIf { selected ->
        selected.isNotEmpty() && selected.all { uri ->
            DocumentsContract.isDocumentUri(context, uri) &&
                context.checkUriPermission(
                    uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION
                ) == PackageManager.PERMISSION_GRANTED
        }
    }?.toTypedArray()
}

@Composable
private fun FavoriteEditSheet(
    show: Boolean,
    titleValue: String,
    groupValue: String,
    onTitleChange: (String) -> Unit,
    onGroupChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.favorite),
        endAction = {
            MediumTonalButton(
                onClick = onDelete,
                icon = Icons.Default.CleaningServices,
                contentDescription = stringResource(R.string.delete)
            )
        }
    ) {

        Column {
            AppTextField(
                value = titleValue,
                onValueChange = onTitleChange,
                label = stringResource(R.string.title),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = groupValue,
                onValueChange = onGroupChange,
                label = stringResource(R.string.group_name),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ConfirmDismissButtonsRow(
            modifier = Modifier.fillMaxWidth(),
            onDismiss = onDismissRequest,
            onConfirm = onSave,
            dismissText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.action_save)
        )
    }
}

private const val BLANK_HTML = "about:blank"
private const val DATA_HTML = "data:text/html;charset=utf-8;base64,"
