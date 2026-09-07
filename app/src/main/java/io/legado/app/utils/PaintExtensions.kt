package io.legado.app.utils

import android.graphics.Paint
import android.os.Build
import android.text.TextPaint

/**
 * 计入行盒的字体 leading。
 *
 * 正常情况下 leading 是字体建议的行间隙，应计入行高；但部分字体文件
 * （如方正新楷体）把 leading 声明为异常大的值（约等于 1em），按原值
 * 计算会导致行高翻倍且空隙全堆到字形上方。因此当 leading 超过字体
 * 高度（descent-ascent）的 15% 时，视为字体文件异常，返回 0 不计入。
 */
val Paint.validFontLeading: Float
    get() = fontMetrics.run {
        val height = descent - ascent
        if (leading > height * 0.15f) 0f else leading
    }

/** 文本行高：descent - ascent 加上 [validFontLeading]（异常 leading 已排除）。 */
val TextPaint.textHeight: Float
    get() = fontMetrics.run { descent - ascent + validFontLeading }

fun TextPaint.getTextWidthsCompat(text: String, widths: FloatArray) {
    getTextWidths(text, widths)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        val letterSpacing = letterSpacing * textSize
        val letterSpacingHalf = letterSpacing * 0.5f
        for (i in widths.indices) {
            if (widths[i] > 0) {
                widths[i] += letterSpacingHalf
                break
            }
        }
        for (i in text.lastIndex downTo 0) {
            if (widths[i] > 0) {
                widths[i] += letterSpacingHalf
                break
            }
        }
    }
}
