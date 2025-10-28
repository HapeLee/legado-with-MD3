package io.legado.app.lib.theme


import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes

/**
 * @author Aidan Follestad (afollestad), Karim Abou Zeid (kabouzeid)
 */
internal interface ThemeStoreInterface {

    // Primary colors

    fun primaryColor(@ColorInt color: Int): ThemeStore

    fun primaryColorRes(@ColorRes colorRes: Int): ThemeStore

    fun primaryColorAttr(@AttrRes colorAttr: Int): ThemeStore

    fun autoGeneratePrimaryDark(autoGenerate: Boolean): ThemeStore

    fun primaryColorDark(@ColorInt color: Int): ThemeStore

    fun primaryColorDarkRes(@ColorRes colorRes: Int): ThemeStore

    fun primaryColorDarkAttr(@AttrRes colorAttr: Int): ThemeStore

    fun addColorScheme(@ColorInt color: Int): ThemeStore

    // Background

    fun backgroundColor(@ColorInt color: Int): ThemeStore

    fun bottomBackground(@ColorInt color: Int): ThemeStore

    // Commit/apply

    fun apply()
}
