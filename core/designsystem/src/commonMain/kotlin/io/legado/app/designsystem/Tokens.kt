package io.legado.app.designsystem

/**
 * 设计系统 token —— 纯值层（commonMain，零 Compose）。
 *
 * 这是 `:core:designsystem` 的「可跨平台」底座：只承载数值、色值、字号刻度等
 * 与 UI 框架无关的稳定值，供 Compose 映射层（见 [composeMain] 源集的 `Tokens`）消费。
 *
 * 设计约束（对应 docs/dev/kmp-cmp-migration-plan.md §5 不变量 2）：
 * `commonMain` 不依赖 `androidx.compose.*`。因此这里**刻意**不出现 `Dp`/`Color`/
 * `TextStyle`，颜色用 32 位 ARGB `Long`（`0xAARRGGBB`），尺寸用逻辑像素 `Float`，
 * 字号用 sp `Float`。平台 UI 类型一律在 `composeMain` 做窄映射。
 */

/** 间距刻度（dp）。基准 4dp 网格。 */
object Spacing {
    const val xs = 4f
    const val sm = 8f
    const val md = 12f
    const val lg = 16f
    const val xl = 20f
    const val xxl = 24f
}

/** 圆角刻度（dp）。 */
object Radius {
    const val sm = 4f
    const val md = 8f
    const val lg = 12f
    const val xl = 16f
    const val full = 999f
}

/**
 * 色值 token（32 位 ARGB `Long`，`0xAARRGGBB`）。
 *
 * 这里只放与「Material 3 语义色」正交的品牌/中性基色，Material 语义色
 * （primary/surface/error 等）由 `composeMain` 的 `ColorScheme` 派生，避免在
 * 纯值层硬编码一套 Material 语义而失去框架无关性。
 */
object DesignColors {
    // 中性色（Material 3 neutral palette 参考）
    const val Neutral0 = 0xFF000000L
    const val Neutral4 = 0xFF0F0D15L
    const val Neutral10 = 0xFF1A1B1FL
    const val Neutral90 = 0xFFE3E2E6L
    const val Neutral98 = 0xFFF8F9FFL
    const val Neutral99 = 0xFFFFFBFFL
}
