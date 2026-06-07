package io.legado.app.ui.book.read.config

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance

/**
 * 用于在阅读排版阶段传递局部下划线样式
 */
class HighlightStyleSpan(
    val underlineMode: Int,
    val underlineColor: Int,
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String = "",
    val bgImage: String = "",
    val bgImageFit: Int = 0,
    val bgImageScale: Float = 1f,
) : CharacterStyle(), UpdateAppearance {

    override fun updateDrawState(tp: TextPaint) = Unit

}
