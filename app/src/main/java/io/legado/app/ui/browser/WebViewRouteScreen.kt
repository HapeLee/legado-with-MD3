package io.legado.app.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.help.http.CookieManager
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.SourceVerificationHelp
import io.legado.app.model.Download
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.rss.read.VisibleWebViewCompose
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.topbar.GlassSmallTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import java.net.URLDecoder
import android.webkit.CookieManager as AndroidCookieManager

@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
@Composable
fun WebViewRouteScreen(
    intent: Intent,
    viewModel: WebViewModel,
    onFinish: () -> Unit,
    onImportBookSource: (String) -> Unit,
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var cloudflareChallenge by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var imageUrlToSave by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<Pair<String, String>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val loadingText = stringResource(R.string.loading)
    val refreshDescription = stringResource(R.string.refresh)
    val openInBrowserDescription = stringResource(R.string.open_in_browser)
    val copyUrlDescription = stringResource(R.string.copy_url)
    val confirmDescription = stringResource(R.string.ok)
    val selectOperationTitle = stringResource(R.string.select_operation)
    val saveActionText = stringResource(R.string.action_save)
    val downloadActionText = stringResource(R.string.action_download)

    LaunchedEffect(intent) {
        viewModel.initData(intent) { ready = true }
    }

    fun finishScreen() {
        if (viewModel.sourceVerificationEnable) {
            SourceVerificationHelp.checkResult(viewModel.sourceOrigin)
        }
        onFinish()
    }

    fun finishWithVerification() {
        val currentWebView = webView ?: return finishScreen()
        viewModel.saveVerificationResult(currentWebView, ::finishScreen)
    }

    BackHandler {
        when {
            webView?.canGoBack() == true && (webView?.copyBackForwardList()?.size
                ?: 0) > 1 -> webView?.goBack()

            else -> finishScreen()
        }
    }

    AppScaffold(
        // WebView 内容不能作为 haze 模糊采样源，否则会被反复重采样导致闪烁（与 RSS 页一致）
        disableHazeSource = true,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassSmallTopAppBar(
                title = pageTitle ?: viewModel.sourceName.ifBlank {
                    intent.getStringExtra("title") ?: loadingText
                },
                navigationIcon = { TopBarNavigationButton(onClick = ::finishScreen) },
                actions = {
                    TopBarActionButton(
                        onClick = { webView?.reload() },
                        imageVector = Icons.Default.Refresh,
                        contentDescription = refreshDescription,
                    )
                    TopBarActionButton(
                        onClick = {
                            val currentUrl = webView?.url
                            val externalUrl = currentUrl
                                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                                ?: viewModel.baseUrl
                            context.openUrl(externalUrl)
                        },
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = openInBrowserDescription,
                    )
                    TopBarActionButton(
                        onClick = {
                            val currentUrl = webView?.url.orEmpty()
                                .takeIf { it.isNotBlank() }
                                ?: viewModel.baseUrl
                            context.sendToClip(currentUrl)
                        },
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = copyUrlDescription,
                    )
                    TopBarActionButton(
                        onClick = ::finishWithVerification,
                        imageVector = Icons.Default.Check,
                        contentDescription = confirmDescription,
                    )
                },
            )
        },
    ) { paddingValues ->
        Box(Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            if (ready) {
                VisibleWebViewCompose(
                    modifier = Modifier.fillMaxSize(),
                    onCreated = { createdWebView ->
                        createdWebView.apply {
                            // Keep the verification page in an opaque WebView layer. A transparent WebView is
                            // appropriate for RSS text, but Chromium repeatedly composites it during challenge
                            // animations and causes the unstable flashing reported for manga sources.
                            setBackgroundColor(android.graphics.Color.WHITE)
                            settings.apply {
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                domStorageEnabled = true
                                allowContentAccess = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                javaScriptEnabled = true
                                viewModel.headerMap[AppConst.UA_NAME]?.let {
                                    userAgentString = it
                                }
                            }
                            isClickable = true
                            isFocusable = true
                            isFocusableInTouchMode = true
                            val currentWebView = this
                            AndroidCookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(currentWebView, true)
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean = handleUrl(request.url)

                                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    url: String,
                                ): Boolean = handleUrl(Uri.parse(url))

                                private fun handleUrl(uri: Uri): Boolean {
                                    return when (uri.scheme) {
                                        "http", "https" -> false
                                        "legado", "yuedu" -> {
                                            val importUrl = uri.getQueryParameter("src")
                                            val isBookSourceImport = uri.path == "/bookSource" ||
                                                    (uri.path == "/importonline" &&
                                                            uri.host.equals("booksource", true))
                                            if (isBookSourceImport && !importUrl.isNullOrBlank()) {
                                                onImportBookSource(importUrl)
                                            } else {
                                                context.startActivity(
                                                    Intent(
                                                        context,
                                                        OnLineImportActivity::class.java
                                                    )
                                                        .setData(uri)
                                                )
                                            }
                                            true
                                        }

                                        else -> {
                                            context.openUrl(uri)
                                            true
                                        }
                                    }
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    pageTitle = view.title
                                        ?.takeIf { it.isNotBlank() && it != url && it != view.url }
                                    AndroidCookieManager.getInstance().getCookie(url)
                                        ?.let { CookieStore.setCookie(url, it) }
                                    if (viewModel.sourceVerificationEnable) view.evaluateJavascript(
                                        "!!window._cf_chl_opt"
                                    ) {
                                        if (it == "true") {
                                            cloudflareChallenge = true
                                        } else if (cloudflareChallenge) {
                                            finishWithVerification()
                                        }
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: android.net.http.SslError?
                                ) {
                                    handler?.proceed()
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            setOnLongClickListener {
                                val result = hitTestResult
                                if (result.type == WebView.HitTestResult.IMAGE_TYPE ||
                                    result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                ) {
                                    imageUrlToSave = result.extra
                                    imageUrlToSave != null
                                } else false
                            }
                            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                                val encodedName = URLUtil.guessFileName(
                                    url,
                                    contentDisposition,
                                    mimeType,
                                )
                                val fileName = runCatching {
                                    URLDecoder.decode(encodedName, "UTF-8")
                                }.getOrDefault(encodedName)
                                pendingDownload = url to fileName
                            }
                            CookieManager.applyToWebView(viewModel.baseUrl)
                            val html = viewModel.html
                            if (html.isNullOrEmpty()) loadUrl(
                                viewModel.baseUrl,
                                viewModel.headerMap
                            )
                            else loadDataWithBaseURL(
                                viewModel.baseUrl,
                                html,
                                "text/html",
                                "utf-8",
                                viewModel.baseUrl
                            )
                            webView = this
                        }
                    },
                )
            }
            if (progress in 0..99) AppLinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    OptionSheet(
        show = imageUrlToSave != null,
        onDismissRequest = { imageUrlToSave = null },
        title = selectOperationTitle,
    ) {
        OptionCard(
            icon = Icons.Default.Save,
            text = saveActionText,
            onClick = {
                val imageUrl = imageUrlToSave
                imageUrlToSave = null
                viewModel.saveImage(imageUrl)
            },
        )
    }
    val download = pendingDownload
    LaunchedEffect(download) {
        download ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = download.second,
            actionLabel = downloadActionText,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
            Download.start(context, download.first, download.second)
        }
        if (pendingDownload == download) pendingDownload = null
    }
}
