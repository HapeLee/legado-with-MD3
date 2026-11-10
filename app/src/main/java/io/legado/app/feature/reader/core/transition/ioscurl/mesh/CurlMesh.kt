package io.legado.app.feature.reader.core.transition.ioscurl.mesh

import io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation.MeshGenerator
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation.build
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues

/**
 * Unpremultiplied RGBA material color, normally with channels in 0..1.
 * Mesh packing premultiplies RGB by alpha and clamps quantized channels to 0..255.
 */
data class CurlColor(val red: Double, val green: Double, val blue: Double, val alpha: Double)

/**
 * Colors used for the page faces, back-face lighting and cast shadow.
 * The generator converts these to premultiplied half-float colors once per build.
 *
 * @property front Tint of the front-facing curved surface.
 * @property back Base tint for the back-facing curved surface.
 * @property backShade Back-face lighting tint blended with back according to local bend.
 * @property shadow Tint of the cast shadow and front shading region.
 */
data class CurlMaterial(
    val front: CurlColor = CurlColor(1.0, 1.0, 1.0, 1.0),
    val back: CurlColor = CurlColor(244.0 / 255, 244.0 / 255, 244.0 / 255, 0.8),
    val backShade: CurlColor = CurlColor(0.7, 0.7, 0.7, 1.0),
    val shadow: CurlColor = CurlColor(0.15, 0.15, 0.15, 1.0),
)

/**
 * All inputs needed to generate one frame without depending on a UI or graphics API.
 *
 * @property values Geometric progress, radius and normal angle; radius must be in (0, 4096].
 * @property width Logical page width, accepted in 1..4096.
 * @property height Logical page height, accepted in 1..4096.
 * @property scale Logical-to-output scale, accepted in 0.1..4.0.
 * @property startAngle Minimum local bend angle in radians, separate from values.angle.
 * @property endAngle Maximum local bend angle in radians; raised to startAngle when smaller.
 * @property frontEnabled Whether to emit front-face batches.
 * @property backEnabled Whether to emit back-face and cast-shadow batches.
 * @property material Colors applied during vertex generation.
 * @property surfaceRect Source rectangle in scaled page coordinates; defaults to the rounded-up pixel size.
 * @property targetBounds Output rectangle used to derive the default clip; does not rescale the geometry.
 * @property clipRect Requested output clip, intersected with targetBounds.
 * @property shadowBounds Shadow receiving rectangle in logical coordinates; defaults to a one-unit inset.
 * @property shadowSoftness Multiplier for penumbra width, accepted in 1..3.
 */
data class CurlMeshInput(
    val values: CurlValues,
    val width: Double,
    val height: Double,
    val scale: Double = 1.0,
    val startAngle: Double = 0.0,
    val endAngle: Double = kotlin.math.PI,
    val frontEnabled: Boolean = true,
    val backEnabled: Boolean = true,
    val material: CurlMaterial = CurlMaterial(),
    val surfaceRect: CurlRect? = null,
    val targetBounds: CurlRect? = null,
    val clipRect: CurlRect? = null,
    val shadowBounds: CurlRect? = null,
    val shadowSoftness: Double = 1.0,
)

/**
 * A renderer-neutral draw command. Consume batches in their returned order.
 *
 * Each vertex occupies 64 bytes in little-endian order: Float position x/y/z/w at
 * 0/4/8/12, Float texture u/v at 16/20, and four half-float RGBA channels at 40..47.
 * Other bytes are reserved and zero-filled. Texture coordinates are source-relative
 * scaled page coordinates; the renderer must apply its texture normalization.
 * Colors are premultiplied. Position x/y use logical geometry units, whereas
 * frame dimensions and clip bounds use scaled output coordinates; the renderer
 * must apply the corresponding logical-to-output transform.
 *
 * @property primitive Internal primitive code, currently 2 for the generated strips; not a GL enum.
 * @property mode Program code: 0 color, 3 texture, 12 texture with alpha.
 * @property blendFunction Internal blend code, currently 3; the renderer must interpret it.
 * @property blendEnabled Whether the renderer should enable blending.
 * @property vertexCount Number of vertices in the byte buffer.
 * @property count Number of elements to draw, currently equal to vertexCount.
 * @property vertices Packed vertex data owned by this batch.
 * @property indices Optional element indices; current generation emits non-indexed strips.
 * @property clip Output-space clip rectangle to enforce when drawing.
 */
data class CurlDrawBatch(
    val primitive: Int,
    val mode: Int,
    val blendFunction: Int,
    val blendEnabled: Boolean,
    val vertexCount: Int,
    val count: Int,
    val vertices: ByteArray,
    val indices: ShortArray?,
    val clip: CurlRect,
)

/**
 * A complete render result with output dimensions and an ordered list of draw batches.
 */
data class CurlMeshFrame(val width: Int, val height: Int, val batches: List<CurlDrawBatch>)

/**
 * Public entry point for mesh generation.
 *
 * Integration flow: recognize input with the gesture package, compute CurlValues with
 * CurlControl, interpolate with CurlTiming, then build and draw this frame. CurlLayout
 * selects content faces; CurlTransitions manages overlapping turns and layer events.
 *
 * This instance reuses mutable working buffers and must not be built concurrently.
 * Returned frames own their vertex bytes and can be retained across later builds.
 * See the generation package, starting at MeshGenerator, to customize geometry.
 */
class CurlMesh {
    private val generator = MeshGenerator()

    /**
     * Builds a frame synchronously. Invalid dimensions, scale, radius, angles or softness
     * throw IllegalArgumentException; geometry output limits are checked separately.
     */
    fun build(input: CurlMeshInput): CurlMeshFrame = generator.build(input)

    /**
     * Optionally joins adjacent compatible strips with degenerate vertices to reduce draw calls.
     * Triangle order, winding and attributes are preserved; packet boundaries and counts may change.
     * The single-argument overload retains the original packet layout.
     */
    fun build(input: CurlMeshInput, coalesceTriangleStrips: Boolean): CurlMeshFrame =
        generator.build(input, coalesceTriangleStrips)
}
