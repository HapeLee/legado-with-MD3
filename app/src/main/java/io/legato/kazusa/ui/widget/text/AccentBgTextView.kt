package io.legato.kazusa.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import io.legato.kazusa.R
import io.legato.kazusa.lib.theme.Selector
import io.legato.kazusa.utils.ColorUtils
import io.legato.kazusa.utils.dpToPx
import androidx.core.content.withStyledAttributes
import io.legato.kazusa.utils.spToPx
import io.legato.kazusa.utils.themeColor

class AccentBgTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MaterialTextView(context, attrs) {

    private var radiusPx = 20

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        val horizontalPadding = 6.dpToPx()
        val verticalPadding = 2.dpToPx()
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        updateBackground()
    }

    fun setRadius(dp: Int) {
        radiusPx = dp.dpToPx()
        updateBackground()
    }

    private fun updateBackground() {
        val backgroundColor = context.themeColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val textColor = context.themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)

        background = Selector.shapeBuild()
            .setCornerRadius(radiusPx)
            .setDefaultBgColor(backgroundColor)
            .setPressedBgColor(ColorUtils.darkenColor(backgroundColor))
            .create()

        setTextColor(textColor)
    }
}