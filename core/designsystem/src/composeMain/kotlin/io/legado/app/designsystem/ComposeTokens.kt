package io.legado.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Compose 映射层（专用 UI 源集，非 commonMain）。
 *
 * 把 [Spacing]/[Radius]/[DesignColors] 的纯值 token 映射为 Compose 类型。
 * `androidx.compose.*` 依赖被关在本源集，`commonMain` 保持零 Compose（G2 纯度守卫）。
 */
object DesignSpacing {
    val xs get() = Spacing.xs.dp
    val sm get() = Spacing.sm.dp
    val md get() = Spacing.md.dp
    val lg get() = Spacing.lg.dp
    val xl get() = Spacing.xl.dp
    val xxl get() = Spacing.xxl.dp
}

object DesignRadius {
    val sm get() = Radius.sm.dp
    val md get() = Radius.md.dp
    val lg get() = Radius.lg.dp
    val xl get() = Radius.xl.dp
    val full get() = Radius.full.dp
}

/** 把纯值 ARGB `Long` 映射为 Compose [Color]。 */
fun designColor(argb: Long): Color = Color(argb)
