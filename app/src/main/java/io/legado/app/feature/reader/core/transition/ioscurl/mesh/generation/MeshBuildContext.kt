package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.withSign
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMeshFrame
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMeshInput
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect

/**
 * Normal and tangent basis for one coordinate space.
 * The normal points along curl travel; the tangent follows a cross-section of the sheet.
 * Logical bases describe page geometry; texture bases include the output scale.
 */
internal data class CurlBasis(
    val normalX: Double,
    val normalY: Double,
    val tangentX: Double,
    val tangentY: Double,
)

/**
 * Validated per-frame inputs shared by all generation stages.
 *
 * @property source Source rectangle in scaled page coordinates.
 * @property logical Curl basis in logical geometry coordinates.
 * @property texture Basis used for source/texture intersections, including scale.
 * @property pageToLogical Inverse logical-to-scaled-page transform.
 * @property clip Final output clipping rectangle.
 * Progress is clamped to 0..1 here; angular limits describe local bending, not the page orientation.
 */
internal data class MeshBuildContext(
    val input: CurlMeshInput,
    val source: CurlRect,
    val logical: CurlBasis,
    val texture: CurlBasis,
    val pageToLogical: LinearTransform = LinearTransform.IDENTITY,
    val clip: CurlRect = source,
) {
    val time = input.values.time.coerceIn(0.0, 1.0)
    val radius = input.values.radius
    val minimumAngle = input.startAngle
    val maximumAngle = max(input.startAngle, input.endAngle)
    val shadowBounds =
        input.shadowBounds
            ?: if (input.width <= 2 || input.height <= 2) CurlRect(1.0, 1.0, 0.0, 0.0)
            else CurlRect(1.0, 1.0, input.width - 2, input.height - 2)
}

/**
 * Validates public inputs, intersects clipping bounds and stabilizes nearly axis-aligned bases.
 * The final build context is passed to the stage coordinator.
 */
internal fun MeshGenerator.build(
    input: CurlMeshInput,
    coalesceTriangleStrips: Boolean = false,
): CurlMeshFrame {
    require(
        input.width.isFinite() &&
            input.height.isFinite() &&
            input.width in 1.0..MeshConstants.MAX_DIMENSION &&
            input.height in 1.0..MeshConstants.MAX_DIMENSION
    )
    require(
        input.scale.isFinite() &&
            input.scale in MeshConstants.MIN_SCALE..MeshConstants.MAX_SCALE &&
            input.values.radius > 0 &&
            input.values.radius <= MeshConstants.MAX_DIMENSION
    )
    require(
        input.values.time.isFinite() &&
            input.values.angle.isFinite() &&
            input.startAngle.isFinite() &&
            input.endAngle.isFinite()
    )
    require(input.shadowSoftness.isFinite() && input.shadowSoftness in 1.0..3.0)
    // Only output extents are rounded up; the logical dimensions remain unchanged.
    val pixelWidth = ceil(input.width * input.scale).toInt()
    val pixelHeight = ceil(input.height * input.scale).toInt()
    val source =
        input.surfaceRect ?: CurlRect(0.0, 0.0, pixelWidth.toDouble(), pixelHeight.toDouble())
    val target = input.targetBounds ?: CurlRect(0.0, 0.0, source.width, source.height)
    val requestedClip = input.clipRect ?: target
    val clipLeft = max(target.x, requestedClip.x)
    val clipTop = max(target.y, requestedClip.y)
    val clip =
        CurlRect(
            clipLeft,
            clipTop,
            max(
                0.0,
                min(target.x + target.width, requestedClip.x + requestedClip.width) - clipLeft,
            ),
            max(
                0.0,
                min(target.y + target.height, requestedClip.y + requestedClip.height) - clipTop,
            ),
        )

    // Snap nearly horizontal/vertical folds to exact axes to stabilize reciprocal-direction clipping.
    var sine = sin(input.values.angle.toFloat())

    var cosine = cos(input.values.angle.toFloat())
    if (
        abs(sine.toDouble()) < MeshConstants.AXIS_SNAP_EPSILON ||
            abs(cosine.toDouble()) < MeshConstants.AXIS_SNAP_EPSILON
    ) {
        sine = roundAwayFromZero(sine)
        cosine = roundAwayFromZero(cosine)
    }
    var logicalNormalX = cosine.toDouble()
    var logicalNormalY = sine.toDouble()
    var logicalTangentX = -sine.toDouble()
    var logicalTangentY = cosine.toDouble()

    val logicalToPage = LinearTransform.uniformScale(input.scale)
    val pageToLogical = logicalToPage.inverseLinear()
    var normalX = logicalToPage.vectorX(cosine.toDouble(), sine.toDouble())
    var normalY = logicalToPage.vectorY(cosine.toDouble(), sine.toDouble())
    var tangentX = logicalToPage.vectorX(-sine.toDouble(), cosine.toDouble())
    val tangentY = logicalToPage.vectorY(-sine.toDouble(), cosine.toDouble())
    var textureTangentY = tangentY
    if (
        abs(tangentX) < MeshConstants.TANGENT_SNAP_EPSILON ||
            abs(tangentY) < MeshConstants.TANGENT_SNAP_EPSILON
    ) {
        // Preserve tangent length and sign while removing its nearly zero component.
        val length = sqrt(fma(tangentX, tangentX, tangentY * tangentY).toFloat())
        tangentX =
            if (abs(tangentX) < MeshConstants.TANGENT_SNAP_EPSILON) 0.0
            else length.withSign(tangentX.toFloat()).toDouble()
        val snappedTangentY =
            if (abs(tangentY) < MeshConstants.TANGENT_SNAP_EPSILON) 0.0
            else length.withSign(tangentY.toFloat()).toDouble()
        val snappedNormalX = pageToLogical.vectorX(normalX, normalY)
        val snappedNormalY = pageToLogical.vectorY(normalX, normalY)
        val snappedTangentX = pageToLogical.clockwiseX(normalX, normalY)
        logicalNormalX = snappedNormalX
        logicalNormalY = snappedNormalY
        logicalTangentX = snappedTangentX
        logicalTangentY = snappedNormalX
        normalX = logicalToPage.vectorX(snappedNormalX, snappedNormalY)
        normalY = logicalToPage.vectorY(snappedNormalX, snappedNormalY)
        textureTangentY = snappedTangentY
    }
    return generateMesh(
        MeshBuildContext(
            input,
            source,
            CurlBasis(logicalNormalX, logicalNormalY, logicalTangentX, logicalTangentY),
            CurlBasis(normalX, normalY, tangentX, textureTangentY),
            pageToLogical,
            clip,
        ),
        coalesceTriangleStrips,
    )
}

/**
 * Rounds to the nearest integer, with half values away from zero, for axis snapping.
 */
private fun roundAwayFromZero(value: Float) =
    if (value < 0) ceil(value - 0.5f) else floor(value + 0.5f)
