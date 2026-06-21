package io.legado.app.ui.widget.image

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * An ImageView that maintains a configurable aspect ratio and can be used as an icon button.
 */
class RatioIconButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var ratio: Float = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (ratio > 0) {
            val width = measuredWidth
            val height = (width * ratio).toInt()
            setMeasuredDimension(width, height)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            imageAlpha = if (enabled) 0xFF else 0x3F
        }
        super.setEnabled(enabled)
    }
}
