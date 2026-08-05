package io.legado.app.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.webkit.SslErrorHandler
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
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.help.http.CookieManager
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.SourceVerificationHelp
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.openUrl
import android.webkit.CookieManager as AndroidCookieManager

@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
@Composable
fun WebViewRouteScreen(
    intent: Intent,
    viewModel: WebViewModel,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var cloudflareChallenge by remember { mutableStateOf(false) }

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
            customView != null -> customViewCallback?.onCustomViewHidden()
            webView?.canGoBack() == true && (webView?.copyBackForwardList()?.size
                ?: 0) > 1 -> webView?.goBack()

            else -> finishScreen()
        }
    }

    AppScaffold(
        topBar = {
            GlassTopAppBar(
                title = viewModel.sourceName.ifBlank {
                    intent.getStringExtra("title") ?: context.getString(R.string.loading)
                },
                navigationIcon = { TopBarNavigationButton(onClick = ::finishScreen) },
                actions = {
                    TopBarActionButton(
                        onClick = { webView?.reload() },
                        imageVector = Icons.Default.Refresh,
                        contentDescription = context.getString(R.string.refresh),
                    )
                    TopBarActionButton(
                        onClick = { webView?.url?.let(context::openUrl) },
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = context.getString(R.string.open_in_browser),
                    )
                    TopBarActionButton(
                        onClick = ::finishWithVerification,
                        imageVector = Icons.Default.Check,
                        contentDescription = context.getString(R.string.ok),
                    )
                },
            )
        },
    ) { paddingValues ->
        Box(Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            if (ready) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.apply {
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                domStorageEnabled = true
                                allowContentAccess = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                javaScriptEnabled = true
                                viewModel.headerMap[AppConst.UA_NAME]?.let { userAgentString = it }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean =
                                    request.url.scheme !in setOf("http", "https")

                                override fun onPageFinished(view: WebView, url: String) {
                                    AndroidCookieManager.getInstance().getCookie(url)
                                        ?.let { CookieStore.setCookie(viewModel.sourceOrigin, it) }
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

                                override fun onShowCustomView(
                                    view: View?,
                                    callback: CustomViewCallback?
                                ) {
                                    customView = view; customViewCallback = callback
                                }

                                override fun onHideCustomView() {
                                    customView = null; customViewCallback = null
                                }
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
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }
}
