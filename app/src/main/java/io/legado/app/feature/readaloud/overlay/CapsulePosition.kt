package io.legado.app.feature.readaloud.overlay

import kotlin.math.roundToInt

/** 位置保存为相对屏幕底部中央的中心偏移，尺寸、导航栏和折叠状态不参与保存。 */
const val CapsuleDefaultCenterFromBottomDp = 72f

fun capsuleWindowY(offsetYDp: Float, density: Float, heightPx: Int): Int =
    ((CapsuleDefaultCenterFromBottomDp - offsetYDp) * density - heightPx / 2f).roundToInt()

fun capsuleOffsetY(windowY: Int, density: Float, heightPx: Int): Float =
    CapsuleDefaultCenterFromBottomDp - (windowY + heightPx / 2f) / density


/** 只在松手时吸附靠近的左右边缘，中间区域保持手动位置。 */
fun snapCapsuleOffsetX(offsetXDp: Float, screenWidthDp: Float): Float {
    val edge = screenWidthDp.coerceAtLeast(0f) / 2f
    val x = offsetXDp.coerceIn(-edge, edge)
    return if (edge - kotlin.math.abs(x) <= 72f) {
        if (x < 0f) -edge else edge
    } else x
}

/** 保存的是完整封面的中心；吸附时中心正好在屏幕边线上。 */
fun capsuleDockSide(offsetXDp: Float, screenWidthDp: Float): Int {
    val edge = screenWidthDp / 2f
    if (edge <= 0f) return 0
    return when {
        offsetXDp <= -edge + 0.5f -> -1
        offsetXDp >= edge - 0.5f -> 1
        else -> 0
    }
}

fun capsuleVisibleOffsetX(offsetXDp: Float, screenWidthDp: Float, visibleWidthDp: Float): Float {
    val max = ((screenWidthDp - visibleWidthDp) / 2f).coerceAtLeast(0f)
    return offsetXDp.coerceIn(-max, max)
}


/** 迷你封面中心可到边线上；展开时随同一进度连续让位，始终保留完整封面。 */
fun capsulePresentationOffsetX(
    offsetXDp: Float,
    screenWidthDp: Float,
    footprintWidthDp: Float,
    expansion: Float
): Float {
    val edge = screenWidthDp.coerceAtLeast(0f) / 2f
    val mini = offsetXDp.coerceIn(-edge, edge)
    val expanded = capsuleVisibleOffsetX(offsetXDp, screenWidthDp, footprintWidthDp)
    return mini + (expanded - mini) * expansion.coerceIn(0f, 1f)
}
