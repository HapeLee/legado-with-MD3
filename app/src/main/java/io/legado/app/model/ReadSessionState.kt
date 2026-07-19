package io.legado.app.model

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.getMeanColor

/** 仅在当前阅读会话内有效的渲染资源，不参与持久化。 */
object ReadSessionState {
    var isComic: Boolean = false
        internal set

    @Volatile
    var isDarkThemeOverride: Boolean? = null
        internal set

    var lastNavigationBarHeight: Int = 0

    var background: Drawable? = null
        private set

    var backgroundMeanColor: Int = 0
        private set

    fun updateBackground(width: Int, height: Int) {
        val drawable = ReadBookConfig.durConfig.curBgDrawable(width, height)
        backgroundMeanColor = when (drawable) {
            is BitmapDrawable -> drawable.bitmap?.getMeanColor() ?: 0
            is ColorDrawable -> drawable.color
            else -> 0
        }
        background = drawable
    }
}
