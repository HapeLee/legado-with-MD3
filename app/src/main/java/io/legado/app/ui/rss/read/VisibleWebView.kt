package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.ui.dict.DictDialog
import io.legado.app.utils.toastOnUi

@SuppressLint("SetJavaScriptEnabled")
class VisibleWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

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
    """
        evaluateJavascript(js, null)
    }

    private var lastSelectedText: String = ""

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(VISIBLE)
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode {
        val wrappedCallback = createWrappedCallback(callback)
        return super.startActionMode(wrappedCallback)
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode {
        val wrappedCallback = createWrappedCallback(callback)
        return super.startActionMode(wrappedCallback, type)
    }

    private fun createWrappedCallback(original: ActionMode.Callback?): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original?.onCreateActionMode(mode, menu) ?: false
                menu.add(Menu.NONE, MENU_ID_DICT, 0, R.string.dict)
                getSelectedText { /* 触发缓存*/ }
                return result
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                updateDictMenuItem(menu)
                return original?.onPrepareActionMode(mode, menu) ?: false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
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
                        return true
                    }
                    else -> return original?.onActionItemClicked(mode, item) ?: false
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
        val activity = context as? AppCompatActivity
        activity?.let {
            val dialog = DictDialog(selectedText)
            it.supportFragmentManager.beginTransaction()
                .add(dialog, "DictDialog")
                .commitAllowingStateLoss()
        }
    }

    companion object {
        private const val MENU_ID_DICT = 1001
    }

}