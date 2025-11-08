package io.legado.app.ui.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import io.legado.app.utils.dpToPx

class AccentColorButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialButton(context, attrs) {

    var color: Int = 0
        set(value) {
            field = value
            iconTint = null
            icon = createColorPreviewDrawable(value)
        }

    init {
        setPaddingRelative(14.dpToPx(), 8.dpToPx(), 10.dpToPx(), 8.dpToPx())
        iconGravity = ICON_GRAVITY_END
        iconPadding = 8.dpToPx()
        iconSize = 24.dpToPx()
    }

    private fun createColorPreviewDrawable(color: Int): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f.dpToPx()
            setColor(color)
        }
    }

}



