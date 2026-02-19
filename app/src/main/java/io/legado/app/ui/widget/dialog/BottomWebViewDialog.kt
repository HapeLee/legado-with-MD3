package io.legado.app.ui.widget.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.size
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppConst.imagePathKey
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.databinding.DialogWebViewBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.WebCacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.JS_INJECTION
import io.legado.app.help.webView.WebJsExtensions.Companion.basicJs
import io.legado.app.help.webView.WebJsExtensions.Companion.nameBasic
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.invisible
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.setLayout
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import io.legado.app.utils.writeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.util.Date

class BottomWebViewDialog() : BottomSheetDialogFragment(R.layout.dialog_web_view),
    WebJsExtensions.Callback {

    companion object {
        const val DEFAULT_JS_INJECT_URL = "https://legado-inject-js.internal"
    }

    constructor(
        sourceKey: String,
        bookType: Int,
        url: String,
        html: String? = null,
        preloadJs: String? = null,
        config: String? = null
    ) : this() {
        arguments = Bundle().apply {
            putString("sourceKey", sourceKey)
            putInt("bookType", bookType)
            putString("url", url)
            putString("html", html)
            putString("preloadJs", preloadJs)
            putString("config", config)
        }
    }

    private val binding by viewBinding(DialogWebViewBinding::bind)
    private val bottomSheet by lazy {
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
    }
    private val behavior by lazy {
        bottomSheet?.let { sheet ->
            BottomSheetBehavior.from(sheet)
        }
    }
    private val displayMetrics by lazy { resources.displayMetrics }
    private val selectImageDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ACache.get().put(imagePathKey, uri.toString())
            saveImage(it.value, uri)
        }
    }

    private lateinit var currentWebView: WebView
    private var source: BaseSource? = null
    private var preloadJs: String? = null
    private var isFullScreen = false
    private var customWebViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originOrientation: Int? = null
    private var needClearHistory = true
    private var isBasicJsInjected = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentWebView = WebView(context)
        initWebViewBaseConfig()
    }


    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    private fun setConfig(config: Config, first: Boolean = false) {
        behavior?.let { behavior ->
            config.state?.let { behavior.state = it }
            config.peekHeight?.let { behavior.peekHeight = it }
            config.isHideable?.let { behavior.isHideable = it }
            config.skipCollapsed?.let { behavior.skipCollapsed = it }
            config.setHalfExpandedRatio?.let { behavior.setHalfExpandedRatio(it) }
            config.setExpandedOffset?.let { behavior.setExpandedOffset(it) }
            config.setFitToContents?.let { behavior.setFitToContents(it) }
            config.isDraggable?.let { behavior.isDraggable = it }
            config.isDraggableOnNestedScroll?.let { behavior.isDraggableOnNestedScroll = it }
            config.significantVelocityThreshold?.let { behavior.significantVelocityThreshold = it }
            config.hideFriction?.let { behavior.hideFriction = it }
            config.maxWidth?.let { behavior.maxWidth = it }
            config.maxHeight?.let { behavior.maxHeight = it }
            config.isGestureInsetBottomIgnored?.let { behavior.isGestureInsetBottomIgnored = it }
            config.setUpdateImportantForAccessibilityOnSiblings?.let {
                behavior.setUpdateImportantForAccessibilityOnSiblings(it)
            }
        }

        config.expandedCornersRadius?.let {
            val radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, it, displayMetrics
            )
            bottomSheet?.let { sheet ->
                if (radius > 0) {
                    sheet.backgroundTintList = null
                    val shapeDrawable =
                        android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 0f
                            cornerRadii = floatArrayOf(
                                radius, radius,
                                radius, radius,
                                0f, 0f,
                                0f, 0f
                            )
                        }
                    sheet.background = shapeDrawable
                    sheet.clipToOutline = true
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        currentWebView.outlineProvider =
                            object : android.view.ViewOutlineProvider() {
                                override fun getOutline(
                                    view: View,
                                    outline: android.graphics.Outline
                                ) {
                                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                                }
                            }
                        currentWebView.clipToOutline = true
                        binding.customWebView.outlineProvider =
                            object : android.view.ViewOutlineProvider() {
                                override fun getOutline(
                                    view: View,
                                    outline: android.graphics.Outline
                                ) {
                                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                                }
                            }
                        binding.customWebView.clipToOutline = true
                    }
                } else {
                    sheet.backgroundTintList = null
                    sheet.background = null
                    sheet.clipToOutline = false
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        currentWebView.outlineProvider = null
                        currentWebView.clipToOutline = false
                        binding.customWebView.outlineProvider = null
                        binding.customWebView.clipToOutline = false
                    }
                }
            }
        }

        dialog?.let { dialog ->
            config.backgroundDimAmount?.let { amount ->
                dialog.window?.setDimAmount(amount)
            }
            config.shouldDimBackground?.let { shouldDim ->
                if (!shouldDim) {
                    dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }
            }
            config.dismissOnTouchOutside?.let { touchOutside ->
                isCancelable = touchOutside
            }
            config.hardwareAccelerated?.let { hwAccel ->
                if (hwAccel) {
                    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                }
            }
        }

        currentWebView.let { webView ->
            config.webViewInitialScale?.let { scale ->
                webView.settings.apply {
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    textZoom = scale
                }
            }
            config.webViewCacheMode?.let { cacheMode ->
                webView.settings.cacheMode = cacheMode
            }
            config.isNestedScrollingEnabled?.let { enabled ->
                webView.isNestedScrollingEnabled = enabled
            } ?: run {
                webView.isNestedScrollingEnabled = false
            }
        }

        bottomSheet?.let { sheet ->
            val params = sheet.layoutParams
            var hasChanged = false
            config.widthPercentage?.let { percentage ->
                if (percentage in 0.0..1.0) {
                    val width = (displayMetrics.widthPixels * percentage).toInt()
                    params.width = width
                    hasChanged = true
                }
            }

            val dialogHeight = config.dialogHeight ?: if (first) -1 else null
            dialogHeight?.let { height ->
                params.height = height
                hasChanged = true
            }
            config.heightPercentage?.let { percentage ->
                if (percentage in 0.0..1.0) {
                    val height = (displayMetrics.heightPixels * percentage).toInt()
                    params.height = height
                    if (config.peekHeight == null) {
                        behavior?.peekHeight = height
                    }
                    if (config.maxHeight == null) {
                        behavior?.maxHeight = height
                    }
                    hasChanged = true
                }
            }
            if (hasChanged) {
                sheet.layoutParams = params
            }
        }

        config.responsiveBreakpoint?.let { breakpoint ->
            val screenWidth = displayMetrics.widthPixels
            if (screenWidth < breakpoint) {
                behavior?.peekHeight = config.peekHeight ?: 300
                config.widthPercentage?.let { percentage ->
                    if (percentage > 0.8f) {
                        val maxWidth = (screenWidth * 0.9).toInt()
                        behavior?.maxWidth = maxWidth
                    }
                }
            } else {
                behavior?.peekHeight = config.peekHeight ?: 400
                config.widthPercentage?.let { percentage ->
                    if (percentage < 0.6f) {
                        bottomSheet?.layoutParams?.width =
                            (screenWidth * percentage).toInt()
                        (bottomSheet?.layoutParams as? FrameLayout.LayoutParams)?.gravity =
                            Gravity.CENTER_HORIZONTAL
                    }
                }
            }
        }

        val scrollNoDraggable = config.scrollNoDraggable ?: if (first) true else null
        scrollNoDraggable?.let {
            if (it) {
                currentWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    behavior?.isDraggable = scrollY <= 0
                }
            } else {
                currentWebView.setOnScrollChangeListener(null)
            }
        }

        val longClickSaveImg = config.longClickSaveImg ?: if (first) true else null
        longClickSaveImg?.let {
            if (it) {
                setLongClickSaveImg()
            } else {
                currentWebView.setOnLongClickListener(null)
            }
        }
    }

    private fun setLongClickSaveImg() {
        currentWebView.setOnLongClickListener {
            val hitTestResult = currentWebView.hitTestResult
            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                hitTestResult.extra?.let { webPic ->
                    requireContext().selector(
                        arrayListOf(
                            SelectItem(getString(R.string.action_save), "save"),
                            SelectItem(getString(R.string.select_folder), "selectFolder")
                        )
                    ) { _, charSequence, _ ->
                        when (charSequence.value) {
                            "save" -> saveImage(webPic)
                            "selectFolder" -> selectSaveFolder(null)
                        }
                    }
                    return@setOnLongClickListener true
                }
            }
            return@setOnLongClickListener false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0)
        binding.webViewContainer.addView(currentWebView)
        lifecycleScope.launch(IO) {
            val args = arguments
            if (args == null) {
                dismiss()
                return@launch
            }
            val sourceKey = args.getString("sourceKey") ?: return@launch
            val url = args.getString("url") ?: return@launch
            kotlin.runCatching {
                // 初始化配置
                args.getString("config")?.let { json ->
                    try {
                        GSON.fromJsonObject<Config>(json).getOrThrow().let { config ->
                            activity?.runOnUiThread {
                                setConfig(config, true)
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.put("config err", e)
                    }
                } ?: run {
                    activity?.runOnUiThread {
                        bottomSheet?.let { sheet ->
                            val layoutParams = sheet.layoutParams
                            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            sheet.layoutParams = layoutParams
                        }

                        currentWebView.overScrollMode = View.OVER_SCROLL_NEVER
                        currentWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            behavior?.isDraggable = (scrollY <= 0)
                        }

                        setLongClickSaveImg()
                    }
                }
                val analyzeUrl =
                    AnalyzeUrl(url, source = source, coroutineContext = coroutineContext)
                val html = args.getString("html") ?: analyzeUrl.getStrResponseAwait().body
                if (html.isNullOrEmpty()) {
                    throw NoStackTraceException("html is NullOrEmpty")
                }
                preloadJs = args.getString("preloadJs")
                val spliceHtml = buildSpliceHtml(html)
                source = appDb.bookSourceDao.getBookSource(sourceKey) ?: run {
                    activity?.toastOnUi("no find bookSource")
                    dismiss()
                    return@launch
                }
                val bookType = args.getInt("bookType", 0)
                activity?.runOnUiThread {
                    initWebView(analyzeUrl.url, spliceHtml, analyzeUrl.headerMap, bookType)
                    if (needClearHistory) {
                        currentWebView.clearHistory()
                        needClearHistory = false
                    }
                }
            }.onFailure {
                activity?.runOnUiThread {
                    currentWebView.loadDataWithBaseURL(
                        url,
                        "<html><body style='color:red;'>加载失败：${it.localizedMessage}</body></html>",
                        "text/html",
                        "utf-8",
                        url
                    )
                }
                AppLog.put("WebView加载失败", it)
            }
        }
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (binding.customWebView.size > 0) {
                    customWebViewCallback?.onCustomViewHidden()
                    return@setOnKeyListener true
                }
                if (currentWebView.canGoBack()) {
                    currentWebView.goBack()
                    return@setOnKeyListener true
                }
                dismiss()
                return@setOnKeyListener true
            }
            false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebViewBaseConfig() {
        currentWebView.settings.apply {
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            javaScriptEnabled = true
            displayZoomControls = false
        }
        currentWebView.isClickable = true
        currentWebView.isFocusable = true
        currentWebView.isFocusableInTouchMode = true
        currentWebView.setOnLongClickListener(null)
    }

    private fun initWebView(
        url: String,
        html: String,
        headerMap: HashMap<String, String>,
        bookType: Int
    ) {
        currentWebView.webViewClient = CustomWebViewClient()
        currentWebView.webChromeClient = CustomWebChromeClient()
        headerMap[AppConst.UA_NAME]?.let {
            currentWebView.settings.userAgentString = it
        }
        currentWebView.addJavascriptInterface(JSInterface(this), nameBasic)
        source?.let { source ->
            (activity as? AppCompatActivity)?.let { currentActivity ->
                val webJsExtensions = WebJsExtensions(
                    source, currentActivity, currentWebView, bookType, callback = this
                )
                currentWebView.addJavascriptInterface(webJsExtensions, nameJava)
            }
            currentWebView.addJavascriptInterface(source, nameSource)
            currentWebView.addJavascriptInterface(WebCacheManager, nameCache)
        }
        currentWebView.loadDataWithBaseURL(url, html, "text/html", "utf-8", url)
    }

    private fun buildSpliceHtml(originalHtml: String): String {
        if (preloadJs.isNullOrEmpty()) return originalHtml
        val headIndex = originalHtml.indexOf("<head", ignoreCase = true)
        return if (headIndex >= 0) {
            val closingHeadIndex = originalHtml.indexOf('>', startIndex = headIndex)
            if (closingHeadIndex >= 0) {
                val insertPos = closingHeadIndex + 1
                StringBuilder(originalHtml).insert(insertPos, buildInjectJsScript()).toString()
            } else {
                buildInjectJsScript() + originalHtml
            }
        } else {
            buildInjectJsScript() + originalHtml
        }
    }

    private fun buildInjectJsScript(): String {
        val basicJs = basicJs
        val preloadJs = this.preloadJs ?: ""
        return """
            <script type="text/javascript">
                // 基础JS
                $basicJs
                // 预加载业务JS
                (() => {
                    $JS_INJECTION
                    $preloadJs
                })();
            </script>
        """.trimIndent()
    }

    private fun saveImage(webPic: String) {
        val path = ACache.get().getAsString(imagePathKey)
        if (path.isNullOrEmpty()) {
            selectSaveFolder(webPic)
        } else {
            saveImage(webPic, path.toUri())
        }
    }

    private fun selectSaveFolder(webPic: String?) {
        val default = arrayListOf<SelectItem<Int>>()
        val path = ACache.get().getAsString(imagePathKey)
        if (!path.isNullOrEmpty()) {
            default.add(SelectItem(path, -1))
        }
        selectImageDir.launch {
            otherActions = default
            value = webPic
        }
    }

    private fun saveImage(webPic: String?, uri: Uri) {
        webPic ?: return
        Coroutine.async(lifecycleScope) {
            val fileName = "${AppConst.fileNameFormat.format(Date(System.currentTimeMillis()))}.jpg"
            val byteArray = webData2bitmap(webPic) ?: throw NoStackTraceException("图片数据为空")
            uri.writeBytes(requireContext(), fileName, byteArray)
        }.onError {
            ACache.get().remove(imagePathKey)
            context?.toastOnUi("保存图片失败:${it.localizedMessage}")
        }.onSuccess {
            context?.toastOnUi("保存成功")
        }
    }

    private suspend fun webData2bitmap(data: String): ByteArray? {
        return if (URLUtil.isValidUrl(data)) {
            okHttpClient.newCallResponseBody {
                url(data)
            }.bytes()
        } else {
            runCatching {
                Base64.decode(data.split(",").getOrNull(1) ?: "", Base64.DEFAULT)
            }.getOrNull()
        }
    }

    override fun onDestroyView() {
        customWebViewCallback?.onCustomViewHidden()
        binding.webViewContainer.removeView(currentWebView)
        currentWebView.removeAllViews()
        currentWebView.destroy()
        originOrientation?.let {
            activity?.requestedOrientation = it
        }
        super.onDestroyView()
    }

    override fun upConfig(config: String) {
        try {
            lifecycleScope.launch(Dispatchers.Main) {
                GSON.fromJsonObject<Config>(config).getOrThrow().let { config ->
                    setConfig(config)
                }
            }
        } catch (e: Exception) {
            AppLog.put("config err", e)
        }
    }

    @Suppress("unused")
    private class JSInterface(dialog: BottomWebViewDialog) {
        private val dialogRef: WeakReference<BottomWebViewDialog> = WeakReference(dialog)

        @JavascriptInterface
        fun lockOrientation(orientation: String) {
            val fra = dialogRef.get() ?: return
            val ctx = fra.requireActivity()
            if (fra.isFullScreen && fra.dialog?.isShowing == true) {
                fra.lifecycleScope.launch(Dispatchers.Main) {
                    ctx.requestedOrientation = when (orientation) {
                        "portrait", "portrait-primary" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "portrait-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        "landscape-primary" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        "landscape-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        "any", "unspecified" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }

        @JavascriptInterface
        fun onCloseRequested() {
            val fra = dialogRef.get() ?: return
            if (fra.dialog?.isShowing == true) {
                fra.lifecycleScope.launch(Dispatchers.Main) {
                    fra.dismiss()
                }
            }
        }
    }

    @Keep
    data class Config(
        var state: Int? = null,
        var peekHeight: Int? = null,
        var isHideable: Boolean? = null,
        var skipCollapsed: Boolean? = null,
        var setHalfExpandedRatio: Float? = null,
        var setExpandedOffset: Int? = null,
        var setFitToContents: Boolean? = null,
        var isDraggable: Boolean? = null,
        var isDraggableOnNestedScroll: Boolean? = null,
        var significantVelocityThreshold: Int? = null,
        var hideFriction: Float? = null,
        var maxWidth: Int? = null,
        var maxHeight: Int? = null,
        var isGestureInsetBottomIgnored: Boolean? = null,
        var expandedCornersRadius: Float? = null,
        var setUpdateImportantForAccessibilityOnSiblings: Boolean? = null,
        var backgroundDimAmount: Float? = null,
        var shouldDimBackground: Boolean? = null,
        var webViewInitialScale: Int? = null,
        var webViewCacheMode: Int? = null,
        var dismissOnTouchOutside: Boolean? = null,
        var hardwareAccelerated: Boolean? = null,
        var isNestedScrollingEnabled: Boolean? = null,
        var widthPercentage: Float? = null,
        var heightPercentage: Float? = null,
        var responsiveBreakpoint: Int? = null,
        var dialogHeight: Int? = null,
        var longClickSaveImg: Boolean? = null,
        var scrollNoDraggable: Boolean? = null,
    )

    inner class CustomWebChromeClient : WebChromeClient() {
        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            originOrientation = activity?.requestedOrientation
            isFullScreen = true
            binding.webViewContainer.invisible()
            binding.customWebView.addView(view)
            customWebViewCallback = callback
            behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }

        override fun onHideCustomView() {
            originOrientation?.let {
                activity?.requestedOrientation = it
                originOrientation = null
            }
            isFullScreen = false
            binding.webViewContainer.visible()
            binding.customWebView.removeAllViews()
            customWebViewCallback = null
        }

        override fun onCloseWindow(window: WebView?) {
            dismiss()
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (!AppConfig.recordLog) return false
            val source = source ?: return false
            val messageLevel = consoleMessage.messageLevel().name
            val message = consoleMessage.message()
            AppLog.put(
                "${source.getTag()}${messageLevel}: $message",
                NoStackTraceException("\n${message}\n- Line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
            )
            return true
        }
    }

    inner class CustomWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            request?.url?.let {
                return shouldOverrideUrlLoading(it)
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (needClearHistory) {
                currentWebView.clearHistory()
                needClearHistory = false
            }
            if (!isBasicJsInjected && view != null) {
                view.evaluateJavascript(basicJs, null)
                isBasicJsInjected = true
            }
        }

        private fun shouldOverrideUrlLoading(url: Uri): Boolean {
            return when (url.scheme) {
                "http", "https" -> {
                    false
                }
                "legado", "yuedu" -> {
                    startActivity<OnLineImportActivity> {
                        data = url
                    }
                    true
                }
                else -> {
                    binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                        activity?.openUrl(url)
                    }
                    true
                }
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView?, handler: SslErrorHandler?, error: SslError?
        ) {
            handler?.proceed()
        }

        private val webCookieManager by lazy { android.webkit.CookieManager.getInstance() }
        override fun shouldInterceptRequest(
            view: WebView, request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            if (!request.isForMainFrame && url.contains(DEFAULT_JS_INJECT_URL)) {
                val preloadJs = preloadJs ?: ""
                return WebResourceResponse(
                    "text/javascript",
                    "utf-8",
                    ByteArrayInputStream("(() => {$JS_INJECTION\n$preloadJs\n})();".toByteArray())
                )
            }
            if (request.isForMainFrame && !preloadJs.isNullOrEmpty()) {
                return runBlocking(IO) {
                    getModifiedContentWithJs(url, request) ?: super.shouldInterceptRequest(
                        view,
                        request
                    )
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        private suspend fun getModifiedContentWithJs(
            url: String,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return try {
                val cookie = webCookieManager.getCookie(url)
                val res = okHttpClient.newCallResponse {
                    url(url)
                    method(request.method, null)
                    if (!cookie.isNullOrEmpty()) addHeader("Cookie", cookie)
                    request.requestHeaders?.forEach { (key, value) -> addHeader(key, value) }
                }
                res.headers("Set-Cookie").forEach { webCookieManager.setCookie(url, it) }
                val body = res.body
                val contentType = body.contentType()
                val mimeType = contentType?.toString()?.substringBefore(";") ?: "text/html"
                val charset = contentType?.charset() ?: Charsets.UTF_8
                val bodyText = buildSpliceHtml(body.text(charset.toString()))
                WebResourceResponse(
                    mimeType,
                    charset.name(),
                    ByteArrayInputStream(bodyText.toByteArray(charset))
                )
            } catch (e: Exception) {
                AppLog.put("拦截请求处理失败", e)
                null
            }
        }
    }
}