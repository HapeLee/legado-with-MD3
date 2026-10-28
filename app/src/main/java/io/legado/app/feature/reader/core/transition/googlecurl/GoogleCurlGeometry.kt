package io.legado.app.feature.reader.core.transition.googlecurl

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

/** Front-face wave, with its visible travel fitted to the control-point interval.
 * All coordinates here are normalized material/display coordinates, before RTL reflection.
 */
internal class GoogleCurlGeometry(radiusFraction: Float) {
    private val peakAmplitude = radiusFraction.toDouble() * 2.25
    val exitTravel: Double
    private var travel = 0.0
    private var amplitude = 0.0
    private var movement = 0.0
    private var columnU = Float.NaN
    private var columnPerspective = 1.0
    private var columnX = 0.0
    private var columnBrightness = 255

    init {
        require(radiusFraction.isFinite() && radiusFraction in 0.005f..0.3f)
        // At zero travel the right edge is 1; at reference travel 1.05 it is offscreen.
        // The visible right edge decreases strictly for the supported amplitude range.
        var low = 0.0
        var high = 1.05
        repeat(48) {
            val middle = (low + high) * 0.5
            val a = peakAmplitude * minOf(1.0, middle * 5.0)
            val m = maxOf(0.0, middle - 0.05)
            val z = depth(1.0, middle, a)
            val right = (0.5 - a - m) * 2.0 / (2.0 - z) + 0.5
            if (right > 0.0) low = middle else high = middle
        }
        exitTravel = high
    }

    fun update(controlProgress: Float) {
        travel = exitTravel * controlProgress.coerceIn(0f, 1f)
        amplitude = peakAmplitude * minOf(1.0, travel * 5.0)
        movement = maxOf(0.0, travel - 0.05)
        columnU = Float.NaN
    }

    /** Writes a material point; returns the reference front-face brightness. */
    fun project(u: Float, v: Float, width: Float, height: Float,
        vertices: FloatArray, offset: Int): Int {
        // The reference wave's depth, perspective, x and lighting depend on u only.
        // Reuse those terms while visiting a column, retaining all vertical samples
        // for the mesh's piecewise-affine texture mapping. update invalidates them.
        if (u != columnU) {
            val z = depth(u.toDouble(), travel, amplitude)
            columnPerspective = 2.0 / (2.0 - z)
            columnX = (u * (1.0 - amplitude) - movement - 0.5) * columnPerspective + 0.5
            // Nearest-level quantization keeps vanishing depth at full brightness.
            // Truncation darkened the entire sheet as soon as z became positive.
            columnBrightness = (255.0 - z * 90.0).roundToInt().coerceIn(0, 255)
            columnU = u
        }
        vertices[offset] = (columnX * width).toFloat()
        vertices[offset + 1] = (((v - 0.5) * columnPerspective + 0.5) * height).toFloat()
        return columnBrightness
    }

    private fun depth(u: Double, t: Double, a: Double) =
        a * (sin(PI / 0.60 * (u - t)) + 1.1)
}
