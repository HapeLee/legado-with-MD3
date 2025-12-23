package io.legado.app.help

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class GlideUrlDrawable(): Drawable() {
    private var mDrawable: Drawable? = null
    fun setDrawable(drawable: Drawable?) {
        this.mDrawable = drawable
        drawable?.bounds?.let { bounds ->
            setBounds(bounds)
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        mDrawable?.draw(canvas)
    }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setAlpha(alpha: Int) {
        mDrawable?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mDrawable?.colorFilter = colorFilter
    }
}