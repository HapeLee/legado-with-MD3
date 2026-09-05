package io.legado.app.feature.reader.core.transition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class CurlPoint(val x: Float, val y: Float) {
    val isFinite: Boolean get() = x.isFinite() && y.isFinite()
}

data class CurlMirror(
    val scaleX: Float,
    val skewX: Float,
    val skewY: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float,
)

data class PageCurlFrame(
    val touch: CurlPoint,
    val corner: CurlPoint,
    val control1: CurlPoint,
    val control2: CurlPoint,
    val start1: CurlPoint,
    val start2: CurlPoint,
    val end1: CurlPoint,
    val end2: CurlPoint,
    val vertex1: CurlPoint,
    val vertex2: CurlPoint,
    val degrees: Float,
    val touchToCornerDistance: Float,
    val mirror: CurlMirror,
) {
    val isValid: Boolean get() = listOf(
        touch, corner, control1, control2, start1, start2, end1, end2, vertex1, vertex2,
    ).all(CurlPoint::isFinite)
}

/**
 * 折页三道阴影的峰值颜色。渐变结构与色相沿用原版 GradientDrawable
 * （0xFF111111 / 0x80111111 / 0xB0333333），峰值 alpha 整体按约六成调轻：
 * 全不透明的折缝黑边是"阴影偏重"观感的主要来源。
 */
object ReaderCurlVisualPolicy {
    val backShadowDarkArgb: Int = 0x99111111.toInt()
    val frontShadowDarkArgb: Int = 0x4D111111.toInt()
    val folderShadowDarkArgb: Int = 0x73333333.toInt()
}

object ReaderCurlTouchPolicy {
    fun settleDurationMillis(
        currentX: Float,
        targetX: Float,
        pageWidth: Float,
        baseDurationMillis: Int = 300,
    ): Int {
        if (pageWidth <= 0f || baseDurationMillis <= 0) return 0
        val distance = abs(targetX - currentX)
        if (distance < .01f) return 0
        // A one- or two-pixel final travel previously rounded to 0 ms, committing the page
        // before Compose had a chance to present the final curl frame.
        return (baseDurationMillis * distance / pageWidth).toInt().coerceAtLeast(90)
    }

    /**
     * Keeps the first visible curl clear of its locked corner after horizontal drag capture.
     * This creates a small, deterministic "snap" into a drawable fold instead of allowing the
     * Bezier controls to collapse while the finger is still on the page edge.
     */
    fun dragX(
        direction: ReaderTurnDirection,
        rawX: Float,
        pageWidth: Float,
        snapFraction: Float = .04f,
    ): Float {
        if (pageWidth <= 0f) return rawX
        val inset = (pageWidth * snapFraction).coerceAtLeast(1f)
        return when (direction) {
            ReaderTurnDirection.PREVIOUS -> rawX.coerceIn(inset, pageWidth)
            ReaderTurnDirection.NEXT -> rawX.coerceIn(0f, pageWidth - inset)
        }
    }

    /** Interpolates the first safe fold out of its corner so the entering page does not pop in. */
    fun revealX(
        direction: ReaderTurnDirection,
        targetX: Float,
        pageWidth: Float,
        revealProgress: Float,
    ): Float {
        if (pageWidth <= 0f) return targetX
        val cornerX = if (direction == ReaderTurnDirection.NEXT) pageWidth else 0f
        val progress = revealProgress.coerceIn(0f, 1f)
        return cornerX + (targetX - cornerX) * progress
    }

    fun programmaticX(
        direction: ReaderTurnDirection,
        pageWidth: Float,
    ): Float = when (direction) {
        ReaderTurnDirection.PREVIOUS -> 0f
        ReaderTurnDirection.NEXT -> pageWidth * .9f
    }

    fun settledX(
        direction: ReaderTurnDirection,
        committed: Boolean,
        pageWidth: Float,
    ): Float = when (direction) {
        ReaderTurnDirection.PREVIOUS -> if (committed) pageWidth else -pageWidth
        ReaderTurnDirection.NEXT -> if (committed) -pageWidth else pageWidth
    }

    fun cornerY(
        direction: ReaderTurnDirection,
        capturedY: Float,
        pageHeight: Float,
    ): Float = when (direction) {
        ReaderTurnDirection.PREVIOUS -> pageHeight
        ReaderTurnDirection.NEXT -> if (capturedY <= pageHeight / 2f) 0f else pageHeight
    }

    fun settledY(cornerY: Float, pageHeight: Float): Float =
        if (cornerY <= pageHeight / 2f) 1f else pageHeight

    fun dragY(
        direction: ReaderTurnDirection,
        capturedY: Float,
        currentY: Float,
        pageHeight: Float,
    ): Float {
        if (pageHeight <= 0f) return currentY
        if (direction == ReaderTurnDirection.PREVIOUS) return pageHeight
        return when {
            capturedY > pageHeight / 3f && capturedY < pageHeight / 2f -> 1f
            capturedY >= pageHeight / 2f && capturedY < pageHeight * 2f / 3f -> pageHeight
            else -> currentY
        }
    }

    fun programmaticY(
        direction: ReaderTurnDirection,
        sourceY: Float,
        pageHeight: Float,
    ): Float = when (direction) {
        ReaderTurnDirection.PREVIOUS -> pageHeight
        ReaderTurnDirection.NEXT -> if (sourceY > pageHeight / 2f) pageHeight * .9f else 1f
    }
}

/** Pure page-curl geometry, ported from the mature SimulationPageDelegate algorithm. */
object PageCurlGeometry {
    fun calculate(
        width: Float,
        height: Float,
        inputX: Float,
        inputY: Float,
        lockedCorner: CurlPoint? = null,
    ): PageCurlFrame? {
        if (width <= 0f || height <= 0f) return null
        val corner = lockedCorner ?: CurlPoint(
            if (inputX <= width / 2f) 0f else width,
            if (inputY <= height / 2f) 0f else height,
        )
        // 收尾动画把触点送出页外让折页连同阴影滑出屏幕；页外触点保持原值走原始
        // 贝塞尔计算（对照原版 calcPoints 仅在触点页内时做收拢修正），页内仍收敛
        // 到 .1f 边界避免 0 值退化。
        var touch = CurlPoint(
            if (inputX < 0f || inputX > width) inputX else inputX.coerceIn(.1f, width - .1f),
            inputY.coerceIn(.1f, height - .1f),
        )
        fun controls(point: CurlPoint): Pair<CurlPoint, CurlPoint> {
            val middleX = (point.x + corner.x) / 2f
            val middleY = (point.y + corner.y) / 2f
            val dx = (corner.x - middleX).takeUnless { abs(it) < .0001f } ?: .0001f
            val dy = (corner.y - middleY).takeUnless { abs(it) < .0001f } ?: .1f
            return CurlPoint(middleX - (corner.y - middleY) * (corner.y - middleY) / dx, corner.y) to
                CurlPoint(corner.x, middleY - (corner.x - middleX) * (corner.x - middleX) / dy)
        }
        var (control1, control2) = controls(touch)
        var start1 = CurlPoint(control1.x - (corner.x - control1.x) / 2f, corner.y)
        if (touch.x > 0f && touch.x < width && start1.x !in 0f..width) {
            if (start1.x < 0f) start1 = CurlPoint(width - start1.x, start1.y)
            val f1 = abs(corner.x - touch.x).coerceAtLeast(.1f)
            val f2 = width * f1 / start1.x
            val adjustedX = abs(corner.x - f2)
            val f3 = abs(corner.x - adjustedX) * abs(corner.y - touch.y) / f1
            touch = CurlPoint(adjustedX, abs(corner.y - f3))
            val controls = controls(touch)
            control1 = controls.first
            control2 = controls.second
            start1 = CurlPoint(control1.x - (corner.x - control1.x) / 2f, corner.y)
        }
        val start2 = CurlPoint(corner.x, control2.y - (corner.y - control2.y) / 2f)
        val end1 = cross(touch, control1, start1, start2) ?: return null
        val end2 = cross(touch, control2, start1, start2) ?: return null
        val vertex1 = CurlPoint((start1.x + 2f * control1.x + end1.x) / 4f, (2f * control1.y + start1.y + end1.y) / 4f)
        val vertex2 = CurlPoint((start2.x + 2f * control2.x + end2.x) / 4f, (2f * control2.y + start2.y + end2.y) / 4f)
        val distance = hypot((touch.x - corner.x).toDouble(), (touch.y - corner.y).toDouble()).toFloat()
        val degrees = (atan2((control1.x - corner.x).toDouble(), (control2.y - corner.y).toDouble()) * 180.0 / PI).toFloat()
        val mirror = mirror(corner, control1, control2)
        return PageCurlFrame(touch, corner, control1, control2, start1, start2, end1, end2, vertex1, vertex2, degrees, distance, mirror).takeIf(PageCurlFrame::isValid)
    }

    private fun cross(p1: CurlPoint, p2: CurlPoint, p3: CurlPoint, p4: CurlPoint): CurlPoint? {
        val dx1 = p2.x - p1.x
        val dx2 = p4.x - p3.x
        if (abs(dx1) < .0001f || abs(dx2) < .0001f) return null
        val a1 = (p2.y - p1.y) / dx1
        val b1 = p1.y - a1 * p1.x
        val a2 = (p4.y - p3.y) / dx2
        val b2 = p3.y - a2 * p3.x
        if (abs(a1 - a2) < .0001f) return null
        val x = (b2 - b1) / (a1 - a2)
        return CurlPoint(x, a1 * x + b1)
    }

    private fun mirror(corner: CurlPoint, c1: CurlPoint, c2: CurlPoint): CurlMirror {
        val distance = hypot((corner.x - c1.x).toDouble(), (c2.y - corner.y).toDouble()).toFloat().coerceAtLeast(.0001f)
        val f8 = (corner.x - c1.x) / distance
        val f9 = (c2.y - corner.y) / distance
        val sx = 1f - 2f * f9 * f9
        val skew = 2f * f8 * f9
        val sy = 1f - 2f * f8 * f8
        return CurlMirror(sx, skew, skew, sy, c1.x - (sx * c1.x + skew * c1.y), c1.y - (skew * c1.x + sy * c1.y))
    }
}
