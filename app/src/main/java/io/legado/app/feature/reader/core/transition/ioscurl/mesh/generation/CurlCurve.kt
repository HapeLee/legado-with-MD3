package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/**
 * Evaluates the calibrated odd polynomial used by the page-shape model.
 * This is a visual curve approximation, not a general-purpose replacement for sin.
 */
internal fun curlSine(theta: Double): Float {
    val a = theta.toFloat()
    val a2 = a * a
    return a * fma(a2, fma(a2, 0.0054653967f, -0.1534627f), 0.9844168f)
}

/**
 * Evaluates the shared even-polynomial core used for displacement slopes and shadow spread.
 * The input is the squared local bend angle, not the angle itself.
 */
internal fun curlCosineCore(squared: Float): Float =
    fma(squared, fma(squared, 0.000969668f, -0.03922769f), 0.4953497f)

/**
 * Returns the Float displacement along the curl normal for a sample at x.
 *
 * The local bend angle is (center - x) / radius + PI/2. Before minimumAngle the
 * page stays flat; within the bend it follows the calibrated curve; beyond
 * maximumAngle it continues along the terminal slope.
 *
 * @param onSine Receives the clamped bend's curve-sine value for lighting calculations.
 * Preserve Float rounding points when modifying this model to avoid changing seams.
 */
internal inline fun curlDisplacement(
    x: Double,
    center: Double,
    radius: Double,
    minimumAngle: Double,
    maximumAngle: Double,
    onSine: (Double) -> Unit = {},
): Float {
    val theta = (center - x) / radius + PI / 2
    val t = max(minimumAngle, min(theta, maximumAngle))
    val sine = curlSine(t).toDouble()
    var result = 0f
    if (!(theta < minimumAngle)) {
        val displacement: Double
        if (theta < maximumAngle) displacement = ((theta - sine) * radius).toFloat().toDouble()
        else {
            val a = maximumAngle.toFloat()
            val a2 = a * a
            val ns =
                a *
                    fma(
                        a2,
                        fma(a2, -0.0054653967f, 0.1534627f),
                        -0.9844168f,
                    )
            val edge = ((maximumAngle + ns.toDouble()) * radius).toFloat().toDouble()
            val distance = fma(PI / 2 - maximumAngle, radius, center) - x
            val slope = fma(a2, curlCosineCore(a2), 0.0013932586f)
            displacement = fma(distance, slope.toDouble(), edge)
        }
        val correction = ((curlSine(minimumAngle).toDouble() - minimumAngle) * radius).toFloat()
        result = (displacement + correction.toDouble()).toFloat()
    }
    onSine(sine)
    return result
}
