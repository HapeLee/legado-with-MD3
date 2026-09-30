package io.legado.app.feature.reader.core.transition

import androidx.compose.ui.geometry.Rect

/**
 * 书架↔阅读页形变的纯几何与纯时序（对照「胶囊→播放页」那一份 `PlayerMorphFrames`）。
 *
 * 时长、速度曲线、封面退场时机都是写死的一份，不再做成设置：这三样调好之后没有第二套
 * 想要试的值，做成设置反而多一屏要维护的滑块。
 */

/** 打开一段的时长：场景层撑住的时长与形变本身必须读同一个数。 */
const val READER_MORPH_OPEN_DURATION_MILLIS = 420

/** 返回一段的时长：返回是打开的倒放，两段等长才不会一边快一边慢。 */
const val READER_MORPH_BACK_DURATION_MILLIS = 420

/**
 * 封面**完全消失**的时刻，以整段时长的百分比计：0 就是一出发就没了，35 就是走到三成半它
 * 已经看不见。打开时它从第一帧起一路淡到这个点；返回是倒放，从这个点起一路淡回到落地。
 */
private const val COVER_GONE_FRACTION = 0.35f

/** 整段速度曲线：标准 FastOutSlowIn，起步快、收尾缓，形变收尾不会戛然而止。 */
const val READER_MORPH_EASING_X1 = 0.2f
const val READER_MORPH_EASING_Y1 = 0f
const val READER_MORPH_EASING_X2 = 0.1f
const val READER_MORPH_EASING_Y2 = 1f

/**
 * 打开：时间比例（0=刚出发，1=整段走完）→ 封面不透明度，到 [COVER_GONE_FRACTION] 归零。
 *
 * 面板从封面那一格长出去，所以起步这一帧封面必须和书架上那张完全重合；之后它跟着面板一起
 * 放大，同时在整段走完三成半之前彻底让位给正文。
 */
fun openCoverAlphaAt(elapsedFraction: Float): Float =
    (1f - elapsedFraction.coerceIn(0f, 1f) / COVER_GONE_FRACTION).coerceIn(0f, 1f)

/** 返回：打开的倒放，最后三成半一路把封面显出来，最后一帧正好接住书架上那张。 */
fun backCoverAlphaAt(elapsedFraction: Float): Float =
    openCoverAlphaAt(1f - elapsedFraction.coerceIn(0f, 1f))

/** 面板矩形：从封面那一格长到整块画布。四边各自线性插值，所以永远包含起点那一格。 */
fun panelRectAt(start: Rect, canvasWidth: Float, canvasHeight: Float, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    return Rect(
        left = start.left + (0f - start.left) * p,
        top = start.top + (0f - start.top) * p,
        right = start.right + (canvasWidth - start.right) * p,
        bottom = start.bottom + (canvasHeight - start.bottom) * p,
    )
}

/**
 * 飞行封面这一帧的矩形：跟着面板一起缩放，但**只做等比**，并且永远被面板框着。
 *
 * 面板四边各插各的，宽高比一直在变；封面要按非等比跟着长就会被压扁（正方的格子拉成竖条）。
 * 所以取两个方向里小的那个倍率，封面中心贴住面板中心。起点那一帧倍率为 1、中心就是封面
 * 自己的中心，和书架上那张逐像素重合。
 */
fun coverRectAt(start: Rect, canvasWidth: Float, canvasHeight: Float, progress: Float): Rect {
    if (start.width <= 0f || start.height <= 0f) return start
    val panel = panelRectAt(start, canvasWidth, canvasHeight, progress)
    val scale = minOf(panel.width / start.width, panel.height / start.height).coerceAtLeast(1f)
    val width = start.width * scale
    val height = start.height * scale
    return Rect(
        panel.center.x - width / 2f,
        panel.center.y - height / 2f,
        panel.center.x + width / 2f,
        panel.center.y + height / 2f,
    )
}

/**
 * 裁剪圆角：起点是封面自己的圆角，收尾贴屏幕物理圆角。
 *
 * 必须按**当前帧**的短边夹住，否则面板刚长出封面那一格时圆角比框还大，
 * 画出来是自交路径（表现为气泡一样的缺口）。
 */
fun panelCornerRadiusAt(
    startRadiusPx: Float,
    screenRadiusPx: Float,
    progress: Float,
    frameWidthPx: Float,
    frameHeightPx: Float,
): Float {
    val p = progress.coerceIn(0f, 1f)
    val radius = startRadiusPx + (screenRadiusPx - startRadiusPx) * p
    return radius.coerceAtMost(minOf(frameWidthPx, frameHeightPx) / 2f).coerceAtLeast(0f)
}

/**
 * 一帧的裁剪轮廓：圆角矩形的位置与圆角，一次算成一对，免得调用方两边各插值一遍算出
 * 「框已经长大、圆角还按小框夹」的中间态。
 */
fun panelClipAt(
    start: Rect,
    canvasWidth: Float,
    canvasHeight: Float,
    startRadiusPx: Float,
    screenRadiusPx: Float,
    progress: Float,
): Pair<Rect, Float> {
    val frame = panelRectAt(start, canvasWidth, canvasHeight, progress)
    val radius = panelCornerRadiusAt(
        startRadiusPx = startRadiusPx,
        screenRadiusPx = screenRadiusPx,
        progress = progress,
        frameWidthPx = frame.width,
        frameHeightPx = frame.height,
    )
    return frame to radius
}
