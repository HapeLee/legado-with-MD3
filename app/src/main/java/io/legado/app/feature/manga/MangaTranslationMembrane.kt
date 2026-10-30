package io.legado.app.feature.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.HazeBlurStyle
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.*

// Fixed normal directions keep the control polygon's topology stable throughout reveal.
private val membraneNormals = List(32) { index ->
    val angle = index * 2f * PI.toFloat() / 32f
    Offset(cos(angle), sin(angle))
}

/** Convex periodic cubic B-spline, C2 continuous at every join, including the closing seam. */
internal fun translationMembranePath(root: Offset, children: List<Offset>, unit: Float = 1f): Path {
    // Support points on the fan arc fill the space between buttons without a diamond-shaped edge.
    val vectors = children.map { it - root }
    val average = vectors.fold(Offset.Zero) { sum, point -> sum + point }
    val heading = atan2(average.y, average.x)
    val polar = vectors.map { vector ->
        val angle = atan2(vector.y, vector.x) - heading
        atan2(sin(angle), cos(angle)) to vector.getDistance()
    }.sortedBy { it.first }
    val arc = polar.zipWithNext().flatMap { (start, end) ->
        if (end.first - start.first > PI.toFloat()) emptyList() else (1..3).map { index ->
            val fraction = index / 4f
            val angle = heading + start.first + (end.first - start.first) * fraction
            val distance = start.second + (end.second - start.second) * fraction
            root + Offset(cos(angle), sin(angle)) * distance
        }
    }
    val points = (listOf(root) + children + arc).map { it - root }
    val rawSupport = membraneNormals.map { normal ->
        points.maxOf { it.x * normal.x + it.y * normal.y } + 29f * unit
    }
    // Angular convolution is a convex combination of rotated envelopes. It bows the sides
    // smoothly without adding hull vertices. Work relative to root so smoothing cannot move it.
    val support = rawSupport.indices.map { index ->
        fun at(delta: Int) = rawSupport[(index + delta + rawSupport.size) % rawSupport.size]
        (at(-2) + 4f * at(-1) + 6f * at(0) + 4f * at(1) + at(2)) / 16f
    }
    val polygon = membraneNormals.mapIndexed { index, a ->
        val next = (index + 1) % membraneNormals.size
        val b = membraneNormals[next]
        val determinant = a.x * b.y - a.y * b.x
        Offset((support[index] * b.y - a.y * support[next]) / determinant,
            (a.x * support[next] - support[index] * b.x) / determinant)
    }
    // Equal arc-length spacing avoids tight curvature spikes where a long side meets a short
    // shoulder. Keep 64 controls in every frame, including the fully collapsed circle. Padding
    // absorbs convolution/spline shrinkage while still enclosing the 44dp button circles.
    val lengths = polygon.indices.map { (polygon[(it + 1) % polygon.size] - polygon[it]).getDistance() }
    val perimeter = lengths.sum()
    var edge = 0
    var passed = 0f
    val controls = List(64) { index ->
        val distance = perimeter * index / 64f
        while (edge < lengths.lastIndex && passed + lengths[edge] < distance) {
            passed += lengths[edge++]
        }
        val fraction = (distance - passed) / lengths[edge]
        root + polygon[edge] + (polygon[(edge + 1) % polygon.size] - polygon[edge]) * fraction
    }
    return Path().apply {
        controls.indices.forEach { index ->
            val p0 = controls[index]
            val p1 = controls[(index + 1) % controls.size]
            val p2 = controls[(index + 2) % controls.size]
            val p3 = controls[(index + 3) % controls.size]
            if (index == 0) {
                val start = (p0 + p1 * 4f + p2) / 6f
                moveTo(start.x, start.y)
            }
            val c1 = (p1 * 2f + p2) / 3f
            val c2 = (p1 + p2 * 2f) / 3f
            val end = (p1 + p2 * 4f + p3) / 6f
            cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
        }
        close()
    }
}

internal fun translationBubbleRevealPoint(main: Offset, target: Offset, progress: Float, index: Int): Offset {
    val p = progress.coerceIn(0f, 1.12f)
    val vector = target - main
    val bend = (index - 1) * .22f * (1f - p)
    return main + Offset(vector.x * cos(bend) - vector.y * sin(bend),
        vector.x * sin(bend) + vector.y * cos(bend)) * p
}

private class MembraneShape(private val root: Offset, private val children: ImmutableList<Offset>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(translationMembranePath(root * density.density, children.map { it * density.density }, density.density))
}

@Composable
internal fun MangaTranslationMembrane(
    modifier: Modifier,
    root: Offset,
    children: ImmutableList<Offset>,
    size: Size,
    reveal: Float,
    solid: Boolean,
    blurEnabled: Boolean,
    surface: Color,
    haze: HazeState?,
) {
    val shape = remember(root, children) { MembraneShape(root, children) }
    val alpha = reveal.coerceIn(0f, 1f)
    // The backdrop must remain transparent. A zero tint does not clear an opaque backgroundColor.
    val blur = if (!solid && blurEnabled && haze != null) Modifier.hazeBlur(input = HazeInput.Sources(haze),
        style = HazeBlurStyle {
            backgroundColor(Color.Transparent)
            blurRadius(16.dp)
            noiseFactor(0f)
            colorEffects(emptyList())
        }) else Modifier
    Box(modifier.size(size.width.dp, size.height.dp).graphicsLayer { this.alpha = alpha }
        .drawWithCache {
            val path = (shape.createOutline(this.size, layoutDirection, this) as Outline.Generic).path
            // Approximate a Gaussian falloff with 0.5dp radial steps on all supported APIs.
            // Keep the halo outside the glass clip; 44dp layout padding leaves 15dp beyond it.
            val halo = (24 downTo 1).map { index ->
                val distance = index * .5f
                val opacity = (exp(-distance * distance / 32f) - exp(-(distance + .5f).pow(2) / 32f)) * .12f
                Stroke(index.dp.toPx()) to Color(0xFFD7EFFF).copy(alpha = opacity)
            }
            onDrawBehind {
                if (!solid) halo.forEach { (stroke, color) ->
                    drawPath(path, color, style = stroke)
                }
            }
        }
        .clip(shape).then(blur)
        .background(if (solid) Brush.linearGradient(listOf(surface, surface)) else Brush.linearGradient(
            listOf(Color.White.copy(alpha = .36f), surface.copy(alpha = .30f), Color(0xFFD4EBFA).copy(alpha = .34f))))
        .border(.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .30f),
            Color(0xFFD5EDFF).copy(alpha = .06f), Color.White.copy(alpha = .18f))), shape))
}
