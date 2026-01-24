package io.legado.app.ui.widget.dialog

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.databinding.DialogWebViewBinding
import io.legado.app.help.WebCacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.JS_INJECTION
import io.legado.app.help.webView.WebJsExtensions.Companion.basicJs
import io.legado.app.help.webView.WebJsExtensions.Companion.nameBasic
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.utils.get
import io.legado.app.utils.invisible
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class BottomWebViewDialog() :
    BaseBottomSheetDialogFragment(R.layout.dialog_web_view) {

    constructor(
        sourceKey: String,
        bookType: Int,
        url: String,
        html: String,
        preloadJs: String? = null
    ) : this() {
        arguments = Bundle().apply {
            putString("sourceKey", sourceKey)
            putInt("bookType", bookType)
            putString("url", url)
            putString("html", html)
            putString("preloadJs", preloadJs)
        }
    }

    private val binding by viewBinding(DialogWebViewBinding::bind)

    private val bottomSheet by lazy {
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
    }

    private val behavior by lazy {
        bottomSheet?.let { BottomSheetBehavior.from(it) }
    }

    private lateinit var currentWebView: WebView

    private var source: BaseSource? = null
    private var isFullScreen = false
    private var customWebViewCallback: CustomViewCallback? = null
    private var originOrientation: Int? = null
    private var needClearHistory = true

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching {
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        if (!AppConfig.isEInkMode) {
            view.setBackgroundColor(ThemeStore.backgroundColor())
        }

        currentWebView = createWebView()
        binding.webViewContainer.addView(currentWebView)

        lifecycleScope.launch {
            val args = arguments ?: run {
                dismiss(); return@launch
            }

            val sourceKey = args.getString("sourceKey") ?: return@launch
            val url = args.getString("url") ?: return@launch
            var html = args.getString("html") ?: return@launch

            args.getString("preloadJs")?.let { preloadJs ->
                html = if (html.contains("<head>")) {
                    html.replaceFirst(
                        "<head>",
                        "<head><script>(() => {$JS_INJECTION\n$preloadJs\n})();</script>"
                    )
                } else {
                    "<head><script>(() => {$JS_INJECTION\n$preloadJs\n})();</script></head>$html"
                }
            }

            source = appDb.bookSourceDao.getBookSource(sourceKey) ?: run {
                activity?.toastOnUi("no find bookSource")
                dismiss()
                return@launch
            }

            val bookType = args.getInt("bookType", 0)
            val analyzeUrl = AnalyzeUrl(url, source = source, coroutineContext = coroutineContext)

            currentWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                behavior?.isDraggable = scrollY == 0
            }

            currentWebView.post {
                initWebView(
                    analyzeUrl.url,
                    html,
                    analyzeUrl.headerMap,
                    bookType
                )
                currentWebView.clearHistory()
            }
        }

        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                when {
                    binding.customWebView.isNotEmpty() -> {
                        customWebViewCallback?.onCustomViewHidden()
                        true
                    }

                    currentWebView.canGoBack() -> {
                        currentWebView.goBack()
                        true
                    }

                    else -> {
                        dismiss()
                        true
                    }
                }
            } else false
        }
    }

    private fun createWebView(): WebView =
        WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

    private fun initWebView(
        url: String,
        html: String,
        headerMap: HashMap<String, String>,
        bookType: Int
    ) {
        currentWebView.apply {
            webChromeClient = CustomWebChromeClient()
            webViewClient = CustomWebViewClient()

            settings.userAgentString =
                headerMap.get(AppConst.UA_NAME, true)

            addJavascriptInterface(JSInterface(this@BottomWebViewDialog), nameBasic)

            source?.let { source ->
                (activity as? AppCompatActivity)?.let { act ->
                    addJavascriptInterface(
                        WebJsExtensions(source, act, this, bookType),
                        nameJava
                    )
                }
                addJavascriptInterface(source, nameSource)
                addJavascriptInterface(WebCacheManager, nameCache)
            }

            loadDataWithBaseURL(url, html, "text/html", "utf-8", url)
        }
    }

    override fun onDestroyView() {
        customWebViewCallback?.onCustomViewHidden()

        currentWebView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()

            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()

            destroy()
        }


        originOrientation?.let {
            activity?.requestedOrientation = it
        }

        super.onDestroyView()
    }

    private class JSInterface(dialog: BottomWebViewDialog) {
        private val ref = WeakReference(dialog)

        @JavascriptInterface
        fun lockOrientation(orientation: String) {
            val fra = ref.get() ?: return
            val act = fra.activity ?: return

            if (fra.isFullScreen && fra.dialog?.isShowing == true) {
                act.runOnUiThread {
                    act.requestedOrientation = when (orientation) {
                        "portrait", "portrait-primary" ->
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                        "portrait-secondary" ->
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

                        "landscape", "landscape-primary" ->
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                        "landscape-secondary" ->
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

                        "any", "unspecified" ->
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR

                        else ->
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }

        @JavascriptInterface
        fun onCloseRequested() {
            ref.get()?.activity?.runOnUiThread {
                ref.get()?.dismiss()
            }
        }
    }

    inner class CustomWebChromeClient : WebChromeClient() {

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            isFullScreen = true
            binding.webViewContainer.invisible()
            binding.customWebView.addView(view)
            customWebViewCallback = callback
            behavior?.state = BottomSheetBehavior.STATE_EXPANDED
            originOrientation = activity?.requestedOrientation
        }

        override fun onHideCustomView() {
            isFullScreen = false
            binding.webViewContainer.visible()
            binding.customWebView.removeAllViews()
            customWebViewCallback = null
            originOrientation?.let {
                activity?.requestedOrientation = it
            }
        }

        override fun onCloseWindow(window: WebView?) {
            dismiss()
        }
    }

    inner class CustomWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (needClearHistory) {
                needClearHistory = false
                currentWebView.clearHistory()
            }
            super.onPageStarted(view, url, favicon)
            currentWebView.evaluateJavascript(basicJs, null)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return request?.url?.let(::handleUri) ?: true
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return url?.toUri()?.let(::handleUri) ?: true
        }

        private fun handleUri(uri: Uri): Boolean =
            when (uri.scheme) {
                "http", "https" -> false
                "legado", "yuedu" -> {
                    startActivity<OnLineImportActivity> { data = uri }
                    true
                }

                else -> {
                    binding.root.longSnackbar(
                        R.string.jump_to_another_app,
                        R.string.confirm
                    ) {
                        activity?.openUrl(uri)
                    }
                    true
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
}
