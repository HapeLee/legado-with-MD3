package io.legado.app.feature.reader.core.gesture

object ReaderGestureSettingsPolicy {
    /** The legacy preference is stored as raw pixels; zero delegates to the platform default. */
    fun touchSlopPx(platformTouchSlopPx: Float, configuredTouchSlopPx: Int): Float =
        configuredTouchSlopPx.takeIf { it > 0 }?.toFloat()
            ?: platformTouchSlopPx.coerceAtLeast(0f)

    /** Only discrete scroll-page commands honor this setting; direct dragging still flings. */
    fun scrollPageAnimationSteps(noAnimation: Boolean): Int = if (noAnimation) 1 else 18
}
