package io.legado.app.feature.reader.core.gesture

object ReaderGestureSettingsPolicy {
    /** The legacy preference is stored as raw pixels; zero delegates to the platform default. */
    fun touchSlopPx(platformTouchSlopPx: Float, configuredTouchSlopPx: Int): Float =
        configuredTouchSlopPx.takeIf { it > 0 }?.toFloat()
            ?: platformTouchSlopPx.coerceAtLeast(0f)

    /**
     * 长按建立初始选区后，进入 drag 所需的位移阈值。
     *
     * 与 [touchSlopPx] 刻意解耦：`pageTouchSlop` 是“防误触翻页”的灵敏度设置，可配到
     * 1000px；沿用它会让长按后的拖选要移动几十甚至上百像素才开始响应。这里恒用平台
     * slop，保证拖选启动手感不随翻页灵敏度漂移。
     */
    fun selectionDragSlopPx(platformTouchSlopPx: Float): Float =
        platformTouchSlopPx.coerceAtLeast(MIN_SELECTION_DRAG_SLOP_PX)

    private const val MIN_SELECTION_DRAG_SLOP_PX = 1f

    /**
     * 滚动点击翻页是否播放动画。
     *
     * 关闭时一步到位（旧 `noAnim` 分支直接 `curPage.scroll(offset)`）；开启时按时长
     * 插值（旧 `PageDelegate.startScroll`），不再是固定帧数。
     */
    fun animatesScrollPage(noAnimation: Boolean): Boolean = !noAnimation
}
