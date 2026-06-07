package io.legado.app.ui.book.read

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 护眼色温覆盖层
 * 在父 View 之上叠加一层半透明暖色，模拟护眼色温
 */
class EyeProtectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    /**
     * 刷新当前护眼状态
     */
    fun refresh() {
        val color = EyeProtectionHelper.overlayColor()
        if (color == Color.TRANSPARENT) {
            if (visibility != GONE) {
                visibility = GONE
            }
        } else {
            if (visibility != VISIBLE) {
                visibility = VISIBLE
            }
            paint.color = color
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (paint.color == Color.TRANSPARENT) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
