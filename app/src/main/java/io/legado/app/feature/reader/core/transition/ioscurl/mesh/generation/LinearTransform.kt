package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/**
 * Internal Double-precision point used while projecting geometry and shadow contours.
 */
internal data class MeshPoint(val x: Double, val y: Double)

/**
 * A 2x2 linear transform with rows [xx, xy] and [yx, yy].
 * Used for page scaling and contour rotation; translation is represented by separate origins.
 * Floating-point operation order is intentional for stable vertex output.
 */
internal data class LinearTransform(
    val xx: Double,
    val xy: Double,
    val yx: Double,
    val yy: Double,
) {
    /**
     * Computes the x component of the transformed vector.
     */
    fun vectorX(x: Double, y: Double) = fma(xx, x, xy * y)

    /**
     * Computes the y component of the transformed vector.
     */
    fun vectorY(x: Double, y: Double) = fma(yx, x, yy * y)

    /**
     * Computes a transformed x coordinate with an explicit zero translation.
     */
    fun affineX(x: Double, y: Double) = 0.0 + fma(x, xx, y * xy)

    /**
     * Computes a transformed y coordinate with an explicit zero translation.
     */
    fun affineY(x: Double, y: Double) = 0.0 + fma(x, yx, y * yy)

    /**
     * Projects a point through the linear transform with the retained homogeneous-coordinate calculation.
     */
    fun projectPoint(x: Double, y: Double): MeshPoint {

        val w = fma(x, 0.0, y * 0.0) + 1.0
        val scale = if (abs(w - 1) < 1e-10) w else if (w > 0) 1 / w else Double.POSITIVE_INFINITY
        return MeshPoint(scale * (0.0 + fma(x, xx, y * xy)), scale * (0.0 + fma(x, yx, y * yy)))
    }

    /**
     * Transforms a point and takes its dot product with the supplied normal.
     */
    fun projectedOnto(nx: Double, ny: Double, x: Double, y: Double) =
        fma(nx, vectorX(x, y), ny * vectorY(x, y))

    /**
     * Inverts this linear transform. Callers must supply an invertible matrix.
     */
    fun inverseLinear(): LinearTransform {
        val reciprocal = 1 / fma(xx, yy, -(xy * yx))
        return LinearTransform(yy * reciprocal, -xy * reciprocal, -yx * reciprocal, xx * reciprocal)
    }

    /**
     * Returns the negated transformed y component used as the rotated tangent x component.
     */
    fun clockwiseX(x: Double, y: Double) = fma(-yx, x, -(yy * y))

    companion object {
        val IDENTITY = uniformScale(1.0)

        /**
         * Creates a uniform scale without translation.
         */
        fun uniformScale(scale: Double) = LinearTransform(scale, 0.0, 0.0, scale)

        /**
         * Creates a rotation from an angle in radians; visual direction follows the caller's axis convention.
         */
        fun rotation(angle: Double): LinearTransform {
            val sine = sin(angle)
            val cosine = cos(angle)
            return LinearTransform(cosine, -sine, sine, cosine)
        }
    }
}
