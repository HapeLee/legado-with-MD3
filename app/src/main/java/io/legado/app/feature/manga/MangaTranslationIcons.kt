package io.legado.app.feature.manga

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import io.legado.app.R

/** Original-color PNGs on ordinary screens; matching high-contrast silhouettes on e-ink. */
@Composable
internal fun TranslationEntryIcon(main: Boolean, modifier: Modifier, solid: Boolean) {
    if (!solid) {
        Image(painterResource(if (main) R.drawable.ic_manga_translation_glass
            else R.drawable.ic_manga_auto_translation_glass), contentDescription = null, modifier = modifier)
        return
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        scale(size.width / 32f, size.height / 32f, pivot = Offset.Zero) {
            val stroke = Stroke(1.6f, cap = StrokeCap.Round)
            if (main) {
                listOf(Offset(2f, 2f), Offset(13f, 13f)).forEach { origin ->
                    drawRoundRect(ink.copy(alpha = .10f), origin, Size(17f, 17f), CornerRadius(3f))
                    drawRoundRect(ink, origin, Size(17f, 17f), CornerRadius(3f), style = stroke)
                }
                val upper = Path().apply { moveTo(23f, 4f); cubicTo(29f, 4f, 30f, 7f, 30f, 11f)
                    moveTo(27f, 8f); lineTo(30f, 11f); lineTo(31f, 7f) }
                val lower = Path().apply { moveTo(10f, 28f); cubicTo(4f, 28f, 2f, 26f, 2f, 22f)
                    moveTo(0.8f, 25f); lineTo(2f, 22f); lineTo(5f, 25f) }
                drawPath(upper, ink, style = stroke)
                drawPath(lower, ink, style = stroke)
            } else {
                val frame = Path().apply { moveTo(12f, 6f); lineTo(25f, 6f)
                    quadraticTo(28f, 6f, 28f, 9f); lineTo(28f, 26f)
                    quadraticTo(28f, 29f, 25f, 29f); lineTo(7f, 29f)
                    quadraticTo(4f, 29f, 4f, 26f); lineTo(4f, 12f) }
                drawPath(frame, ink, style = stroke)
                drawCircle(ink, 2.7f, Offset(12f, 13f))
                val mountains = Path().apply { moveTo(4f, 26f); lineTo(11f, 19f)
                    lineTo(15f, 23f); lineTo(22f, 15f); lineTo(28f, 23f)
                    lineTo(28f, 27f); quadraticTo(28f, 29f, 25f, 29f)
                    lineTo(7f, 29f); quadraticTo(4f, 29f, 4f, 26f); close() }
                drawPath(mountains, ink)
            }
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink.toArgb()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textSize = if (main) 12f else 13f
                }
                canvas.nativeCanvas.drawText("A", if (main) 5f else 0f, if (main) 15f else 11f, paint)
                if (main) canvas.nativeCanvas.drawText("文", 15f, 27f, paint)
            }
        }
    }
}
