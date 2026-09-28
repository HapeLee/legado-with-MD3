package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.ui.book.read.ReadMenuColors
import io.legado.app.ui.book.read.ReadMenuConfig
import io.legado.app.ui.book.read.readMenuLiquidGlass
import io.legado.app.ui.book.read.readMenuTextColor
import io.legado.app.ui.book.read.readMenuTintColor
import io.legado.app.ui.book.read.toReaderMenuTintStyle
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.reader.ReaderMenuEffect
import io.legado.app.ui.widget.components.reader.ReaderMenuPlacement
import io.legado.app.ui.widget.components.reader.ReaderMenuVisualState
import io.legado.app.ui.widget.components.reader.readerMenuHazeEffect
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlassAvailable
import io.legado.app.ui.widget.components.reader.readerMenuSurfaceBrush

/**
 * 正文内新做的悬浮卡片（分配角色 / 分配表 / AI 分配角色）的公共外观。
 *
 * 圆角、模糊、液态玻璃、着色都取底栏那一套 [ReadMenuConfig]（和官方底栏同一个数据源），
 * 用户在「顶/底栏布局」里改的设置这里自动跟着变。没设着色也没开模糊时保持原来的
 * surfaceContainerHigh 卡片，视觉与改动前一致。
 *
 * 后面新加的正文内菜单要「沿用底栏布局设置」，套这个 Composable 就行。
 */
@Composable
fun CastSheetCard(
    menuConfig: ReadMenuConfig?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val config = menuConfig
    val corner = (config?.readMenuBottomCornerRadius ?: 28).dp
    val shape = RoundedCornerShape(corner)
    val visuals = LocalCastSheetVisuals.current
    val hazeState = visuals.hazeState
    val backdrop = visuals.backdrop
    val mode = config?.readMenuBottomBarBlurMode ?: ReadMenuBlurMode.None
    val useGlass = config != null && mode == ReadMenuBlurMode.LiquidGlass &&
            readerMenuLiquidGlassAvailable(backdrop)
    val useHaze = config != null && mode == ReadMenuBlurMode.Haze && hazeState != null
    val tint = config?.let { readMenuTintColor(it) }
    val base = tint ?: LegadoTheme.colorScheme.surfaceContainerHigh
    val contentColor = config?.let { readMenuTextColor(it) } ?: Color.Unspecified
    val visualState = ReaderMenuVisualState(
        effect = when {
            useGlass -> ReaderMenuEffect.LiquidGlass
            useHaze -> ReaderMenuEffect.Haze
            else -> ReaderMenuEffect.None
        },
        tintStyle = (config?.readMenuBottomBarBlurStyle ?: ReadMenuBlurStyle.Solid)
            .toReaderMenuTintStyle(),
        styleEnabled = true,
        tintAllowed = true,
        tintFill = true,
    )
    // 效果/着色由 modifier 画，Surface 本身透明，否则会把模糊和玻璃盖掉
    val painted = useGlass || useHaze || tint != null
    val effectModifier = when {
        useGlass -> Modifier.readMenuLiquidGlass(
            backdrop = backdrop,
            colors = ReadMenuColors(base, Color.Unspecified),
            shape = shape,
            useTopBarStyle = false,
            useLens = corner > 0.dp,
            menuConfig = config!!,
        )

        useHaze -> Modifier
            .clip(shape)
            .readerMenuHazeEffect(
                state = hazeState!!,
                visualState = visualState,
                placement = ReaderMenuPlacement.Bottom,
                baseColor = base,
                tintColor = tint,
                blurRadius = config!!.readMenuBlurRadius,
                surfaceAlpha = config.readMenuBlurAlpha,
            )

        tint != null -> Modifier
            .clip(shape)
            .background(
                readerMenuSurfaceBrush(
                    style = visualState.tintStyle,
                    placement = ReaderMenuPlacement.Bottom,
                    color = base,
                    alpha = config!!.readMenuBlurAlpha.coerceIn(0, 100) / 100f,
                )
            )

        else -> Modifier
    }
    Surface(
        modifier = modifier.then(effectModifier),
        shape = shape,
        color = if (painted) Color.Transparent else LegadoTheme.colorScheme.surfaceContainerHigh,
        contentColor = contentColor,
        tonalElevation = if (painted) 0.dp else 3.dp,
        content = content,
    )
}

/**
 * 阅读器给正文内悬浮窗准备的模糊/玻璃数据源，就是官方底栏用的那一份
 * （`menuBackdrop` + `menuHazeState`，在 ReadBookRouteScreen 里 provide）。
 */
@Immutable
data class CastSheetVisuals(
    val backdrop: Backdrop? = null,
    val hazeState: HazeState? = null,
)

val LocalCastSheetVisuals = compositionLocalOf { CastSheetVisuals() }
