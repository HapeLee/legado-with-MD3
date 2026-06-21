@file:Suppress("unused")

package io.legado.app.lib.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
val Context.primaryColor: Int
    get() = ThemeStore.primaryColor(this)

val Context.backgroundColor: Int
    get() = ThemeStore.backgroundColor(this)

val Context.accentColor: Int
    get() = ThemeStore.primaryColor(this)

val Context.primaryTextColor: Int
    @androidx.annotation.ColorInt
    get() = androidx.core.content.ContextCompat.getColor(this, io.legado.app.R.color.primaryText)

val Context.secondaryTextColor: Int
    @androidx.annotation.ColorInt
    get() = androidx.core.content.ContextCompat.getColor(this, io.legado.app.R.color.secondaryText)

fun Context.composePanelRadius(): Dp = 16.dp

fun Context.composeActionRadius(): Dp = 12.dp

fun Context.uiTypeface(): Typeface = Typeface.DEFAULT
