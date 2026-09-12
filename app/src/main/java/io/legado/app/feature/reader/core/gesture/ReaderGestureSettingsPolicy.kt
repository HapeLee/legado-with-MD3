package io.legado.app.feature.reader.core.gesture

object ReaderGestureSettingsPolicy {
    /** The legacy preference is stored as raw pixels; zero delegates to the platform default. */
    fun touchSlopPx(platformTouchSlopPx: Float, configuredTouchSlopPx: Int): Float =
        configuredTouchSlopPx.takeIf { it > 0 }?.toFloat()
            ?: platformTouchSlopPx.coerceAtLeast(0f)

    /**
     * 滚动点击翻页是否播放动画。
     *
     * 关闭时一步到位（旧 `noAnim` 分支直接 `curPage.scroll(offset)`）；开启时按时长
     * 插值（旧 `PageDelegate.startScroll`），不再是固定帧数。
     */
    fun animatesScrollPage(noAnimation: Boolean): Boolean = !noAnimation
}
