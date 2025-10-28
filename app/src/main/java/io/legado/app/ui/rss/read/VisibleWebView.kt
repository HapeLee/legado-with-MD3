package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.ui.dict.DictDialog

@SuppressLint("SetJavaScriptEnabled")
class VisibleWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
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
                        getSelectedText { selectedText ->
                            if (selectedText.isNotBlank()) {
                                showDictDialog(selectedText)
                            }
                        }
                        mode.finish()
                        return true
                    }
                    else -> {
                        return original?.onActionItemClicked(mode, item) ?: false
                    }
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
        evaluateJavascript("(function(){return window.getSelection().toString();})()") { result ->
            val selectedText = result
                ?.removeSurrounding("\"")
                ?.replace("\\n", "\n")
                ?.replace("\\t", "\t")
                ?.replace("\\\"", "\"")
                ?: ""
            callback(selectedText)
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