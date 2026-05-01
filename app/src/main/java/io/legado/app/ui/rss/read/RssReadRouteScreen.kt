@file:Suppress("DEPRECATION")

package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.CookieManager
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.Download
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.isTrue
import io.legado.app.utils.keepScreenOn
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.setDarkeningAllowed
import io.legado.app.utils.share
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import java.net.URLDecoder
import org.koin.androidx.compose.koinViewModel

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssReadRouteScreen(
    title: String?,
    origin: String,
    link: String?,
    openUrl: String?,
    onBackClick: () -> Unit,
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
    var showRedirectMenu by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember {
        mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
    }

    var redirectPolicy by remember { mutableStateOf(RedirectPolicy.ALLOW_ALL) }

    var showFavoriteSheet by remember { mutableStateOf(false) }
    var favoriteTitle by remember { mutableStateOf("") }
    var favoriteGroup by remember { mutableStateOf("") }

    val content by viewModel.contentState.collectAsStateWithLifecycle()
    val analyzeUrl by viewModel.urlState.collectAsStateWithLifecycle()
    val rssStar by viewModel.rssStarState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeakingState.collectAsStateWithLifecycle()
    val fallbackUserAgent = OtherConfig.userAgent

    fun hideCustomView() {
        val currentCustomView = customView ?: return
        (currentCustomView.parent as? ViewGroup)?.removeView(currentCustomView)
        customView = null
        customViewCallback = null
        activity?.keepScreenOn(false)
        activity?.toggleSystemBar(AppConfig.showStatusBar)
    }

    LaunchedEffect(origin, link, openUrl, title) {
        viewModel.initData(
            ReadRssArgs(
                title = title,
                origin = origin,
                link = link,
                openUrl = openUrl
            )
        )
    }

    LaunchedEffect(viewModel.rssSource?.redirectPolicy) {
        redirectPolicy = RedirectPolicy.fromString(viewModel.rssSource?.redirectPolicy)
    }

    LaunchedEffect(content, webView, fallbackUserAgent) {
        val body = content ?: return@LaunchedEffect
        val currentWebView = webView ?: return@LaunchedEffect
        currentWebView.settings.userAgentString = viewModel.headerMap[AppConst.UA_NAME] ?: fallbackUserAgent
        val article = viewModel.rssArticle ?: return@LaunchedEffect
        val url = NetworkUtils.getAbsoluteURL(article.origin, article.link)
        val html = viewModel.clHtml(body)
        if (viewModel.rssSource?.loadWithBaseUrl == true) {
            currentWebView.loadDataWithBaseURL(url, html, "text/html", "utf-8", url)
        } else {
            currentWebView.loadDataWithBaseURL(null, html, "text/html;charset=utf-8", "utf-8", url)
        }
    }

    LaunchedEffect(analyzeUrl, webView) {
        val url = analyzeUrl ?: return@LaunchedEffect
        val currentWebView = webView ?: return@LaunchedEffect
        CookieManager.applyToWebView(url.url)
        currentWebView.settings.userAgentString = url.getUserAgent()
        currentWebView.loadUrl(url.url, url.headerMap)
    }

    BackHandler {
        when {
            customView != null -> customViewCallback?.onCustomViewHidden() ?: hideCustomView()
            webView?.canGoBack() == true && (webView?.copyBackForwardList()?.size ?: 0) > 1 -> webView?.goBack()
            else -> onBackClick()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            customViewCallback?.onCustomViewHidden()
            hideCustomView()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle.ifBlank { defaultTopBarTitle },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        TopBarNavigationButton(onClick = onBackClick)
                    },
                    actions = {
                        TopBarActionButton(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            onClick = { viewModel.refresh { webView?.reload() } }
                        )
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
                        Box {
                            TopBarActionButton(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                onClick = { showMenu = true }
                            )
                            RoundDropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) { dismiss ->
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.share),
                                    leadingIcon = { MenuItemIcon(Icons.Default.Share) },
                                    onClick = {
                                        dismiss()
                                        webView?.url?.let {
                                            context.share(it)
                                        } ?: viewModel.rssArticle?.let {
                                            context.share(it.link)
                                        } ?: context.toastOnUi(R.string.null_url)
                                    }
                                )
                                RoundDropdownMenuItem(
                                    text = if (isSpeaking) stringResource(R.string.aloud_stop) else stringResource(
                                        R.string.read_aloud
                                    ),
                                    leadingIcon = {
                                        MenuItemIcon(if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp)
                                    },
                                    onClick = {
                                        dismiss()
                                        if (isSpeaking) {
                                            viewModel.stopReadAloud()
                                        } else {
                                            webView?.evaluateJavascript("document.documentElement.outerHTML") {
                                                val html = StringEscapeUtils.unescapeJson(it)
                                                    .replace("^\"|\"$".toRegex(), "")
                                                viewModel.readAloud(
                                                    Jsoup.parse(html).text()
                                                )
                                            }
                                        }
                                    }
                                )
                                if (!viewModel.rssSource?.loginUrl.isNullOrBlank()) {
                                    RoundDropdownMenuItem(
                                        text = stringResource(R.string.login),
                                        leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                                        onClick = {
                                            dismiss()
                                            context.startActivity<SourceLoginActivity> {
                                                putExtra("type", "rssSource")
                                                putExtra("key", viewModel.rssSource?.sourceUrl)
                                            }
                                        }
                                    )
                                }
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.redirect_policy),
                                    onClick = { showRedirectMenu = true }
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.open_in_browser),
                                    leadingIcon = { MenuItemIcon(Icons.Default.OpenInBrowser) },
                                    onClick = {
                                        dismiss()
                                        webView?.url?.let { context.openUrl(it) } ?: context.toastOnUi("url null")
                                    }
                                )
                            }
                            RoundDropdownMenu(
                                expanded = showRedirectMenu,
                                onDismissRequest = { showRedirectMenu = false }
                            ) { dismiss ->
                                RedirectPolicy.entries.forEach { policy ->
                                    RoundDropdownMenuItem(
                                        text = policy.title(),
                                        onClick = {
                                            dismiss()
                                            showMenu = false
                                            viewModel.rssSource?.let { source ->
                                                viewModel.updateRssSourceRedirectPolicy(source.sourceUrl, policy.name)
                                                redirectPolicy = policy
                                            }
                                            context.toastOnUi("重定向策略已更新")
                                        },
                                        trailingIcon = {
                                            if (policy == redirectPolicy) {
                                                Icon(Icons.Default.Check, null)
                                            }
                                        }
                                    )
                                }
                            }
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
                VisibleWebViewCompose(
                    modifier = Modifier.fillMaxSize(),
                    onCreated = { createdWebView ->
                        webView = createdWebView
                        configureRssReadWebView(
                            webView = createdWebView,
                            context = context,
                            activity = activity,
                            appCompatActivity = appCompatActivity,
                            viewModel = viewModel,
                            initialTitle = title,
                            redirectPolicyProvider = { redirectPolicy },
                            callbacks = RssReadWebControllerCallbacks(
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
                                onHideCustomView = { hideCustomView() }
                            )
                        )
                    }
                )
                if (webProgress in 0..99) {
                    LinearProgressIndicator(
                        progress = { webProgress / 100f },
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
            SmallIconButton(
                onClick = onDelete,
                imageVector = Icons.Default.CleaningServices,
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
