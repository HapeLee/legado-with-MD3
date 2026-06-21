@file:Suppress("unused")

package io.legado.app.lib.theme

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.legado.app.R

/**
 * Applies a section title style to a TextView (larger, bold, primary text color).
 */
fun TextView.applyUiSectionTitleStyle(context: Context) {
    textSize = 16f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(ContextCompat.getColor(context, R.color.primaryText))
}

/**
 * Applies a label style to a TextView (smaller, secondary text color).
 */
fun TextView.applyUiLabelStyle(context: Context) {
    textSize = 13f
    setTypeface(typeface, Typeface.NORMAL)
    setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
}
