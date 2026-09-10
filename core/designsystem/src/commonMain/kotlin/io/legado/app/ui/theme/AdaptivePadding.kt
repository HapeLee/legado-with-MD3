package io.legado.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 从 `:core:ui` 整体搬来（M1-2）：这是 `:core:designsystem` 转真 CMP 后的第一块真实内容。
// 消费方目前是 `:feature:tagrules` 等（包名不变，所以调用方 import 零改动）。
//
// 迁移时只做两件事，都不改行为：
//   1. 引擎判定的来源由 Android 侧的 `LegadoTheme.composeEngine` 换成共享的 `LocalComposeEngine`；
//   2. 把原本散在各函数里的「引擎 → 数字」条件式收敛成 [AdaptiveSpacing] 一处，
//      这样这张刻度表可以被 `AdaptiveSpacingTest` 逐项锁住（也让将来做 token 层时有唯一落点）。
//
// 下面两处是**原样保留的现状**：它们看起来像笔误，但属于既有行为，迁移切片不顺手改语义
// （要修应单独一片并先有 characterization 测试兜住）：
//   - `adaptiveVerticalPadding()` 内部写的是 `padding(horizontal = horizontal)`；
//   - 函数名 `adaptiveHorizonalPadding`（少一个 t）。

/**
 * 引擎条件式间距刻度表：每个成员对应一个 padding 函数的条件分支。
 *
 * 本仓的间距**不是**一套常量刻度，而是按引擎分叉（Miuix / Material3 各一套）。所以这里
 * 存的是"判定"而不是"数字 token"——把数字抽成不带引擎语义的常量会把真实语义抹平。
 */
internal object AdaptiveSpacing {

    /** 内容 / 列表横向。 */
    fun horizontal(engine: ComposeEngine): Dp = if (engine == ComposeEngine.Miuix) 12.dp else 16.dp

    /** Tab 的起点。 */
    fun tabStart(engine: ComposeEngine): Dp = if (engine == ComposeEngine.Miuix) 12.dp else 0.dp

    /** Tab 的终点。 */
    fun tabEnd(engine: ComposeEngine): Dp = if (engine == ComposeEngine.Miuix) 12.dp else 16.dp

    /** `adaptiveVerticalPadding` 用的横向值（现状即如此，见文件头注记）。 */
    fun verticalHorizontal(engine: ComposeEngine): Dp =
        if (engine == ComposeEngine.Miuix) 12.dp else 8.dp

    /** 仅纵向内容时，顶部额外增量。 */
    fun onlyVerticalTop(engine: ComposeEngine): Dp = if (engine == ComposeEngine.Miuix) 8.dp else 0.dp

    /** 内容顶部额外增量。 */
    fun contentTop(engine: ComposeEngine): Dp = if (engine == ComposeEngine.Miuix) 12.dp else 16.dp

    /** 书架内容顶部额外增量。 */
    fun bookshelfTop(engine: ComposeEngine): Dp = if (engine == ComposeEngine.Miuix) 12.dp else 8.dp

    /** 书架横向额外增量（叠加在传入的 horizontal 之上）。 */
    fun bookshelfHorizontal(engine: ComposeEngine): Dp =
        if (engine == ComposeEngine.Miuix) 6.dp else 4.dp
}

@Composable
fun Modifier.adaptiveHorizontalPadding(): Modifier {
    val horizontal = AdaptiveSpacing.horizontal(LocalComposeEngine.current)
    return this.padding(horizontal = horizontal)
}

@Composable
fun Modifier.adaptiveHorizontalPaddingTab(): Modifier {
    val engine = LocalComposeEngine.current
    return this.padding(start = AdaptiveSpacing.tabStart(engine), end = AdaptiveSpacing.tabEnd(engine))
}

@Composable
fun Modifier.adaptiveHorizontalPadding(
    vertical: Dp,
): Modifier {
    val horizontal = AdaptiveSpacing.horizontal(LocalComposeEngine.current)
    return this.padding(horizontal = horizontal, vertical = vertical)
}

@Composable
fun Modifier.adaptiveVerticalPadding(): Modifier {
    val horizontal = AdaptiveSpacing.verticalHorizontal(LocalComposeEngine.current)
    return this.padding(horizontal = horizontal)
}

@Composable
fun adaptiveHorizonalPadding(): PaddingValues {
    val horizontal = AdaptiveSpacing.horizontal(LocalComposeEngine.current)
    return PaddingValues(
        horizontal = horizontal
    )
}

@Composable
fun adaptiveContentPaddingOnlyVertical(
    top: Dp,
    bottom: Dp
): PaddingValues {
    val adjustedTop = top + AdaptiveSpacing.onlyVerticalTop(LocalComposeEngine.current)
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = 0.dp,
        end = 0.dp
    )
}

@Composable
fun adaptiveContentPadding(
    top: Dp,
    bottom: Dp
): PaddingValues {
    val engine = LocalComposeEngine.current
    val horizontal = AdaptiveSpacing.horizontal(engine)
    return PaddingValues(
        top = top + AdaptiveSpacing.contentTop(engine),
        bottom = bottom,
        start = horizontal,
        end = horizontal
    )
}

@Composable
fun adaptiveContentPadding(
    top: Dp,
    bottom: Dp,
    miuixHorizontal: Dp,
    m3Horizontal: Dp
): PaddingValues {
    val engine = LocalComposeEngine.current
    val horizontal = if (engine == ComposeEngine.Miuix) miuixHorizontal else m3Horizontal
    return PaddingValues(
        top = top + AdaptiveSpacing.contentTop(engine),
        bottom = bottom,
        start = horizontal,
        end = horizontal
    )
}

@Composable
fun adaptiveContentPadding(
    top: Dp,
    bottom: Dp,
    horizontal: Dp
): PaddingValues {
    val adjustedTop = top + AdaptiveSpacing.contentTop(LocalComposeEngine.current)
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = horizontal,
        end = horizontal
    )
}

@Composable
fun adaptiveContentPaddingBookshelf(
    top: Dp,
    bottom: Dp,
    horizontal: Dp
): PaddingValues {
    val engine = LocalComposeEngine.current
    val adjustedHorizontal = AdaptiveSpacing.bookshelfHorizontal(engine) + horizontal
    return PaddingValues(
        top = top + AdaptiveSpacing.bookshelfTop(engine),
        bottom = bottom,
        start = adjustedHorizontal,
        end = adjustedHorizontal
    )
}
