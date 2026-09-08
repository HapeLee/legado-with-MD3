package io.legado.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * 是否应显示分栏（宽屏 / 近方形窗口）。从 `:app` 的 `ui/util/MiuixUtils.kt` 原样下沉——
 * `:core:ui` 的 `effect/BgEffectBackground.kt` 需要它，而该函数只依赖 Compose 的窗口信息。
 *
 * 判定与迁移前一致：宽度 ≥ 840dp，或宽度 ≥ 600dp 且高宽比 < 1.2。
 */
@Composable
fun shouldShowSplitPane(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) {
        val widthDp = windowInfo.containerSize.width.toDp()
        val heightDp = windowInfo.containerSize.height.toDp()
        val ratio = heightDp / widthDp
        widthDp >= 840.dp || (widthDp >= 600.dp && ratio < 1.2f)
    }
}
