package io.legado.app.ui.platform

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier

/**
 * Android 实现：直接转发 `Modifier.systemGestureExclusion()`。
 *
 * 与搬进 commonMain 之前**逐字等价**——声明这块区域不被系统返回手势
 * （predictive back / 边缘滑动）抢走，等于 `View.setSystemGestureExclusionRects`。
 * 本切片在 Android 侧的行为变化为零。
 */
actual fun Modifier.systemGestureExclusionCompat(): Modifier = this.systemGestureExclusion()
