@file:Suppress("unused")

package io.legado.app.lib.theme

import android.content.Context

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
val Context.primaryColor: Int
    get() = ThemeStore.primaryColor(this)