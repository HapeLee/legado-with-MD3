package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.R
import io.legado.app.ui.dict.DictActivity
import io.legado.app.ui.widget.components.WebViewContainerCompose
import io.legado.app.utils.toastOnUi

@SuppressLint("SetJavaScriptEnabled")
class VisibleWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    private var lastSelectedText: String = ""

    init {
        setBackgroundColor(0)
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
                                    showDictSheet(selectedText)
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

    private fun showDictSheet(selectedText: String) {
        context.startActivity(DictActivity.startIntent(context, selectedText))
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
    WebViewContainerCompose(
        modifier = modifier,
        createWebView = { context -> VisibleWebView(context).also(onCreated) },
        onDestroyed = onDestroyed
    )
}
