package io.legado.app.feature.reader

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.legado.app.feature.reader.core.style.ReaderBackgroundBand

/**
 * 画一条合并后的背景色带：填充与描边共用同一个圆角——圆角大于 0 时填充也走圆角矩形，
 * 直边色块配圆角描边会在四角露出色块尖。
 * 圆角夹到宽高较小一边的一半：半径超出的圆角矩形在窄行盒上会画出交叉的弧线。
 */
fun DrawScope.drawBackgroundBand(band: ReaderBackgroundBand) {
    val bounds = band.bounds
    val topLeft = Offset(bounds.left, bounds.top)
    val size = Size(bounds.width, bounds.height)
    val radiusPx = band.borderRadiusPx
        .coerceAtLeast(0f)
        .coerceAtMost(minOf(bounds.width, bounds.height) / 2f)
    band.fillColorArgb?.let { fill ->
        if (radiusPx > 0f) {
            drawRoundRect(Color(fill), topLeft, size, CornerRadius(radiusPx))
        } else {
            drawRect(Color(fill), topLeft, size)
        }
    }
    val borderColor = band.borderColorArgb
    if (borderColor != null && band.borderWidthPx > 0f) {
        val stroke = Stroke(width = band.borderWidthPx)
        if (radiusPx > 0f) {
            drawRoundRect(Color(borderColor), topLeft, size, CornerRadius(radiusPx), style = stroke)
        } else {
            drawRect(Color(borderColor), topLeft, size, style = stroke)
        }
    }
}
