package io.legado.app.help

import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.view.View
import org.xml.sax.XMLReader

/**
 * A simple HTML tag handler that supports `<button>` tags with click events
 * for use with [Html.fromHtml] rendering in TextViews.
 */
class TextViewTagHandler(
    private val listener: OnButtonClickListener
) : Html.TagHandler {

    interface OnButtonClickListener {
        fun onButtonClick(name: String, click: String)
    }

    override fun handleTag(
        opening: Boolean,
        tag: String,
        output: Editable,
        xmlReader: XMLReader
    ) {
        if (tag.equals("button", ignoreCase = true)) {
            if (opening) {
                startButton(output, ButtonSpan())
            } else {
                endButton(output)
            }
        }
    }

    private fun startButton(output: Editable, span: ButtonSpan) {
        val len = output.length
        output.setSpan(span, len, len, Spannable.SPAN_MARK_MARK)
    }

    private fun endButton(output: Editable) {
        val last = getLast(output, ButtonSpan::class.java) ?: return
        val start = output.getSpanStart(last)
        val end = output.length
        output.removeSpan(last)
        if (start != end) {
            val clickable = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    listener.onButtonClick(output.substring(start, end), "")
                }
            }
            output.setSpan(clickable, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun <T> getLast(text: Editable, kind: Class<T>): T? {
        val spans = text.getSpans(0, text.length, kind)
        return if (spans.isEmpty()) null else spans[spans.size - 1]
    }

    private class ButtonSpan
}
