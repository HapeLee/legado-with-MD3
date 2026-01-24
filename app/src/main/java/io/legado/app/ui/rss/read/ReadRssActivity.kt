package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ActivityRssReadBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.CookieManager
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.Download
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.favorites.RssFavoritesDialog
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.isTrue
import io.legado.app.utils.keepScreenOn
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.setDarkeningAllowed
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.textArray
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.launch
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import splitties.views.bottomPadding
import java.net.URLDecoder

/**
 * rss阅读界面
 */
class ReadRssActivity : VMBaseActivity<ActivityRssReadBinding, ReadRssViewModel>(),
    RssFavoritesDialog.Callback {

    enum class RedirectPolicy {
        ALLOW_ALL,
        ASK_ALWAYS,
        ASK_CROSS_ORIGIN,
        BLOCK_CROSS_ORIGIN,
        BLOCK_ALL,
        ASK_SAME_DOMAIN_BLOCK_CROSS;

        companion object {
            fun fromString(value: String?): RedirectPolicy {
                return entries.find { it.name.equals(value, ignoreCase = true) } ?: ALLOW_ALL
            }
        }

    }

    var redirectPolicy: RedirectPolicy = RedirectPolicy.ALLOW_ALL

    override val binding by viewBinding(ActivityRssReadBinding::inflate)
    override val viewModel by viewModels<ReadRssViewModel>()

    private var starMenuItem: MenuItem? = null
    private var ttsMenuItem: MenuItem? = null
    private var redirectPolicyMenu: MenuItem? = null
    private var isFullScreen = false
    private var customWebViewCallback: WebChromeClient.CustomViewCallback? = null
    private val rssJsExtensions by lazy { RssJsExtensions(this, viewModel.rssSource) }

    fun getSource(): RssSource? {
        return viewModel.rssSource
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.upStarMenuData.observe(this) { upStarMenu() }
        viewModel.upTtsMenuData.observe(this) { upTtsMenu(it) }
        binding.titleBar.title = intent.getStringExtra("title")
        initView()
        initWebView()
        initLiveData()
        viewModel.initData(intent)
        onBackPressedDispatcher.addCallback(this) {
            if (isFullScreen) {
                toggleFullScreen()
            } else {
                if (binding.customWebView.size > 0) {
                    customWebViewCallback?.onCustomViewHidden()
                    return@addCallback
                } else if (binding.webView.canGoBack()
                    && binding.webView.copyBackForwardList().size > 1
                ) {
                    binding.webView.goBack()
                    return@addCallback
                }
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SwitchIntDef")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_read, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        starMenuItem = menu.findItem(R.id.menu_rss_star)
        ttsMenuItem = menu.findItem(R.id.menu_aloud)
        redirectPolicyMenu = menu.findItem(R.id.menu_redirect_policy)
        redirectPolicyMenu?.setOnMenuItemClickListener {
            showRedirectPolicySubMenu()
            true
        }
        upStarMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !viewModel.rssSource?.loginUrl.isNullOrBlank()
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_full_screen -> toggleFullScreen()
            R.id.menu_rss_refresh -> viewModel.refresh {
                binding.webView.reload()
            }

            R.id.menu_rss_star -> {
                viewModel.addFavorite()
                viewModel.rssArticle?.let {
                    showDialogFragment(RssFavoritesDialog(it))
                }
            }
            R.id.menu_share_it -> {
                binding.webView.url?.let {
                    share(it)
                } ?: viewModel.rssArticle?.let {
                    share(it.link)
                } ?: toastOnUi(R.string.null_url)
            }

            R.id.menu_aloud -> readAloud()
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }

            R.id.menu_browser_open -> binding.webView.url?.let {
                openUrl(it)
            } ?: toastOnUi("url null")
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun updateFavorite(title: String?, group: String?) {
        viewModel.rssArticle?.let {
            if (title != null) {
                it.title = title
            }
            if (group != null) {
                it.group = group
            }
        }
        viewModel.updateFavorite()
    }

    override fun deleteFavorite() {
        viewModel.delFavorite()
    }

    @JavascriptInterface
    fun isNightTheme(): Boolean {
        return AppConfig.isNightTheme
    }

    private fun showRedirectPolicySubMenu() {
        val popup = PopupMenu(this, binding.titleBar, Gravity.END)
        popup.menuInflater.inflate(R.menu.menu_redirect_policy_submenu, popup.menu)
        setCurrentRedirectPolicyChecked(popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            handleRedirectPolicySelection(menuItem)
            true
        }
        popup.show()
    }

    private fun setCurrentRedirectPolicyChecked(menu: Menu) {
        val currentPolicy = redirectPolicy
        val menuItemId = when (currentPolicy) {
            RedirectPolicy.ALLOW_ALL -> R.id.menu_redirect_allow_all
            RedirectPolicy.ASK_ALWAYS -> R.id.menu_redirect_ask_always
            RedirectPolicy.ASK_CROSS_ORIGIN -> R.id.menu_redirect_ask_cross_origin
            RedirectPolicy.BLOCK_CROSS_ORIGIN -> R.id.menu_redirect_block_cross_origin
            RedirectPolicy.BLOCK_ALL -> R.id.menu_redirect_block_all
            RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS -> R.id.menu_redirect_ask_same_domain_block_cross
        }
        menu.findItem(menuItemId)?.isChecked = true
    }

    private fun handleRedirectPolicySelection(menuItem: MenuItem) {
        val selectedPolicy = when (menuItem.itemId) {
            R.id.menu_redirect_allow_all -> RedirectPolicy.ALLOW_ALL
            R.id.menu_redirect_ask_always -> RedirectPolicy.ASK_ALWAYS
            R.id.menu_redirect_ask_cross_origin -> RedirectPolicy.ASK_CROSS_ORIGIN
            R.id.menu_redirect_block_cross_origin -> RedirectPolicy.BLOCK_CROSS_ORIGIN
            R.id.menu_redirect_block_all -> RedirectPolicy.BLOCK_ALL
            R.id.menu_redirect_ask_same_domain_block_cross -> RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS
            else -> RedirectPolicy.ALLOW_ALL
        }

        updateRedirectPolicy(selectedPolicy)
        toastOnUi("重定向策略已更新")
    }

    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen

        toggleSystemBar(!isFullScreen)

        if (isFullScreen) {
            supportActionBar?.hide()
            toastOnUi("可通过系统手势退出全屏。")
        } else {
            supportActionBar?.show()
        }
    }

    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val insets = windowInsets.getInsets(typeMask)
            view.bottomPadding = insets.bottom
            windowInsets
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun initWebView() {
        //binding.progressBar.fontColor = accentColor
        binding.webView.webChromeClient = CustomWebChromeClient()
        binding.webView.webViewClient = CustomWebViewClient()
        binding.webView.settings.apply {
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            allowContentAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            setDarkeningAllowed(AppConfig.isNightTheme)
        }
        binding.webView.addJavascriptInterface(this, "thisActivity")
        binding.webView.setOnLongClickListener {
            val hitTestResult = binding.webView.hitTestResult
            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                hitTestResult.extra?.let { webPic ->
                    selector(
                        arrayListOf(
                            SelectItem(getString(R.string.action_save), "save"),
                        )
                    ) { _, charSequence, _ ->
                        when (charSequence.value) {
                            "save" -> saveImage(webPic)
                        }
                    }
                    return@setOnLongClickListener true
                }
            }
            return@setOnLongClickListener false
        }
        binding.webView.setDownloadListener { url, _, contentDisposition, _, _ ->
            var fileName = URLUtil.guessFileName(url, contentDisposition, null)
            fileName = URLDecoder.decode(fileName, "UTF-8")
            binding.llView.longSnackbar(fileName, getString(R.string.action_download)) {
                Download.start(this, url, fileName)
            }
        }

    }

    private fun saveImage(webPic: String) {
        viewModel.saveImage(webPic)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initLiveData() {
        viewModel.contentLiveData.observe(this) { content ->
            viewModel.rssArticle?.let {
                if (viewModel.rssSource != null) {
                    redirectPolicy = RedirectPolicy.fromString(viewModel.rssSource?.redirectPolicy)
                }
                upJavaScriptEnable()
                val url = NetworkUtils.getAbsoluteURL(it.origin, it.link)
                val html = viewModel.clHtml(content)
                binding.webView.settings.userAgentString =
                    viewModel.headerMap[AppConst.UA_NAME] ?: AppConfig.userAgent
                if (viewModel.rssSource?.loadWithBaseUrl == true) {
                    binding.webView
                        .loadDataWithBaseURL(url, html, "text/html", "utf-8", url)//不想用baseUrl进else
                } else {
                    binding.webView
                        .loadDataWithBaseURL(null, html, "text/html;charset=utf-8", "utf-8", url)
                }
            }
        }
        viewModel.urlLiveData.observe(this) {
            upJavaScriptEnable()
            CookieManager.applyToWebView(it.url)
            binding.webView.settings.userAgentString = it.getUserAgent()
            binding.webView.loadUrl(it.url, it.headerMap)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun upJavaScriptEnable() {
        if (viewModel.rssSource?.enableJs == true) {
            binding.webView.settings.javaScriptEnabled = true
        }
    }

    fun updateRedirectPolicy(policy: RedirectPolicy) {
        viewModel.rssSource?.let { source ->
            viewModel.updateRssSourceRedirectPolicy(source.sourceUrl, policy.name)
            redirectPolicy = policy
        }
    }

    private fun upStarMenu() {
        starMenuItem?.isVisible = viewModel.rssArticle != null
        if (viewModel.rssStar != null) {
            starMenuItem?.setIcon(R.drawable.ic_star)
            starMenuItem?.setTitle(R.string.in_favorites)
        } else {
            starMenuItem?.setIcon(R.drawable.ic_star_border)
            starMenuItem?.setTitle(R.string.out_favorites)
        }
        //starMenuItem?.icon?.setTintMutate(primaryTextColor)
    }

    private fun upTtsMenu(isPlaying: Boolean) {
        lifecycleScope.launch {
            if (isPlaying) {
                ttsMenuItem?.setIcon(R.drawable.ic_stop_black_24dp)
                ttsMenuItem?.setTitle(R.string.aloud_stop)
            } else {
                ttsMenuItem?.setIcon(R.drawable.ic_volume_up)
                ttsMenuItem?.setTitle(R.string.read_aloud)
            }
            //ttsMenuItem?.icon?.setTintMutate(primaryTextColor)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun readAloud() {
        if (viewModel.tts?.isSpeaking == true) {
            viewModel.tts?.stop()
            upTtsMenu(false)
        } else {
            binding.webView.settings.javaScriptEnabled = true
            binding.webView.evaluateJavascript("document.documentElement.outerHTML") {
                val html = StringEscapeUtils.unescapeJson(it)
                    .replace("^\"|\"$".toRegex(), "")
                viewModel.readAloud(
                    Jsoup.parse(html)
                        .textArray()
                        .joinToString("\n")
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }

    inner class CustomWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            binding.progressBar.setProgress(newProgress)
            binding.progressBar.gone(newProgress == 100)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            binding.llView.invisible()
            binding.customWebView.addView(view)
            customWebViewCallback = callback
            keepScreenOn(true)
            toggleSystemBar(false)
        }

        override fun onHideCustomView() {
            binding.customWebView.removeAllViews()
            binding.llView.visible()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            keepScreenOn(false)
            toggleSystemBar(true)
        }
    }

    inner class CustomWebViewClient : WebViewClient() {
        private var lastUrl: String? = null
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val targetUri = request.url
            if (targetUri.scheme == "legado" || targetUri.scheme == "yuedu") {
                return shouldOverrideUrlLoading(targetUri)
            }
            val currentUrl = lastUrl ?: view.url
            val targetUrl = request.url.toString()
            lastUrl = targetUrl
            if (!request.isForMainFrame) return false
            if (handleRedirect(view, currentUrl, targetUrl)) {
                return true
            }

            return shouldOverrideUrlLoading(request.url)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "KotlinRedundantDiagnosticSuppress")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val targetUri = url.toUri()
            if (targetUri.scheme == "legado" || targetUri.scheme == "yuedu") {
                return shouldOverrideUrlLoading(targetUri)
            }
            val currentUrl = lastUrl ?: view.url
            val targetUrl = url
            lastUrl = targetUrl
            if (handleRedirect(view, currentUrl, targetUrl)) {
                return true
            }

            return shouldOverrideUrlLoading(url.toUri())
        }

        private fun handleRedirect(view: WebView, fromUrl: String?, toUrl: String): Boolean {
            val fromHost = fromUrl?.toUri()?.host
            val toHost = toUrl.toUri().host
            val crossOrigin = fromHost != null && toHost != null && fromHost != toHost

            return when (redirectPolicy) {
                RedirectPolicy.ALLOW_ALL -> false
                RedirectPolicy.BLOCK_ALL -> {
                    toastOnUi("已阻止重定向")
                    true
                }
                RedirectPolicy.ASK_ALWAYS -> {
                    askUser(fromUrl, toUrl) { if (it) view.loadUrl(toUrl) }
                    true
                }
                RedirectPolicy.ASK_CROSS_ORIGIN -> {
                    if (crossOrigin) {
                        askUser(fromUrl, toUrl) { if (it) view.loadUrl(toUrl) }
                        true
                    } else {
                        false
                    }
                }
                RedirectPolicy.BLOCK_CROSS_ORIGIN -> {
                    if (crossOrigin) {
                        toastOnUi("已阻止跨域重定向")
                        true
                    } else {
                        false
                    }
                }
                RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS -> {
                    if (crossOrigin) {
                        toastOnUi("已阻止域外跳转")
                        true
                    } else {
                        askUser(fromUrl, toUrl) { if (it) view.loadUrl(toUrl) }
                        true
                    }
                }
            }
        }

        private fun askUser(fromUrl: String?, toUrl: String, onResult: (Boolean) -> Unit) {
            AlertDialog.Builder(this@ReadRssActivity)
                .setTitle("重定向请求")
                .setMessage("是否允许页面跳转？\n\n来源：${fromUrl ?: "未知"}\n目标：$toUrl")
                .setPositiveButton("允许") { _, _ -> onResult(true) }
                .setNegativeButton("拒绝") { _, _ -> onResult(false) }
                .setCancelable(true)
                .show()
        }

        private fun shouldOverrideUrlLoading(url: Uri): Boolean {
            val source = viewModel.rssSource
            val js = source?.shouldOverrideUrlLoading
            if (!js.isNullOrBlank()) {
                val t = SystemClock.uptimeMillis()
                val result = kotlin.runCatching {
                    runScriptWithContext(lifecycleScope.coroutineContext) {
                        source.evalJS(js) {
                            put("java", rssJsExtensions)
                            put("url", url.toString())
                        }.toString()
                    }
                }.onFailure {
                    AppLog.put("${source.getTag()}: url跳转拦截js出错", it)
                }.getOrNull()
                if (SystemClock.uptimeMillis() - t > 30) {
                    AppLog.put("${source.getTag()}: url跳转拦截js执行耗时过长")
                }
                if (result.isTrue()) {
                    return true
                }
            }
            when (url.scheme) {
                "http", "https", "jsbridge" -> {
                    return false
                }

                "legado", "yuedu" -> {
                    startActivity<OnLineImportActivity> {
                        data = url
                    }
                    return true
                }

                else -> {
                    binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                        openUrl(url)
                    }
                    return true
                }
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            view.title?.let { title ->
                if (title != url && title != view.url && title.isNotBlank() && url != "about:blank") {
                    binding.titleBar.title = title
                } else {
                    binding.titleBar.title = intent.getStringExtra("title")
                }
            }
            viewModel.rssSource?.injectJs?.let {
                if (it.isNotBlank()) {
                    view.evaluateJavascript(it, null)
                }
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.proceed()
        }
    }

    companion object {
        fun start(context: Context, title: String?, url: String, origin: String) {
            context.startActivity<ReadRssActivity> {
                putExtra("title", title ?: "")
                putExtra("origin", origin)
                putExtra("openUrl", url)
            }
        }

        private val webCookieManager by lazy { android.webkit.CookieManager.getInstance() }
    }

}
