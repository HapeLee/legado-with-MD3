package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.dict.DictDialog
import io.legado.app.utils.toastOnUi

@SuppressLint("SetJavaScriptEnabled")
class VisibleWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    private var lastSelectedText: String = ""

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        addJavascriptInterface(object {
            @JavascriptInterface
            fun onTextSelected(text: String) {
                lastSelectedText = text
            }
        }, "TextSelectionBridge")

        val js = """
            document.addEventListener('selectionchange', function() {
                const text = window.getSelection().toString();
                if (text) {
                    TextSelectionBridge.onTextSelected(text);
                }
            });
        """.trimIndent()
        evaluateJavascript(js, null)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode {
        return super.startActionMode(createWrappedCallback(callback))
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode {
        return super.startActionMode(createWrappedCallback(callback), type)
    }

    private fun createWrappedCallback(original: ActionMode.Callback?): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original?.onCreateActionMode(mode, menu) ?: false
                menu.add(Menu.NONE, MENU_ID_DICT, 0, R.string.dict)
                getSelectedText { }
                return result
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                updateDictMenuItem(menu)
                return original?.onPrepareActionMode(mode, menu) ?: false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    MENU_ID_DICT -> {
                        postDelayed({
                            getSelectedText { selectedText ->
                                if (selectedText.isNotBlank()) {
                                    showDictDialog(selectedText)
                                } else {
                                    context.toastOnUi("未获取到选中文本，请重试")
                                }
                            }
                        }, 200)
                        mode.finish()
                        true
                    }

                    else -> original?.onActionItemClicked(mode, item) ?: false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                original?.onDestroyActionMode(mode)
            }
        }
    }

    private fun updateDictMenuItem(menu: Menu) {
        val dictItem = menu.findItem(MENU_ID_DICT)
        dictItem?.let { item ->
            getSelectedText { selectedText ->
                item.isEnabled = selectedText.isNotBlank()
            }
        }
    }

    private fun getSelectedText(callback: (String) -> Unit) {
        if (lastSelectedText.isNotBlank()) {
            callback(lastSelectedText)
        } else {
            evaluateJavascript("(function(){return window.getSelection().toString();})()") { result ->
                val selectedText = result?.removeSurrounding("\"") ?: ""
                lastSelectedText = selectedText
                callback(selectedText)
            }
        }
    }

    private fun showDictDialog(selectedText: String) {
        val activity = context as? AppCompatActivity ?: return
        val dialog = DictDialog(selectedText)
        activity.supportFragmentManager.beginTransaction()
            .add(dialog, "DictDialog")
            .commitAllowingStateLoss()
    }

    companion object {
        private const val MENU_ID_DICT = 1001
    }
}

@Composable
fun VisibleWebViewCompose(
    modifier: Modifier = Modifier,
    onCreated: (VisibleWebView) -> Unit,
    onDestroyed: (() -> Unit)? = null
) {
    val webViewHolder = remember { WebViewHolder() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                val webView = VisibleWebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                addView(webView)
                webViewHolder.webView = webView
                onCreated(webView)
            }
        },
        update = { container ->
            webViewHolder.webView = container.getChildAt(0) as? VisibleWebView
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            onDestroyed?.invoke()
            webViewHolder.webView?.let { webView ->
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
            webViewHolder.webView = null
        }
    }
}

private class WebViewHolder {
    var webView: VisibleWebView? = null
}
