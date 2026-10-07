package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay

/**
 * 正文内新做的悬浮卡片（分配角色 / 分配表 / AI 分配角色）的公共外观。
 *
 * 圆角、模糊、液态玻璃、着色都取底栏那一套 [ReadMenuConfig]（和官方底栏同一个数据源），
 * 用户在「顶/底栏布局」里改的设置这里自动跟着变。没设着色也没开模糊时
 * 保持 surfaceContainerHigh 卡片。
 *
 * 后面新加的正文内菜单要「沿用底栏布局设置」，套这个 Composable 就行。
 */
@Composable
fun CastSheetCard(
    menuConfig: ReadMenuConfig?,
    modifier: Modifier = Modifier,
    /** 卡片是否处于打开态。宿主用 [rememberSheetAlive] 让它在关闭后再活一小段，退场动画才有地方播。 */
    visible: Boolean = true,
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
    // 开合过渡抄官方对话框那一套窗口动画（正文里「离线缓存」那种 AlertDialog 就是它）：
    // 淡入 120ms、从 0.8 放大 180ms，两样都延后 40ms 起步；收起 150ms 淡出同时缩回 0.8。
    // 首帧先按关闭态挂上，下一帧再开：进场动画要有起点，否则一上来就是终值、什么都不动。
    // 用淡入+缩放而非 spring 回弹：官方窗口动画不弹，观感要与它一致。
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    val opened = armed && visible
    val appear = animateFloatAsState(
        targetValue = if (opened) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (opened) ENTER_ALPHA_MS else EXIT_MS,
            delayMillis = if (opened) ENTER_DELAY_MS else 0,
        ),
        label = "castSheetAlpha",
    )
    val pop = animateFloatAsState(
        targetValue = if (opened) 1f else DIALOG_SCALE,
        animationSpec = tween(
            durationMillis = if (opened) ENTER_SCALE_MS else EXIT_MS,
            delayMillis = if (opened) ENTER_DELAY_MS else 0,
        ),
        label = "castSheetScale",
    )
    Surface(
        modifier = modifier
            .then(effectModifier)
            .graphicsLayer {
                alpha = appear.value
                scaleX = pop.value
                scaleY = pop.value
            },
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

// Material3 对话框的窗口动画口径（AlertDialog 的 dialogTransitionSpec 默认值）
private const val ENTER_DELAY_MS = 40
private const val ENTER_ALPHA_MS = 120
private const val ENTER_SCALE_MS = 180
private const val EXIT_MS = 150
private const val DIALOG_SCALE = 0.8f

/**
 * 悬浮卡片退场期间的「还要继续 compose」标记：show 撤下后再多留 [exitMillis]。
 *
 * 正文内这几张卡片的宿主一律是 `if (!show) return`，一撤整棵树就没了，淡出根本播不出来
 * （官方那侧也是这个套路：ChangeChapterSourceSheet 先本地关掉、动画跑完再清 sheet）。
 * 宿主拿这个返回值决定要不要继续挂树，真正的开关状态仍然用 `show` 传给 [CastSheetCard]。
 */
@Composable
fun rememberSheetAlive(show: Boolean, exitMillis: Long = 180L): Boolean {
    var alive by remember { mutableStateOf(show) }
    LaunchedEffect(show) {
        if (show) {
            alive = true
        } else {
            delay(exitMillis)
            alive = false
        }
    }
    return alive
}

/** 退场那 180ms 里宿主会把行号清成 -1：记住最后一次有效值，卡片不能在半路换成空内容。 */
@Composable
fun rememberSheetArg(show: Boolean, value: Int): Int {
    var held by remember { mutableStateOf(value) }
    if (show && value >= 0) held = value
    return held
}

/** 整屏遮罩的淡入淡出，与卡片同一条窗口动画口径；宿主贴在遮罩 Box 的 graphicsLayer 上。 */
@Composable
fun rememberSheetScrimAlpha(visible: Boolean): Float {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    return animateFloatAsState(
        targetValue = if (armed && visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visible) ENTER_ALPHA_MS else EXIT_MS,
            delayMillis = if (visible) ENTER_DELAY_MS else 0,
        ),
        label = "castSheetScrim",
    ).value
}
