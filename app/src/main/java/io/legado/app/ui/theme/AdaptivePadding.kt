package io.legado.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.legado.app.ui.theme.LegadoTheme.composeEngine

/** Space occupied by a host's bottom overlay, including its remaining system inset. */
internal val LocalBottomOverlayPadding = compositionLocalOf { 0.dp }

/** Measure and draw into a parent's symmetric padding without changing the space it reserves. */
internal fun Modifier.extendIntoHorizontalPadding(padding: Dp): Modifier {
    if (padding == 0.dp) return this
    return layout { measurable, constraints ->
        val inset = if (constraints.hasBoundedWidth) padding.roundToPx() else 0
        val child = measurable.measure(constraints.offset(horizontal = 2 * inset))
        layout(child.width - 2 * inset, child.height) {
            child.place(-inset, 0)
        }
    }
}

@Composable
fun Modifier.adaptiveHorizontalPadding(): Modifier {
    val horizontal = if (composeEngine == ComposeEngine.Miuix) 12.dp else 16.dp
    return this.padding(horizontal = horizontal)
}

@Composable
fun Modifier.adaptiveHorizontalPaddingTab(): Modifier {
    val start = if (composeEngine == ComposeEngine.Miuix) 12.dp else 0.dp
    val end = if (composeEngine == ComposeEngine.Miuix) 12.dp else 16.dp
    return this.padding(start = start, end = end)
}

@Composable
fun Modifier.adaptiveHorizontalPadding(
    vertical: Dp,
): Modifier {
    val horizontal = if (composeEngine == ComposeEngine.Miuix) 12.dp else 16.dp
    return this.padding(horizontal = horizontal, vertical = vertical)
}

@Composable
fun Modifier.adaptiveVerticalPadding(): Modifier {
    val horizontal = if (composeEngine == ComposeEngine.Miuix) 12.dp else 8.dp
    return this.padding(horizontal = horizontal)
}

@Composable
fun adaptiveHorizonalPadding(): PaddingValues {
    val horizontal = if (composeEngine == ComposeEngine.Miuix) 12.dp else 16.dp
    return PaddingValues(
        horizontal = horizontal
    )
}

@Composable
fun adaptiveContentPaddingOnlyVertical(
    top: Dp,
    bottom: Dp
): PaddingValues {
    val adjustedTop = if (composeEngine == ComposeEngine.Miuix) top + 8.dp else top
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
    val horizontal = if (composeEngine == ComposeEngine.Miuix) 12.dp else 16.dp
    val adjustedTop = if (composeEngine == ComposeEngine.Miuix) top + 12.dp else top + 16.dp
    return PaddingValues(
        top = adjustedTop,
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
    val horizontal =
        if (composeEngine == ComposeEngine.Miuix) miuixHorizontal else m3Horizontal
    val adjustedTop = if (composeEngine == ComposeEngine.Miuix) top + 12.dp else top + 16.dp
    return PaddingValues(
        top = adjustedTop,
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
    val adjustedTop = if (composeEngine == ComposeEngine.Miuix) top + 12.dp else top + 16.dp
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = horizontal,
        end = horizontal
    )
}

/**
 * 书籍封面上可见区域内缩的量（非沉浸），与 `BookshelfItemCover` 的 padding 同源。
 *
 * 口径是「封面到屏幕边的距离」，所以网格内容边距必须减掉这一层：
 * 直接把 contentPadding 当封面边距会凭空多出 4dp；以后封面为水波纹再扩边也只改这里。
 */
internal val BookshelfCoverInnerInset: Dp = 4.dp

/**
 * 书架书籍封面到屏幕边缘的目标距离：Material / Mochi / Oxygen 16dp，Miuix / Coui 12dp。
 */
@Composable
fun bookshelfCoverScreenMargin(): Dp = when (composeEngine) {
    ComposeEngine.Miuix, ComposeEngine.Coui -> 12.dp
    else -> 16.dp
}

/**
 * 书籍网格左右内容边距 = 封面目标边距 − 封面自身内边距。
 *
 * 书架里所有「铺到屏幕边、但内容仍要和封面像素对齐」的东西（就地展开的系列抽屉等）
 * 都必须取这一份口径，不要自己重算引擎分支。
 */
@Composable
fun bookshelfCoverGridSidePadding(): Dp = bookshelfCoverScreenMargin() - BookshelfCoverInnerInset

@Composable
fun adaptiveContentPaddingBookshelf(
    top: Dp,
    bottom: Dp,
    horizontal: Dp,
    /** 沉浸网格：左右贴到屏幕边（不下沉引擎默认边距），只保留上下内边距。 */
    edgeToEdge: Boolean = false,
): PaddingValues {
    val adjustedTop = if (composeEngine == ComposeEngine.Miuix) top + 12.dp else top + 8.dp
    val sidePadding = when {
        edgeToEdge -> 0.dp
        composeEngine == ComposeEngine.Miuix -> 6.dp + horizontal
        else -> 4.dp + horizontal
    }
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = sidePadding,
        end = sidePadding
    )
}

/**
 * 书籍网格的 contentPadding。
 *
 * 与 [adaptiveContentPaddingBookshelf] 的差别只在左右：这里按「封面到屏幕边的目标距离」推导，
 * 而不是「引擎基础偏移 + horizontal」——后者在 Miuix/Coui 下会让封面离屏幕偏大。
 */
@Composable
fun adaptiveContentPaddingBookshelfCoverGrid(
    top: Dp,
    bottom: Dp,
    /** 沉浸网格：封面贴屏幕边，不留左右边距 */
    immersive: Boolean = false,
): PaddingValues {
    val adjustedTop = if (composeEngine == ComposeEngine.Miuix) top + 12.dp else top + 8.dp
    val sidePadding = if (immersive) 0.dp else bookshelfCoverGridSidePadding()
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = sidePadding,
        end = sidePadding
    )
}
