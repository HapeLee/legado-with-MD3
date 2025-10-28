package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.utils.dpToPx
import androidx.core.content.withStyledAttributes

class EmptyMessageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val faceTextView: TextView
    private val messageTextView: TextView

    private val faces = listOf(
        "(；′⌒`)", "(つ﹏⊂)", "(•̀ᴗ•́)و", "(๑•́ ₃ •̀๑)", "(눈‸눈)", "(ಥ﹏ಥ)", "(｡•́︿•̀｡)"
    )

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())

        post {
            val faceWidth = faceTextView.width
            if (faceWidth > 0) {
                messageTextView.maxWidth = (faceWidth * 2f).toInt()
            }
        }

        faceTextView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
            textSize = 32f
            text = faces.random()
        }

        messageTextView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dpToPx()
            }
            gravity = Gravity.CENTER
            text = R.string.empty.toString()
        }

        addView(faceTextView)
        addView(messageTextView)

        attrs?.let {
            context.withStyledAttributes(it, R.styleable.EmptyMessageView) {
                val msg = getString(R.styleable.EmptyMessageView_messageText)
                messageTextView.text = msg ?: ""
                faceTextView.text = faces.random()
            }
        }
    }

    /** 设置文字消息 */
    fun setMessage(@StringRes resId: Int) {
        messageTextView.setText(resId)
    }

    fun setMessage(msg: String) {
        messageTextView.text = msg
    }

}
