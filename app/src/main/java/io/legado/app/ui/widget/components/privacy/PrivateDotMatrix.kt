package io.legado.app.ui.widget.components.privacy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * 锁定封面上的「动态点阵」遮罩。
 *
 * 一片随时间起伏的点阵，比纯色遮罩更能表达"这里是被刻意遮住的"，
 * 也让人一眼看出不是封面加载失败。
 *
 * 实现全走 Compose 标准能力：[rememberInfiniteTransition] 驱动相位、
 * [Canvas] 逐点绘制——没有自定义 Shader，也不依赖平台特效。
 *
 * 开销：点数与 [cellSize] 的平方成反比（10dp 格子下，一张卡片一两百个点），
 * 且只在锁定态渲染；点阵画在模糊层之外，所以它本身是清晰的，不会被封面的模糊一起糊掉。
 */
@Composable
fun PrivateDotMatrix(
    color: Color,
    modifier: Modifier = Modifier,
    cellSize: Dp = 10.dp,
    dotRadius: Dp = 1.6.dp,
    baseAlpha: Float = 0.10f,
    pulseAlpha: Float = 0.16f,
) {
    val transition = rememberInfiniteTransition(label = "private-dot-matrix")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "private-dot-phase",
    )
    Canvas(modifier = modifier) {
        val cell = cellSize.toPx()
        if (cell <= 0f) return@Canvas
        val radius = dotRadius.toPx()
        val rows = ceil(size.height / cell).toInt()
        val columns = ceil(size.width / cell).toInt()
        for (row in 0..rows) {
            val y = row * cell
            // 奇数行错开半格：更像织出来的网，而不是规整的网格
            val rowOffset = if (row % 2 == 0) 0f else cell / 2f
            for (column in 0..columns) {
                val x = column * cell + rowOffset
                // 相邻点之间留相位差，形成一道斜向缓缓流动的波
                val wave = sin(phase + (row + column) * 0.55f)
                val alpha = baseAlpha + pulseAlpha * ((wave + 1f) / 2f)
                drawCircle(
                    color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = radius,
                    center = Offset(x, y),
                )
            }
        }
    }
}
